package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import hudson.model.FreeStyleProject;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
                remoteJenkinsServer.getDisplayName(), true, remoteProject.getFullName(), "",
                "", true, null, null, true, true, 1);
        project.getBuildersList().add(remoteBuildConfiguration);

        jenkinsRule.buildAndAssertSuccess(project);
    }
}
