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
     * The remote job is on the queue.
     */
    QUEUED("QUEUED"),

    /**
     * The remote job is being executed.
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
