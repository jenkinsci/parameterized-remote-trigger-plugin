package org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob;

import hudson.model.Result;

/**
 * The build status of a remote build either reflecting a {@link hudson.model.Result} if finished,
 * or if not finished yet a custom status like QUEUED, RUNNING,...<br>
 * Using {@link #isJenkinsResult()} it can be checked if the status if reflecting a {@link Result} or a custom status.<br>
 * The Jenkins {@link Result} can be obtained using {@link BuildStatus#getJenkinsResult()}.
 */
public enum BuildStatus
{

    /**
     * custom status indicating an UNKNOWN state
     */
    UNKNOWN("UNKNOWN"),

    /**
     * custom status indicating nothing started yet, neither QUEUED nor RUNNING
     */
    NOT_STARTED("NOT_STARTED"),

    /**
     * custom status indicating the remote build is in the QUEUE but not running yet
     */
    QUEUED("QUEUED"),

    /**
     * custom status indicating the build is RUNNING currently.
     */
    RUNNING("RUNNING"),

    /**
     * Status corresponding to the Jenkins Result.ABORTED
     */
    ABORTED(Result.ABORTED),

    /**
     * Status corresponding to the Jenkins Result.FAILURE
     */
    FAILURE(Result.FAILURE),

    /**
     * Status corresponding to the Jenkins Result.NOT_BUILT
     */
    NOT_BUILT(Result.NOT_BUILT),

    /**
     * Status corresponding to the Jenkins Result.SUCCESS
     */
    SUCCESS(Result.SUCCESS),

    /**
     * Status corresponding to the Jenkins Result.UNSTABLE
     */
    UNSTABLE(Result.UNSTABLE);


    private final String id;
    private final Result jenkinsResult;

    private BuildStatus(String id) {
        this.id = id;
        this.jenkinsResult = null;
    }

    private BuildStatus(Result jenkinsResult) {
        this.id = jenkinsResult.toString();
        this.jenkinsResult = jenkinsResult;
    }

    /**
     * @return The corresponding Jenkins {@link Result} or null if it is a custom status
     */
    public Result getJenkinsResult() {
        return jenkinsResult;
    }

    /**
     * @return true if it reflects a Jenkins {@link Result} or false if it is a custom status
     */
    public boolean isJenkinsResult() {
        return jenkinsResult != null;
    }

    @Override
    public String toString() {
        return id;
    }

}
