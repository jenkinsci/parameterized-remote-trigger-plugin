package org.jenkinsci.plugins.ParameterizedRemoteTrigger.pipeline;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.RemoteBuildConfiguration;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.RemoteJenkinsServer;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.BuildData;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.BuildInfo;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.BuildStatus;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;

import hudson.model.Result;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

/**
 * A handle to the triggered remote build. This handle is used in Pipelines
 * to have direct access to the (correct) remote build instead of relying on
 * environment variables (like in a Job). This prevents issues e.g. when triggering
 * remote jobs in a parallel pipeline step.
 */
@ParametersAreNullableByDefault
public class Handle implements Serializable {

    private static final long serialVersionUID = 4418782245518194292L;

    @Nonnull
    private final RemoteBuildConfiguration remoteBuildConfiguration;
    @Nonnull
    private final String queueId;

    //Available once moved from queue to an executor
    @Nullable
    private BuildData buildData;
    @Nonnull
    private BuildInfo buildInfo;

    @Nullable
    private String jobName;
    @Nullable
    private String jobFullName;
    @Nullable
    private String jobDisplayName;
    @Nullable
    private String jobFullDisplayName;
    @Nullable
    private String jobUrl;

    /**
     * The current local Item (Job, Pipeline,...) where this plugin is currently used.
     */
    @Nonnull
    private final String currentItem;

    @Nonnull
    private final RemoteJenkinsServer effectiveRemoteServer;

    /*
     * The latest log entries from the last called method.
     * Unfortunately the TaskListener.getLogger() from the StepContext does
     * not write to the pipeline log anymore since the RemoteBuildPipelineStep
     * already finished.
     * TODO: Once we found a way to log to the pipeline log directly we can switch
     */
    @Nonnull
    private String lastLog;


    public Handle(@Nonnull RemoteBuildConfiguration remoteBuildConfiguration, @Nonnull String queueId, @Nonnull String currentItem,
        @Nonnull RemoteJenkinsServer effectiveRemoteServer, @Nonnull JSONObject remoteJobMetadata)
    {
        this.remoteBuildConfiguration = remoteBuildConfiguration;
        this.queueId = queueId;
        this.buildData = null;
        this.buildInfo = new BuildInfo();
        this.jobName = getParameterFromJobMetadata(remoteJobMetadata, "name");
        this.jobFullName = getParameterFromJobMetadata(remoteJobMetadata, "fullName");
        this.jobDisplayName = getParameterFromJobMetadata(remoteJobMetadata, "displayName");
        this.jobFullDisplayName = getParameterFromJobMetadata(remoteJobMetadata, "fullDisplayName");
        this.jobUrl = getParameterFromJobMetadata(remoteJobMetadata, "url");
        this.currentItem = currentItem;
        this.effectiveRemoteServer = effectiveRemoteServer;
        this.lastLog = "";
        if(trimToNull(currentItem) == null) throw new IllegalArgumentException("currentItem null");
    }

    /**
     * Check if the remote build is still queued (not building yet).
     *
     * @return true if still queued, false if already running.
     * @throws IOException
     *            if there is an error retrieving the remote build number.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     */
    @Whitelisted
    public boolean isQueued() throws IOException, InterruptedException {
        //Return if we already have the buildData
        if(buildData != null) return false;

        PrintStreamWrapper log = new PrintStreamWrapper();
        try {
            //TODO: This currently blocks
            getBuildData(queueId, log.getPrintStream());
            return false;
        } finally {
            lastLog = log.getContent();
        }
    }

    /**
     * Check if the remote job build is finished.
     *
     * @return true if the remote job build ran and finished successfully, otherwise false.
     * @throws IOException
     *            if there is an error retrieving the remote build number, or,
     *            if there is an error retrieving the remote build status, or,
     *            if there is an error retrieving the console output of the remote build, or,
     *            if the remote build does not succeed.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     */
    @Whitelisted
    public boolean isFinished() throws IOException, InterruptedException {
        return buildInfo.getStatus() == BuildStatus.FINISHED;
    }

    /**
     * @return the name or URL of the remote job as configured in the job/pipeline.
     */
    public String getConfiguredJobNameOrUrl() {
        return remoteBuildConfiguration.getJob();
    }

    @CheckForNull
    public String getJobName()
    {
        return jobName;
    }

    @CheckForNull
    public String getJobFullName()
    {
        return jobFullName;
    }

    @CheckForNull
    public String getJobDisplayName()
    {
        return jobDisplayName;
    }

    @CheckForNull
    public String getJobFullDisplayName()
    {
        return jobFullDisplayName;
    }

    @CheckForNull
    public String getJobUrl()
    {
        return jobUrl;
    }

    /**
     * @return the name of the remote job.
     */
    @Nonnull
    public String getQueueId() {
        return queueId;
    }

    /**
     * Get the build URL of the remote build.
     *
     * @return the URL, or null if it could not be identified (yet).
     * @throws IOException
     *            if there is an error retrieving the remote build number, or,
     *            if there is an error retrieving the remote build status, or,
     *            if there is an error retrieving the console output of the remote build, or,
     *            if the remote build does not succeed.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     */
    @Nonnull
    @Whitelisted
    public URL getBuildUrl() throws IOException, InterruptedException {
        //Return if we already have the buildData
        if(buildData != null) return buildData.getURL();

        PrintStreamWrapper log = new PrintStreamWrapper();
        try {
            //TODO: This currently blocks
            BuildData buildData = getBuildData(queueId, log.getPrintStream());
            return buildData.getURL();
        } finally {
            lastLog = log.getContent();
        }
    }

    /**
     * Get the build number of the remote build.
     *
     * @return the number, or -1 if it could not be identified (yet).
     * @throws IOException
     *            if there is an error retrieving the remote build number, or,
     *            if there is an error retrieving the remote build status, or,
     *            if there is an error retrieving the console output of the remote build, or,
     *            if the remote build does not succeed.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     */
    @Whitelisted
    public int getBuildNumber() throws IOException, InterruptedException {
        //Return if we already have the buildData
        if(buildData != null) return buildData.getBuildNumber();

        PrintStreamWrapper log = new PrintStreamWrapper();
        try {
            //TODO: This currently blocks
            BuildData buildData = getBuildData(queueId, log.getPrintStream());
            return buildData.getBuildNumber();
        } finally {
            lastLog = log.getContent();
        }
    }

    /**
     * Gets the current build info of the remote job, containing build status and build result.
     *
     * @return {@link org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.BuildInfo} the build info
     */
    @Nonnull
    @Whitelisted
    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    /**
     * Gets the current build status of the remote job.
     *
     * @return {@link hudson.model.Result} the build result
     *
     * @throws IOException
     *            if there is an error retrieving the remote build number, or,
     *            if there is an error retrieving the remote build status, or,
     *            if there is an error retrieving the console output of the remote build, or,
     *            if the remote build does not succeed.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     */
    @Nonnull
    @Whitelisted
    public BuildStatus getBuildStatus() throws IOException, InterruptedException {
        return getBuildStatus(false);
    }

    /**
     * Gets the build status of the remote build and <b>blocks</b> until it finished.
     *
     * @return {@link org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.BuildStatus} the build status
     * @throws IOException
     *            if there is an error retrieving the remote build number, or,
     *            if there is an error retrieving the remote build status, or,
     *            if there is an error retrieving the console output of the remote build, or,
     *            if the remote build does not succeed.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     */
    @Nonnull
    @Whitelisted
    public BuildStatus getBuildStatusBlocking() throws IOException, InterruptedException {
        return getBuildStatus(true);
    }

    @Nonnull
    private BuildStatus getBuildStatus(boolean blockUntilFinished) throws IOException, InterruptedException {
      //Return if buildStatus exists and is final (does not change anymore)
      if(buildInfo.getStatus() == BuildStatus.FINISHED) return buildInfo.getStatus();

      PrintStreamWrapper log = new PrintStreamWrapper();
      try {
          while(buildInfo.getStatus() != BuildStatus.FINISHED) {
              //TODO: This currently blocks
              BuildData buildData = getBuildData(queueId, log.getPrintStream());
              String jobLocation = buildData.getURL() + "api/json/";
              BuildContext context = new BuildContext(log.getPrintStream(), effectiveRemoteServer, this.currentItem);
              buildInfo = remoteBuildConfiguration.getBuildInfo(jobLocation, context);
              if(!blockUntilFinished) break;
          }
          return buildInfo.getStatus();
      } finally {
          lastLog = log.getContent();
      }
    }

    public void setBuildInfo(BuildInfo buildInfo)
    {
        this.buildInfo = buildInfo;
    }

    /**
     * Gets the current build result of the remote job.
     *
     * @return {@link hudson.model.Result} the build result
     */
    @Nonnull
    @Whitelisted
    public Result getBuildResult() {
        return buildInfo.getResult();
    }

    /**
     * This method returns the log entries which resulted from the last method call
     * to the Handle. This is a workaround since logging to the pipeline log directly does
     * not work yet if used asynchronously.
     *
     * @return The latest log entries from the last called method.
     */
    @Nonnull
    @Whitelisted
    public String lastLog() {
        String log = lastLog.trim();
        lastLog = "";
        return log;
    }

    public void setBuildData(BuildData buildData)
    {
        this.buildData = buildData;
    }

    @Whitelisted
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Handle [job=%s, remoteServerURL=%s, queueId=%s", remoteBuildConfiguration.getJob(), effectiveRemoteServer.getAddress(), queueId));
        if(buildInfo != null) sb.append(String.format(", %s", buildInfo.toString()));
        if(buildData != null) sb.append(String.format(", buildNumber=%s, buildUrl=%s", buildData.getBuildNumber(), buildData.getURL()));
        sb.append("]");
        return sb.toString();
    }

    /**
     * This method returns a all available methods. This might be helpful to get available methods
     * while developing and testing a pipeline script.
     *
     * @return a string representing all the available methods.
     */
    @Whitelisted
    public static String help() {
        StringBuilder sb = new StringBuilder();
        sb.append("This object provides the following methods:\n");
        for (Method method : Handle.class.getDeclaredMethods()) {
            if (method.getAnnotation(Whitelisted.class) != null && Modifier.isPublic(method.getModifiers())) {
            sb.append("- ").append(method.getReturnType().getSimpleName()).append(" ");
            sb.append(method.getName()).append("(");
            Class<?>[] params = method.getParameterTypes();
            for(Class<?> param : params) {
                if(params.length > 1 && !param.equals(params[0])) sb.append( ", ");
                sb.append(param.getSimpleName());
            }
            sb.append(")\n");
          }
        }
        return sb.toString();
    }

    @Nonnull
    private BuildData getBuildData(String queueId, PrintStream logger) throws IOException, InterruptedException
    {
        //Return if we already have the buildData
        if(buildData != null) return buildData;

        BuildContext context = new BuildContext(logger, effectiveRemoteServer, this.currentItem);
        BuildData build = remoteBuildConfiguration.getBuildData(queueId, context);
        this.buildData = build;
        return build;
    }

    /**
     * This method reads and parses a JSON file which has been archived by the last remote build.
     * From Groovy/Pipeline code elements can be accessed directly via object.nodeC.nodeB.leafC.
     *
     * @param filename
     *            the filename or path to the remote JSON file relative to the last builds archive folder
     * @return JSON structure as Object (consisting of Map, List, and primitive types), or null if not available (yet)
     * @throws IOException
     *            if there is an error identifying the remote host, or
     *            if there is an error setting the authorization header, or
     *            if the request fails due to an unknown host, unauthorized credentials, or another reason.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     *
     */
    @Whitelisted
    public Object readJsonFileFromBuildArchive(String filename) throws IOException, InterruptedException {
        if(isEmpty(filename)) return null;

        URL remoteBuildUrl = getBuildUrl();
        URL fileUrl = new URL(remoteBuildUrl, "artifact/" + filename);

        PrintStreamWrapper log = new PrintStreamWrapper();
        try {
            BuildContext context = new BuildContext(log.getPrintStream(), effectiveRemoteServer, this.currentItem);
            return remoteBuildConfiguration.sendHTTPCall(fileUrl.toString(), "GET", context);
        } finally {
            lastLog = log.getContent();
        }
    }

    @CheckForNull
    private String getParameterFromJobMetadata(JSONObject remoteJobMetadata, String string)
    {
        try {
            return trimToNull(remoteJobMetadata.getString("name"));
        }
        catch (JSONException e) {
            return null;
        }
    }

}
