package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

/**
 * The status of the remote job on the queue.
 */
public enum RemoteBuildQueueStatus
{
    /**
     * The remote job was not triggered and it is not on the queue.
     */
    NOT_QUEUED("NOT_QUEUED"),

    /**
     * The remote job was triggered and it is on the queue waiting to be executed.
     */
    QUEUED("QUEUED"),

    /**
     * The remote job was executed.
     */
    EXECUTED("EXECUTED");


    private final String id;


    private RemoteBuildQueueStatus(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id;
    }

}
