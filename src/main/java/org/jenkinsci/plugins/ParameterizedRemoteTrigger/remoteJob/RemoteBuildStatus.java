package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

/**
 * The build status of a remote build.
 */
public enum RemoteBuildStatus
{

    /**
     * Nothing started yet, neither QUEUED nor RUNNING
     */
    NOT_STARTED("NOT_STARTED"),

    /**
     * The build is RUNNING currently.
     */
    RUNNING("RUNNING"),

    /**
     * The build is RUNNING currently.
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
