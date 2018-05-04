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

        BuildInfo buildInfo = new BuildInfo(BuildStatus.NOT_STARTED);
        assert(buildInfo.getStatus() == BuildStatus.NOT_STARTED);
        assert(buildInfo.getResult() == Result.NOT_BUILT);
    }

    @Test
    public void illegalBuildStatusTest() {

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("It is not possible to set the status to finished without setting the build result. "
                + "Please use BuildInfo(Result result) or BuildInfo(String result) in order to set the status to finished.");

        new BuildInfo(BuildStatus.FINISHED);
    }

    @Test
    public void buildResultTest() {

        BuildInfo buildInfo = new BuildInfo(Result.SUCCESS);
        assert(buildInfo.getStatus() == BuildStatus.FINISHED);
        assert(buildInfo.getResult() == Result.SUCCESS);
    }

    @Test
    public void stringBuildResultTest() {

        BuildInfo buildInfo = new BuildInfo("SUCCESS");
        assert(buildInfo.getStatus() == BuildStatus.FINISHED);
        assert(buildInfo.getResult() == Result.SUCCESS);
    }

    @Test
    public void buildInfoTest() {

        BuildInfo buildInfo = new BuildInfo(BuildStatus.NOT_STARTED);
        assert(buildInfo.toString().equals("status=NOT_STARTED"));

        buildInfo = new BuildInfo(Result.SUCCESS);
        assert(buildInfo.toString().equals("status=FINISHED, result=SUCCESS"));
    }
}
