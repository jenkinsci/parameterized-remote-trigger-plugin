package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.RemoteBuildConfiguration.DescriptorImpl;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NullAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.pipeline.RemoteBuildPipelineStep;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

import hudson.AbortException;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.security.csrf.DefaultCrumbIssuer;

public class RemoteBuildConfigurationTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testRemoteBuild() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(null);
        _testRemoteBuild();
    }

    @Test
    public void testRemoteBuildWithCrumb() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
        _testRemoteBuild();
    }

    private void _testRemoteBuild() throws Exception {

        String remoteUrl = jenkinsRule.getURL().toString();
        RemoteJenkinsServer remoteJenkinsServer = new RemoteJenkinsServer();
        remoteJenkinsServer.setDisplayName("JENKINS");
        remoteJenkinsServer.setAddress(remoteUrl);
        RemoteBuildConfiguration.DescriptorImpl descriptor =
                jenkinsRule.jenkins.getDescriptorByType(RemoteBuildConfiguration.DescriptorImpl.class);
        descriptor.setRemoteSites(remoteJenkinsServer);

        FreeStyleProject remoteProject = jenkinsRule.createFreeStyleProject();
        remoteProject.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("parameterName1", "value1"),
                new StringParameterDefinition("parameterName2", "value2")));

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        RemoteBuildConfiguration configuration = new RemoteBuildConfiguration();
        configuration.setJob(remoteProject.getFullName());
        configuration.setRemoteJenkinsName(remoteJenkinsServer.getDisplayName());
        configuration.setPreventRemoteBuildQueue(false);
        configuration.setPollInterval(1);
        configuration.setEnhancedLogging(true);
        configuration.setParameters("");

        project.getBuildersList().add(configuration);

        jenkinsRule.waitUntilNoActivity();
        jenkinsRule.buildAndAssertSuccess(project);
    }

    @Test @WithoutJenkins
    public void testDefaults() throws IOException {

      RemoteBuildConfiguration config = new RemoteBuildConfiguration();
      config.setJob("job");

      assertEquals(1, config.getAuth().size());
      assertEquals(false, config.getBlockBuildUntilComplete()); //False in Job
      assertEquals(false, config.getEnhancedLogging());
      assertEquals("job", config.getJob());
      assertEquals(false, config.getLoadParamsFromFile());
      assertEquals(false, config.getOverrideAuth());
      assertEquals("", config.getParameterFile());
      assertEquals("", config.getParameters());
      assertEquals(10, config.getPollInterval());
      assertEquals(false, config.getPreventRemoteBuildQueue());
      assertEquals(null, config.getRemoteJenkinsName());
      assertEquals(false, config.getShouldNotFailBuild());
      assertEquals("", config.getToken());
    }

    @Test @WithoutJenkins
    public void testDefaultsPipelineStep() throws IOException {

      RemoteBuildPipelineStep config = new RemoteBuildPipelineStep("job");

      assertEquals(true, config.getBlockBuildUntilComplete()); //True in Pipeline Step
      assertEquals(false, config.getEnhancedLogging());
      assertEquals("job", config.getJob());
      assertEquals(false, config.getLoadParamsFromFile());
      assertTrue(config.getAuth() instanceof NullAuth);
      assertEquals("", config.getParameterFile());
      assertEquals("", config.getParameters());
      assertEquals(10, config.getPollInterval());
      assertEquals(false, config.getPreventRemoteBuildQueue());
      assertEquals(null, config.getRemoteJenkinsName());
      assertEquals(false, config.getShouldNotFailBuild());
      assertEquals("", config.getToken());
    }

    @Test @WithoutJenkins
    public void testJobUrlHandling_withoutServer() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        assertEquals("MyJob", config.getJob());
        try {
            config.findEffectiveRemoteHost(null);
            fail("findRemoteHost() should throw an AbortException since server not specified");
        } catch(AbortException e) {
            assertEquals("Configuration of the remote Jenkins host is missing.", e.getMessage());
        }
    }

    @Test @WithoutJenkins
    public void testJobUrlHandling_withJobNameAndRemoteUrl() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        config.setRemoteJenkinsUrl("http://test:8080");
        assertEquals("MyJob", config.getJob());
        assertEquals("http://test:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testJobUrlHandling_withJobNameAndRemoteName() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        config = mockGlobalRemoteHost(config, "remoteJenkinsName", "http://test:8080");

        config.setRemoteJenkinsName("remoteJenkinsName");
        assertEquals("MyJob", config.getJob());
        assertEquals("http://test:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testJobUrlHandling_withMultiFolderJobNameAndRemoteName() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("A/B/C/D/MyJob");
        config = mockGlobalRemoteHost(config, "remoteJenkinsName", "http://test:8080");

        config.setRemoteJenkinsName("remoteJenkinsName");
        assertEquals("A/B/C/D/MyJob", config.getJob());
        assertEquals("http://test:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testJobUrlHandling_withJobUrl() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("http://test:8080/job/folder/job/MyJob");
        assertEquals("http://test:8080/job/folder/job/MyJob", config.getJob()); //The value configured for "job"
        assertEquals("http://test:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testJobUrlHandling_withJobUrlAndRemoteUrl() throws IOException {
        //URL specified for "job" shall override specified remote host
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("http://testA:8080/job/folder/job/MyJobA");
        config.setRemoteJenkinsUrl("http://testB:8080");
        assertEquals("http://testA:8080/job/folder/job/MyJobA", config.getJob()); //The value configured for "job"
        assertEquals("http://testA:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testJobUrlHandling_withJobUrlAndRemoteName() throws IOException {
        //URL specified for "job" shall override global setting
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("http://testA:8080/job/folder/job/MyJobA");
        config = mockGlobalRemoteHost(config, "remoteJenkinsName", "http://testB:8080");

        config.setRemoteJenkinsName("remoteJenkinsName");
        assertEquals("http://testA:8080/job/folder/job/MyJobA", config.getJob()); //The value configured for "job"
        assertEquals("http://testA:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testFindEffectiveRemoteHost_withoutJob() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        try {
            RemoteBuildConfiguration config = new RemoteBuildConfiguration();
            config.setJob("xxx");
            Field field = config.getClass().getDeclaredField("job");
            field.setAccessible(true);
            field.set(config, "");
            config.findEffectiveRemoteHost(null);
            fail("findRemoteHost() should throw an AbortException since job not specified");
        } catch(AbortException e) {
            assertEquals("Parameter 'Remote Job Name or URL' ('job' variable in Pipeline) not specified.", e.getMessage());
        }
    }    

    @Test @WithoutJenkins
    public void testRemoteUrlOverridesRemoteName() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        config = mockGlobalRemoteHost(config, "remoteJenkinsName", "http://globallyConfigured:8080");

        config.setRemoteJenkinsName("remoteJenkinsName");
        assertEquals("http://globallyConfigured:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());

        //Now override remote host URL
        config.setRemoteJenkinsUrl("http://locallyOverridden:8080");
        assertEquals("MyJob", config.getJob());
        assertEquals("http://locallyOverridden:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testFindEffectiveRemoteHost_jobNameMissing() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        try {
            config.findEffectiveRemoteHost(null);
        }
        catch (AbortException e) {
            assertEquals("Parameter 'Remote Job Name or URL' ('job' variable in Pipeline) not specified.", e.getMessage());
        }
    }

    @Test @WithoutJenkins
    public void testFindEffectiveRemoteHost_globalConfigMissing() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        config = mockGlobalRemoteHost(config, "remoteJenkinsName", "http://globallyConfigured:8080");

        config.setRemoteJenkinsName("notConfiguredRemoteHost");
        try {
            config.findEffectiveRemoteHost(null);
        }
        catch (AbortException e) {
            assertEquals("Could get remote host with ID 'notConfiguredRemoteHost' configured in Jenkins global configuration. Please check your global configuration.", e.getMessage());
        }
    }

    @Test @WithoutJenkins
    public void testFindEffectiveRemoteHost_globalConfigMissing_localOverrideHostURL() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        config = mockGlobalRemoteHost(config, "remoteJenkinsName", "http://globallyConfigured:8080");

        config.setRemoteJenkinsName("notConfiguredRemoteHost");
        config.setRemoteJenkinsUrl("http://locallyOverridden:8080");
        assertEquals("http://locallyOverridden:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testFindEffectiveRemoteHost_globalConfigMissing_localOverrideJobURL() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("http://localJobUrl:8080/job/MyJob");
        config = mockGlobalRemoteHost(config, "remoteJenkinsName", "http://globallyConfigured:8080");

        config.setRemoteJenkinsName("notConfiguredRemoteHost");
        assertEquals("http://localJobUrl:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testFindEffectiveRemoteHost_localOverrideHostURL() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        config.setRemoteJenkinsUrl("http://hostname:8080");
        assertEquals("http://hostname:8080", config.findEffectiveRemoteHost(null).getRemoteAddress());
    }

    @Test @WithoutJenkins
    public void testFindEffectiveRemoteHost_localOverrideHostURLWrong() throws IOException {
        RemoteBuildConfiguration config = new RemoteBuildConfiguration();
        config.setJob("MyJob");
        config.setRemoteJenkinsUrl("hostname:8080");
        try {
            config.findEffectiveRemoteHost(null);
            fail("Expected AbortException");
        }
        catch (AbortException e) {
            assertEquals("The 'Override remote host URL' parameter value (remoteJenkinsUrl: 'hostname:8080') is no valid URL", e.getMessage());
        }
    }

    private RemoteBuildConfiguration mockGlobalRemoteHost(RemoteBuildConfiguration config, String remoteName, String remoteUrl) throws MalformedURLException {
        RemoteJenkinsServer jenkinsServer = new RemoteJenkinsServer();
        jenkinsServer.setDisplayName(remoteName);
        jenkinsServer.setAddress(remoteUrl);

        RemoteBuildConfiguration spy = spy(config);
        DescriptorImpl descriptor = DescriptorImpl.newInstanceForTests();
        descriptor.setRemoteSites(jenkinsServer);
        doReturn(descriptor).when(spy).getDescriptor();

        return spy;
    }

    @Test @WithoutJenkins
    public void testRemoveTrailingSlashes() {
        assertEquals("xxx", RemoteBuildConfiguration.removeTrailingSlashes("xxx"));
        assertEquals("xxx", RemoteBuildConfiguration.removeTrailingSlashes("xxx/"));
        assertEquals("xxx", RemoteBuildConfiguration.removeTrailingSlashes("xxx//////"));
        assertEquals("xxx/yy", RemoteBuildConfiguration.removeTrailingSlashes("xxx/yy//"));
        assertEquals("xxx", RemoteBuildConfiguration.removeTrailingSlashes("xxx/     "));
    }

    @Test @WithoutJenkins
    public void testRemoveQueryParameters() {
        assertEquals("xxx", RemoteBuildConfiguration.removeQueryParameters("xxx"));
        assertEquals("http://test:8080/MyJob", RemoteBuildConfiguration.removeQueryParameters("http://test:8080/MyJob?xy=abc"));
        assertEquals("xxx", RemoteBuildConfiguration.removeQueryParameters("xxx?zzz"));
    }

    @Test @WithoutJenkins
    public void testRemoveHashParameters() {
        assertEquals("xxx", RemoteBuildConfiguration.removeHashParameters("xxx"));
        assertEquals("http://test:8080/MyJob", RemoteBuildConfiguration.removeHashParameters("http://test:8080/MyJob#asdsad"));
        assertEquals("xxx", RemoteBuildConfiguration.removeHashParameters("xxx#zzz"));
    }

    @Test @WithoutJenkins
    public void testGenerateJobUrl() throws MalformedURLException {
        RemoteJenkinsServer remoteServer = new RemoteJenkinsServer();
        remoteServer.setAddress("https://server:8080/jenkins");

        assertEquals("https://server:8080/jenkins/job/JobName", RemoteBuildConfiguration.generateJobUrl(remoteServer, "JobName"));
        assertEquals("https://server:8080/jenkins/job/Folder/job/JobName", RemoteBuildConfiguration.generateJobUrl(remoteServer, "Folder/JobName"));
        assertEquals("https://server:8080/jenkins/job/More/job/than/job/one/job/folder", RemoteBuildConfiguration.generateJobUrl(remoteServer, "More/than/one/folder"));
        try {
            RemoteBuildConfiguration.generateJobUrl(remoteServer, "");
            Assert.fail("Expected IllegalArgumentException");
        } catch(IllegalArgumentException e) {}
        try {
            RemoteBuildConfiguration.generateJobUrl(remoteServer, null);
            Assert.fail("Expected IllegalArgumentException");
        } catch(IllegalArgumentException e) {}
        try {
            RemoteBuildConfiguration.generateJobUrl(null, "JobName");
            Assert.fail("Expected NullPointerException");
        } catch(NullPointerException e) {}

        //Test trailing slash
        remoteServer.setAddress("https://server:8080/jenkins/");
        assertEquals("https://server:8080/jenkins/job/JobName", RemoteBuildConfiguration.generateJobUrl(remoteServer, "JobName"));

        try {
            RemoteJenkinsServer missingUrl = new RemoteJenkinsServer();
            RemoteBuildConfiguration.generateJobUrl(missingUrl, "JobName");
            Assert.fail("Expected RuntimeException");
        } catch(RuntimeException e) {}

    }


}
