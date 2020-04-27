package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.net.MalformedURLException;
import java.net.URL;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONNull;

/**
 * Contains information about the remote job <b>while is waiting on the queue</b>.
 *
 */
public class QueueItemData
{
    @Nonnull
    private QueueItemStatus status;

    @Nullable
    private String why;

    @Nonnull
    private int buildNumber;

    @Nullable
    private URL buildURL;


    public QueueItemData() throws MalformedURLException
    {
        this.status = QueueItemStatus.WAITING;
    }

    public boolean isWaiting()
    {
        return status == QueueItemStatus.WAITING;
    }

    public boolean isBlocked()
    {
        return status == QueueItemStatus.BLOCKED;
    }

    public boolean isBuildable()
    {
        return status == QueueItemStatus.BUILDABLE;
    }

    public boolean isPending()
    {
        return status == QueueItemStatus.PENDING;
    }

    public boolean isLeft()
    {
        return status == QueueItemStatus.LEFT;
    }

    public boolean isExecuted()
    {
        return status == QueueItemStatus.EXECUTED;
    }

    public boolean isCancelled()
    {
        return status == QueueItemStatus.CANCELLED;
    }

    @Nonnull
    public QueueItemStatus getStatus() {
        return status;
    }

    @CheckForNull
    public String getWhy() {
        return why;
    }

    @Nonnull
    public int getBuildNumber()
    {
        return buildNumber;
    }

    @CheckForNull
    public URL getBuildURL()
    {
        return buildURL;
    }

    /**
     * Updates the queue item data with a queue response.
     *
     * @param context
     *            the context of this Builder/BuildStep.
     * @param queueResponse
     *            the queue response
     * @throws MalformedURLException
     *            if there is an error creating the build URL.
     */
    public void update(@Nonnull BuildContext context, @Nonnull JSONObject queueResponse) throws MalformedURLException
    {
        if (queueResponse.getBoolean("blocked")) status = QueueItemStatus.BLOCKED;
        if (queueResponse.getBoolean("buildable")) status = QueueItemStatus.BUILDABLE;
        if (getOptionalBoolean(queueResponse, "pending")) status = QueueItemStatus.PENDING;
        if (getOptionalBoolean(queueResponse, "cancelled")) status = QueueItemStatus.CANCELLED;
        if (isBlocked() || isBuildable() || isPending()) why = queueResponse.getString("why");
        else if (!isCancelled()) status = QueueItemStatus.LEFT;

        if (isLeft()) {
            try {
                Object remoteJobInfo = queueResponse.get("executable");
                if (!(remoteJobInfo instanceof JSONNull)) {
                    try {
                        buildNumber = remoteJobInfo.getInt("number");
                    } catch (JSONException e) {
                        context.logger.println(String.format("[WARNING] The attribute \"number\" was not found. Unexpected response: %s", queueResponse.toString()));
                    }
                    try {
                        buildURL = new URL(remoteJobInfo.getString("url"));
                    } catch (JSONException e) {
                        context.logger.println(String.format("[WARNING] The attribute \"url\" was not found. Unexpected response: %s", queueResponse.toString()));
                    }
                }
            } catch (JSONException e) {
                context.logger.println(String.format("[WARNING] The attribute \"executable\" was not found. Unexpected response: %s", queueResponse.toString()));
            }
            if (buildNumber != 0 && buildURL != null) status = QueueItemStatus.EXECUTED;
        }
    }

    private boolean getOptionalBoolean(@Nonnull JSONObject queueResponse, @Nonnull String attribute)
    {
        if (queueResponse.containsKey(attribute))
            return queueResponse.getBoolean(attribute);
        else return false;
    }
}
