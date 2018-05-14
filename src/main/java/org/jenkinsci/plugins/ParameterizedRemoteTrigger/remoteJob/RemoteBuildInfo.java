package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.io.Serializable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.Result;

/**
 * Remote build info, containing build status and build result.
 *
 */
public class RemoteBuildInfo implements Serializable
{
    private static final long serialVersionUID = -5177308623227407314L;

    @CheckForNull
    private String queueId;

    @CheckForNull
    private BuildData buildData;

    @Nonnull
    private RemoteBuildQueueStatus queueStatus;

    @Nonnull
    private RemoteBuildStatus status;

    @Nonnull
    private Result result;


    public RemoteBuildInfo()
    {
        queueId = null;
        buildData = null;
        queueStatus = RemoteBuildQueueStatus.NOT_QUEUED;
        status = RemoteBuildStatus.NOT_STARTED;
        result = Result.NOT_BUILT;
    }

    @CheckForNull
    public String getQueueId() {
        return queueId;
    }

    @CheckForNull
    public BuildData getBuildData() {
        return buildData;
    }

    @Nonnull
    public RemoteBuildQueueStatus getQueueStatus()
    {
        return queueStatus;
    }

    @Nonnull
    public RemoteBuildStatus getStatus()
    {
        return status;
    }

    @Nonnull
    public Result getResult()
    {
        return result;
    }

    public void setQueueId(String queueId) {
        this.queueId = queueId;
        this.queueStatus = RemoteBuildQueueStatus.QUEUED;
    }

    public void setBuildData(BuildData buildData) {
        this.buildData = buildData;
        this.queueStatus = RemoteBuildQueueStatus.EXECUTED;
    }

    public void setBuildStatus(RemoteBuildStatus status)
    {
        if (status == RemoteBuildStatus.FINISHED) {
            throw new IllegalArgumentException("It is not possible to set the status to finished without setting the build result. "
                    + "Please use BuildInfo(Result result) or BuildInfo(String result) in order to set the status to finished.");
        } else {
            this.status = status;
            this.result = Result.NOT_BUILT;
        }
    }

    public void setBuildResult(Result result)
    {
        this.status = RemoteBuildStatus.FINISHED;
        this.result = result;
    }

    public void setBuildResult(String result)
    {
        this.status = RemoteBuildStatus.FINISHED;
        this.result = Result.fromString(result);
    }

    @Nonnull
    @Override
    public String toString()
    {
        if (status == RemoteBuildStatus.FINISHED) return String.format("status=%s, result=%s", status.toString(), result.toString());
        else return String.format("queueStatus=%s, status=%s", queueStatus.toString(), status.toString());
    }

}
