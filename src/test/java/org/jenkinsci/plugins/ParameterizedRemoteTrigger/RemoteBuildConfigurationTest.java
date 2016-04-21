package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;

import java.io.IOException;
import java.net.URL;

import net.sf.json.JSONObject;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

public class RemoteBuildConfigurationTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void testRemoteBuild() throws Exception {
        jenkinsRule.jenkins.setCrumbIssuer(null);

        JSONObject authenticationMode = new JSONObject();
        authenticationMode.put("value", "none");
        JSONObject auth = new JSONObject();
        auth.put("authenticationMode", authenticationMode);

        String remoteUrl = jenkinsRule.getURL().toString();
        RemoteJenkinsServer remoteJenkinsServer =
                new RemoteJenkinsServer(remoteUrl, "JENKINS", false, auth);
        RemoteBuildConfiguration.DescriptorImpl descriptor =
                jenkinsRule.jenkins.getDescriptorByType(RemoteBuildConfiguration.DescriptorImpl.class);
        descriptor.setRemoteSites(remoteJenkinsServer);

        FreeStyleProject remoteProject = jenkinsRule.createFreeStyleProject();

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        RemoteBuildConfiguration remoteBuildConfiguration = new RemoteBuildConfiguration(
                remoteJenkinsServer.getDisplayName(), false, remoteProject.getFullName(), "",
                "", true, null, null, false, true, 1);
        project.getBuildersList().add(remoteBuildConfiguration);

        jenkinsRule.buildAndAssertSuccess(project);
    }
    
    private RemoteBuildConfiguration prepareSpy(String expectedJson) throws IOException {
        RemoteJenkinsServer jenkinsServer = mock(RemoteJenkinsServer.class);
        when(jenkinsServer.getAddress()).thenReturn(new URL("http://test.com"));
        
        RemoteBuildConfiguration configuration = new RemoteBuildConfiguration("remoteJenkinsName", true, "job", "token",
                "parameters", true, new JSONObject(), new JSONObject(), true, true, 10);
        RemoteBuildConfiguration spy = spy(configuration);
        doReturn(jenkinsServer).when(spy).findRemoteHost(anyString());
        
        doReturn(JSONObject.fromObject(expectedJson)).when(spy).sendHTTPCall(anyString(), anyString(), any(AbstractBuild.class), any(BuildListener.class));
        
        return spy;
    }

    @Test
    @WithoutJenkins
    public void givenAJobWithNoActions_WhenCheckingIfTheJobIsParameterized_ThenItShouldReturnFalse() throws IOException {
        
        String json = "{\"actions\":[],\"description\":\"\",\"displayName\":\"test_remote\",\"displayNameOrNull\":null,\"name\":\"test_remote\",\"url\":\"http://jenkins.com/job/test_remote/\",\"buildable\":true,\"builds\":[],\"color\":\"notbuilt\",\"firstBuild\":null,\"healthReport\":[],\"inQueue\":false,\"keepDependencies\":false,\"lastBuild\":null,\"lastCompletedBuild\":null,\"lastFailedBuild\":null,\"lastStableBuild\":null,\"lastSuccessfulBuild\":null,\"lastUnstableBuild\":null,\"lastUnsuccessfulBuild\":null,\"nextBuildNumber\":1,\"property\":[{},{},{\"includeTestSummary\":false,\"notifyAborted\":false,\"notifyBackToNormal\":false,\"notifyFailure\":false,\"notifyNotBuilt\":false,\"notifyRepeatedFailure\":false,\"notifySuccess\":false,\"notifyUnstable\":false,\"room\":\"\",\"showCommitList\":false,\"startNotification\":false,\"teamDomain\":\"\",\"token\":\"\"},{}],\"queueItem\":null,\"concurrentBuild\":false,\"downstreamProjects\":[],\"scm\":{},\"upstreamProjects\":[]}";
        
        RemoteBuildConfiguration spy = prepareSpy(json);
        
        assertFalse(spy.isRemoteJobParameterized("job", null, null));
    }
    
    @Test
    @WithoutJenkins
    public void givenAJobWithActionsNotRelatedToParameterizedBuild_WhenCheckingIfTheJobIsParameterized_ThenItShouldReturnFalse() throws IOException {
        
        String json = "{\"actions\":[{},false],\"description\":\"\",\"displayName\":\"test_remote\",\"displayNameOrNull\":null,\"name\":\"test_remote\",\"url\":\"http://jenkins.com/job/test_remote/\",\"buildable\":true,\"builds\":[],\"color\":\"notbuilt\",\"firstBuild\":null,\"healthReport\":[],\"inQueue\":false,\"keepDependencies\":false,\"lastBuild\":null,\"lastCompletedBuild\":null,\"lastFailedBuild\":null,\"lastStableBuild\":null,\"lastSuccessfulBuild\":null,\"lastUnstableBuild\":null,\"lastUnsuccessfulBuild\":null,\"nextBuildNumber\":1,\"property\":[{},{},{\"includeTestSummary\":false,\"notifyAborted\":false,\"notifyBackToNormal\":false,\"notifyFailure\":false,\"notifyNotBuilt\":false,\"notifyRepeatedFailure\":false,\"notifySuccess\":false,\"notifyUnstable\":false,\"room\":\"\",\"showCommitList\":false,\"startNotification\":false,\"teamDomain\":\"\",\"token\":\"\"},{}],\"queueItem\":null,\"concurrentBuild\":false,\"downstreamProjects\":[],\"scm\":{},\"upstreamProjects\":[]}";
        
        RemoteBuildConfiguration spy = prepareSpy(json);
        
        assertFalse(spy.isRemoteJobParameterized("job", null, null));
    }
    
    @Test
    @WithoutJenkins
    public void givenAJobWithActionsRelatedToParameterizedBuild_WhenCheckingIfTheJobIsParameterized_ThenItShouldReturnTrue() throws IOException {
        
        String json = "{\"actions\":[{\"parameterDefinitions\":[{\"defaultParameterValue\":{\"value\":\"\"},\"description\":\"\",\"name\":\"test\",\"type\":\"StringParameterDefinition\"},{\"defaultParameterValue\":{\"value\":false},\"description\":\"\",\"name\":\"test2\",\"type\":\"BooleanParameterDefinition\"}]},{},{}],\"description\":\"\",\"displayName\":\"test_remote_parameterized\",\"displayNameOrNull\":null,\"name\":\"test_remote_parameterized\",\"url\":\"http://jenkins.com/job/test_remote_parameterized/\",\"buildable\":true,\"builds\":[],\"color\":\"notbuilt\",\"firstBuild\":null,\"healthReport\":[],\"inQueue\":false,\"keepDependencies\":false,\"lastBuild\":null,\"lastCompletedBuild\":null,\"lastFailedBuild\":null,\"lastStableBuild\":null,\"lastSuccessfulBuild\":null,\"lastUnstableBuild\":null,\"lastUnsuccessfulBuild\":null,\"nextBuildNumber\":1,\"property\":[{},{\"parameterDefinitions\":[{\"defaultParameterValue\":{\"name\":\"test\",\"value\":\"\"},\"description\":\"\",\"name\":\"test\",\"type\":\"StringParameterDefinition\"},{\"defaultParameterValue\":{\"name\":\"test2\",\"value\":false},\"description\":\"\",\"name\":\"test2\",\"type\":\"BooleanParameterDefinition\"}]},{},{\"includeTestSummary\":false,\"notifyAborted\":false,\"notifyBackToNormal\":false,\"notifyFailure\":false,\"notifyNotBuilt\":false,\"notifyRepeatedFailure\":false,\"notifySuccess\":false,\"notifyUnstable\":false,\"room\":\"\",\"showCommitList\":false,\"startNotification\":false,\"teamDomain\":\"\",\"token\":\"\"},{}],\"queueItem\":null,\"concurrentBuild\":false,\"downstreamProjects\":[],\"scm\":{},\"upstreamProjects\":[]}";
        
        RemoteBuildConfiguration spy = prepareSpy(json);
        
        assertTrue(spy.isRemoteJobParameterized("job", null, null));
    }
}
