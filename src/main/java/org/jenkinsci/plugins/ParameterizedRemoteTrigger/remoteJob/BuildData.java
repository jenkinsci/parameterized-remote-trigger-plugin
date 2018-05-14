package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import java.io.Serializable;
import java.net.URL;

import javax.annotation.Nonnull;

/**
 * Contains information about the <b>location of the job while is being built</b>.
 *
 */
public class BuildData implements Serializable
{
    private static final long serialVersionUID = 3553303097206059203L;

    @Nonnull
    private final int buildNumber;

    @Nonnull
    private final URL buildURL;

    public BuildData(@Nonnull int buildNumber, @Nonnull URL buildURL)
    {
        this.buildNumber = buildNumber;
        this.buildURL = buildURL;
    }

    @Nonnull
    public int getBuildNumber()
    {
        return buildNumber;
    }

    @Nonnull
    public URL getURL()
    {
        return buildURL;
    }

    @Nonnull
    @Override
    public String toString()
    {
        return "RemoteBuild [buildNumber=" + buildNumber + ", buildURL=" + buildURL + "]";
    }

}
