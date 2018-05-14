package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * Contains information about the job <b>while is waiting on the queue</b>.
 *
 */
public class QueueItemData
{
    @Nonnull
    private final JSONObject queueResponse;

    @Nonnull
    private RemoteBuildQueueStatus status;


    public QueueItemData(@Nonnull BuildContext context, @Nonnull JSONObject queueResponse) throws MalformedURLException
    {
        this.queueResponse = queueResponse;
        if (isExecutable() && getBuildData(context)!=null) status = RemoteBuildQueueStatus.EXECUTED;
        else status = RemoteBuildQueueStatus.QUEUED;
    }

    public boolean isBlocked()
    {
        return queueResponse.getBoolean("blocked");
    }

    public boolean isBuildable()
    {
        return queueResponse.getBoolean("buildable");
    }

    public boolean isPending()
    {
        return getOptionalBoolean("pending");
    }

    public boolean isCancelled()
    {
        return getOptionalBoolean("cancelled");
    }

    public String getWhy()
    {
        return queueResponse.getString("why");
    }

    public boolean isExecutable()
    {
        return (!isBlocked() && !isBuildable() && !isPending() && !isCancelled());
    }

    public RemoteBuildQueueStatus getQueueStatus() {
        return status;
    }
    /**
     * When a queue item is <b>executable</b>, the build number and the build URL
     * of the remote job are available in the queue item data.
     *
     * @param context
     *            the context of this Builder/BuildStep.
     * @return {@link BuildData}
     *            the remote build or null if the queue item is not executable.
     * @throws MalformedURLException
     *            if there is an error creating the build URL.
     */
    @CheckForNull
    public BuildData getBuildData(@Nonnull BuildContext context) throws MalformedURLException
    {
        if (!isExecutable()) return null;

        JSONObject remoteJobInfo;
        try {
            remoteJobInfo = queueResponse.getJSONObject("executable");
            if (remoteJobInfo == null) return null;
        } catch (JSONException e) {
            context.logger.println("The attribute \"executable\" was not found. Unexpected response: " + queueResponse.toString());
            return null;
        }
        int buildNumber;
        try {
            buildNumber = remoteJobInfo.getInt("number");
        } catch (JSONException e) {
            context.logger.println("The attribute \"number\" was not found. Unexpected response: " + queueResponse.toString());
            return null;
        }
        String buildUrl;
        try {
            buildUrl = remoteJobInfo.getString("url");
        } catch (JSONException e) {
            context.logger.println("The attribute \"url\" was not found. Unexpected response: " + queueResponse.toString());
            return null;
        }
        return new BuildData(buildNumber, new URL(buildUrl));
    }

    private boolean getOptionalBoolean(String attribute)
    {
        if (queueResponse.containsKey(attribute))
            return queueResponse.getBoolean(attribute);
        else return false;
    }
}
