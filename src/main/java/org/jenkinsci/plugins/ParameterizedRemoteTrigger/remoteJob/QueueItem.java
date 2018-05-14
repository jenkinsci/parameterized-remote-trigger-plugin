package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import hudson.AbortException;

/**
 * Represents an item on the queue. Contains information about the <b>location
 * of the job in the queue</b>.
 *
 */
public class QueueItem
{
    final static private String key = "Location";

    @Nonnull
    private final String location;

    @Nonnull
    private final String id;


    public QueueItem(@Nonnull Map<String,List<String>> header) throws AbortException
    {
        if (!header.containsKey(key))
            throw new AbortException(String.format("Error triggering the remote job. The header of the response has an unexpected format: %n%s", header));
        location = header.get(key).get(0);
        try {
            String loc = location.substring(0, location.lastIndexOf('/'));
            id = loc.substring(loc.lastIndexOf('/')+1);
        } catch (Exception ex) {
            throw new AbortException(String.format("Error triggering the remote job. The header of the response contains an unexpected location: %s", location));
        }
    }

    @Nonnull
    public String getLocation() {
        return location;
    }

    @Nonnull
    public String getId() {
        return id;
    }

}
