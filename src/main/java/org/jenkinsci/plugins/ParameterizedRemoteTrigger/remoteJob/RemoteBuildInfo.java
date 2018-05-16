package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.io.Serializable;

import javax.annotation.Nonnull;

import hudson.model.Result;

/**
 * Remote build info, containing build status and build result.
 *
 */
public class RemoteBuildInfo implements Serializable
{
    private static final long serialVersionUID = -5177308623227407314L;

    @Nonnull
    private RemoteBuildStatus status;

    @Nonnull
    private Result result;


    public RemoteBuildInfo()
    {
        status = RemoteBuildStatus.NOT_STARTED;
        result = Result.NOT_BUILT;
    }

    public RemoteBuildInfo(RemoteBuildStatus status)
    {
        this.status = status;
        if (status == RemoteBuildStatus.FINISHED) {
            throw new IllegalArgumentException("It is not possible to set the status to finished without setting the build result. "
                    + "Please use BuildInfo(Result result) or BuildInfo(String result) in order to set the status to finished.");
        } else {
            this.result = Result.NOT_BUILT;
        }
    }

    public RemoteBuildInfo(Result result)
    {
        this.status = RemoteBuildStatus.FINISHED;
        this.result = result;
    }

    public RemoteBuildInfo(String result)
    {
        this.status = RemoteBuildStatus.FINISHED;
        this.result = Result.fromString(result);
    }

    public RemoteBuildStatus getStatus()
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
        if (status == RemoteBuildStatus.FINISHED) return String.format("status=%s, result=%s", status.toString(), result.toString());
        else return String.format("status=%s", status.toString());
    }

}
