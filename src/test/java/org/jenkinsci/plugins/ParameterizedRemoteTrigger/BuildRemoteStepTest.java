package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.HashMap;
import java.util.Map;

public class BuildRemoteStepTest {
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
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

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("name", "value");

        BuildRemoteStep before = new BuildRemoteStep("JENKINS", "jobName");
        before.setToken("TOKEN");
        before.setPropagate(false);
        before.setWait(false);
        before.setParameters(parameters);

        BuildRemoteStep after = new StepConfigTester(jenkinsRule).configRoundTrip(before);
        jenkinsRule.assertEqualDataBoundBeans(before, after);
    }
}
