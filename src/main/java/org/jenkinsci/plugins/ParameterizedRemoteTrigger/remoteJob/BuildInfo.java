package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.io.Serializable;

import javax.annotation.Nonnull;

import hudson.model.Result;

/**
 * Remote build info, containing build status and build result.
 *
 */
public class BuildInfo implements Serializable
{
    private static final long serialVersionUID = -5177308623227407314L;

    @Nonnull
    private BuildStatus status;

    @Nonnull
    private Result result;


    public BuildInfo()
    {
        status = BuildStatus.NOT_STARTED;
        result = Result.NOT_BUILT;
    }

    public BuildInfo(BuildStatus status)
    {
        this.status = status;
        if (status == BuildStatus.FINISHED) {
            throw new IllegalArgumentException("It is not possible to set the status to finished without setting the build result. "
                    + "Please use BuildInfo(Result result) or BuildInfo(String result) in order to set the status to finished.");
        } else {
            this.result = Result.NOT_BUILT;
        }
    }

    public BuildInfo(Result result)
    {
        this.status = BuildStatus.FINISHED;
        this.result = result;
    }

    public BuildInfo(String result)
    {
        this.status = BuildStatus.FINISHED;
        this.result = Result.fromString(result);
    }

    public BuildStatus getStatus()
    {
        return status;
    }

    public Result getResult()
    {
        return result;
    }

    @Override
    public String toString()
    {
        if (status == BuildStatus.FINISHED) return String.format("status=%s, result=%s", status.toString(), result.toString());
        else return String.format("status=%s", status.toString());
    }

}
