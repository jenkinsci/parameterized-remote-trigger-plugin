package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

/**
 * The build status of a remote build.
 */
public enum RemoteBuildStatus
{

    /**
     * The remote build did not start.
     */
    NOT_STARTED("NOT_STARTED"),

    /**
     * The remote build is running currently.
     */
    RUNNING("RUNNING"),

    /**
     * The remote build is finished.
     */
    FINISHED("FINISHED");


    private final String id;


    private RemoteBuildStatus(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id;
    }

}
