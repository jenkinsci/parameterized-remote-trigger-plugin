package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import hudson.model.FreeStyleProject;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

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

    @Test
    public void testRemoteFolderedBuild() throws Exception {
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

        MockFolder remoteJobFolder = jenkinsRule.createFolder("someJobFolder");
        FreeStyleProject remoteProject = remoteJobFolder.createProject(FreeStyleProject.class, "someJobName");

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();

        RemoteBuildConfiguration remoteBuildConfiguration = new RemoteBuildConfiguration(
                remoteJenkinsServer.getDisplayName(), false, remoteProject.getFullName(), "",
                "", true, null, null, false, true, 1);
        project.getBuildersList().add(remoteBuildConfiguration);

        jenkinsRule.buildAndAssertSuccess(project);
    }
}
