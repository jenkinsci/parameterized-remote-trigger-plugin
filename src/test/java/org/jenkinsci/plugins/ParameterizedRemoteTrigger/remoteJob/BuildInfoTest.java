package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import hudson.model.Result;


public class BuildInfoTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();


    @Test
    public void buildStatusTest() {

        RemoteBuildInfo buildInfo = new RemoteBuildInfo(RemoteBuildStatus.NOT_STARTED);
        assert(buildInfo.getStatus() == RemoteBuildStatus.NOT_STARTED);
        assert(buildInfo.getResult() == Result.NOT_BUILT);
    }

    @Test
    public void illegalBuildStatusTest() {

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("It is not possible to set the status to finished without setting the build result. "
                + "Please use BuildInfo(Result result) or BuildInfo(String result) in order to set the status to finished.");

        new RemoteBuildInfo(RemoteBuildStatus.FINISHED);
    }

    @Test
    public void buildResultTest() {

        RemoteBuildInfo buildInfo = new RemoteBuildInfo(Result.SUCCESS);
        assert(buildInfo.getStatus() == RemoteBuildStatus.FINISHED);
        assert(buildInfo.getResult() == Result.SUCCESS);
    }

    @Test
    public void stringBuildResultTest() {

        RemoteBuildInfo buildInfo = new RemoteBuildInfo("SUCCESS");
        assert(buildInfo.getStatus() == RemoteBuildStatus.FINISHED);
        assert(buildInfo.getResult() == Result.SUCCESS);
    }

    @Test
    public void buildInfoTest() {

        RemoteBuildInfo buildInfo = new RemoteBuildInfo(RemoteBuildStatus.NOT_STARTED);
        assert(buildInfo.toString().equals("status=NOT_STARTED"));

        buildInfo = new RemoteBuildInfo(Result.SUCCESS);
        assert(buildInfo.toString().equals("status=FINISHED, result=SUCCESS"));
    }
}
