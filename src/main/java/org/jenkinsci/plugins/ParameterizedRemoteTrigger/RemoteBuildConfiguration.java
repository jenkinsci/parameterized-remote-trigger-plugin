package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.StringTools.NL;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2.Auth2Descriptor;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NullAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.ForbiddenException;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.UnauthorizedException;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.pipeline.Handle;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.BuildData;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.RemoteBuildInfo;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.BuildInfoExporterAction;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.QueueItem;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.QueueItemData;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.RemoteBuildQueueStatus;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.RemoteBuildStatus;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.FormValidationUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.FormValidationUtils.AffectedField;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.FormValidationUtils.RemoteURLCombinationsResult;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.TokenMacroUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.util.JSONUtils;

/**
 *
 * @author Maurice W.
 *
 */
@ParametersAreNullableByDefault
public class RemoteBuildConfiguration extends Builder implements SimpleBuildStep, Serializable {

    private static final long serialVersionUID = -4059001060991775146L;

    /**
     * Default for this class is "no auth configured" since we do not want to override potential global config
     */
    private final static Auth2 DEFAULT_AUTH = NullAuth.INSTANCE;


    private static final int      DEFAULT_POLLINTERVALL = 10;
    private static final String   paramerizedBuildUrl   = "/buildWithParameters";
    private static final String   normalBuildUrl        = "/build";
    private static final String   buildTokenRootUrl     = "/buildByToken";
    private static final int      connectionRetryLimit  = 5;

    /**
     * We need to keep this for compatibility - old config deserialization!
     * @deprecated since 2.3.0-SNAPSHOT - use {@link Auth2} instead.
     */
    private transient List<Auth> auth;

    private String        remoteJenkinsName;
    private String        remoteJenkinsUrl;
    private Auth2         auth2;
    private boolean       shouldNotFailBuild;
    private boolean       preventRemoteBuildQueue;
    private int           pollInterval;
    private boolean       blockBuildUntilComplete;
    private String        job;
    private String        token;
    private String        parameters;
    private boolean       enhancedLogging;
    private boolean       loadParamsFromFile;
    private String        parameterFile;


    @DataBoundConstructor
    public RemoteBuildConfiguration() {
        pollInterval = DEFAULT_POLLINTERVALL;
    }

    /*
     * see https://wiki.jenkins.io/display/JENKINS/Hint+on+retaining+backward+compatibility
     */
    @SuppressWarnings("deprecation")
    protected Object readResolve() {
        //migrate Auth To Auth2
        if(auth2 == null) {
            if(auth == null || auth.size() <= 0) {
                auth2 = DEFAULT_AUTH;
            } else {
                auth2 = Auth.authToAuth2(auth);
            }
        }
        auth = null;
        return this;
    }


    @DataBoundSetter
    public void setRemoteJenkinsName(String remoteJenkinsName)
    {
        this.remoteJenkinsName = trimToNull(remoteJenkinsName);
    }

    @DataBoundSetter
    public void setRemoteJenkinsUrl(String remoteJenkinsUrl)
    {
        this.remoteJenkinsUrl = trimToNull(remoteJenkinsUrl);
    }

    @DataBoundSetter
    public void setAuth2(Auth2 auth) {
        this.auth2 = auth;
        // disable old auth
        this.auth = null;
    }

    @DataBoundSetter
    public void setShouldNotFailBuild(boolean shouldNotFailBuild) {
        this.shouldNotFailBuild = shouldNotFailBuild;
    }

    @DataBoundSetter
    public void setPreventRemoteBuildQueue(boolean preventRemoteBuildQueue) {
        this.preventRemoteBuildQueue = preventRemoteBuildQueue;
    }

    @DataBoundSetter
    public void setPollInterval(int pollInterval) {
        if(pollInterval <= 0) this.pollInterval = DEFAULT_POLLINTERVALL;
        else this.pollInterval = pollInterval;
    }

    @DataBoundSetter
    public void setBlockBuildUntilComplete(boolean blockBuildUntilComplete) {
        this.blockBuildUntilComplete = blockBuildUntilComplete;
    }

    @DataBoundSetter
    public void setJob(String job) {
        this.job = trimToNull(job);
    }

    @DataBoundSetter
    public void setToken(String token) {
        if (token == null) this.token = "";
        else this.token = token.trim();
    }

    @DataBoundSetter
    public void setParameters(String parameters) {
        if (parameters == null) this.parameters = "";
        else this.parameters = parameters;
    }

    @DataBoundSetter
    public void setEnhancedLogging(boolean enhancedLogging) {
        this.enhancedLogging = enhancedLogging;
    }

    @DataBoundSetter
    public void setLoadParamsFromFile(boolean loadParamsFromFile) {
        this.loadParamsFromFile = loadParamsFromFile;
    }

    @DataBoundSetter
    public void setParameterFile(String parameterFile) {
        if (loadParamsFromFile && (parameterFile == null || parameterFile.isEmpty()))
          throw new IllegalArgumentException("Parameter file path is empty");

        if (parameterFile == null) this.parameterFile = "";
        else this.parameterFile = parameterFile;
    }

    public List<String> getParameterList(BuildContext context) {
        String params = getParameters();
        if (!params.isEmpty()) {
            String[] parameterArray = params.split("\n");
            return new ArrayList<String>(Arrays.asList(parameterArray));
        } else if (loadParamsFromFile) {
            return loadExternalParameterFile(context);
        } else {
            return new ArrayList<String>();
        }
    }

    /**
     * Reads a file from the jobs workspace, and loads the list of parameters from with in it. It will also call
     * ```getCleanedParameters``` before returning.
     *
     * @param build
     * @return List<String> of build parameters
     */
    private List<String> loadExternalParameterFile(BuildContext context) {

        BufferedReader br = null;
        List<String> parameterList = new ArrayList<String>();
        try {
        	if (context.workspace != null){
                FilePath filePath = context.workspace.child(getParameterFile());
                String sCurrentLine;
                context.logger.println(String.format("Loading parameters from file %s", filePath.getRemote()));

                br = new BufferedReader(new InputStreamReader(filePath.read(), "UTF-8"));

                while ((sCurrentLine = br.readLine()) != null) {
                    parameterList.add(sCurrentLine);
                }
        	} else {
        		context.logger.println("[WARNING] workspace is null");
        	}
        } catch (InterruptedException | IOException e) {
            context.logger.println(String.format("[WARNING] Failed loading parameters: %s", e.getMessage()));
		} finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return getCleanedParameters(parameterList);
    }

    /**
     * Strip out any empty strings from the parameterList
     */
    private void removeEmptyElements(Collection<String> collection) {
        collection.removeAll(Arrays.asList(null, ""));
        collection.removeAll(Arrays.asList(null, " "));
    }

    /**
     * Same as "getParameterList", but removes comments and empty strings Notice that no type of character encoding is
     * happening at this step. All encoding happens in the "buildUrlQueryString" method.
     *
     * @param List
     *            <String> parameters
     * @return List<String> of build parameters
     */
    private List<String> getCleanedParameters(List<String> parameters) {
        List<String> params = new ArrayList<String>(parameters);
        removeEmptyElements(params);
        removeCommentsFromParameters(params);
        return params;
    }

    /**
     * Strip out any comments (lines that start with a #) from the collection that is passed in.
     */
    private void removeCommentsFromParameters(Collection<String> collection) {
        List<String> itemsToRemove = new ArrayList<String>();

        for (String parameter : collection) {
            if (parameter.indexOf("#") == 0) {
                itemsToRemove.add(parameter);
            }
        }
        collection.removeAll(itemsToRemove);
    }

    /**
     * Return the Collection<String> in an encoded query-string.
     *
     * @param parameters
     *           the parameters needed to trigger the remote job.
     * @return query-parameter-formated URL-encoded string.
     */
    private String buildUrlQueryString(Collection<String> parameters) {

        // List to hold the encoded parameters
        List<String> encodedParameters = new ArrayList<String>();

        for (String parameter : parameters) {

            // Step #1 - break apart the parameter-pairs (because we don't want to encode the "=" character)
            String[] splitParameters = parameter.split("=");

            // List to hold each individually encoded parameter item
            List<String> encodedItems = new ArrayList<String>();
            for (String item : splitParameters) {
                try {
                    // Step #2 - encode each individual parameter item add the encoded item to its corresponding list

                    encodedItems.add(encodeValue(item));

                } catch (Exception e) {
                    // do nothing
                    // because we are "hard-coding" the encoding type, there is a 0% chance that this will fail.
                }

            }

            // Step #3 - reunite the previously separated parameter items and add them to the corresponding list
            encodedParameters.add(StringUtils.join(encodedItems, "="));
        }

        return StringUtils.join(encodedParameters, "&");
    }

    /**
     * Tries to identify the effective Remote Host configuration based on the different parameters
     * like  <code>remoteJenkinsName</code> and the globally configured remote host, <code>remoteJenkinsURL</code> which overrides
     * the address locally or <code>job</code> which can be a full job URL.
     *
     * @param context
     *            the context of this Builder/BuildStep.
     * @return {@link RemoteJenkinsServer}
     *            a RemoteJenkinsServer object, never null.
     * @throws AbortException
     *            if no server found and remoteJenkinsUrl empty.
     * @throws MalformedURLException
     *            if <code>remoteJenkinsName</code> no valid URL or <code>job</code> an URL but nor valid.
     */
    @Nonnull
    public RemoteJenkinsServer evaluateEffectiveRemoteHost(BasicBuildContext context) throws IOException {
        RemoteJenkinsServer globallyConfiguredServer = findRemoteHost(this.remoteJenkinsName);
        RemoteJenkinsServer server = globallyConfiguredServer;
        String expandedJob = getJobExpanded(context);
        boolean isJobEmpty = isEmpty(trimToNull(expandedJob));
        boolean isJobUrl = FormValidationUtils.isURL(expandedJob);
        boolean isRemoteUrlEmpty = isEmpty(trimToNull(this.remoteJenkinsUrl));
        boolean isRemoteUrlSet = FormValidationUtils.isURL(this.remoteJenkinsUrl);
        boolean isRemoteNameEmpty = isEmpty(trimToNull(this.remoteJenkinsName));

        if(isJobEmpty) throw new AbortException("Parameter 'Remote Job Name or URL' ('job' variable in Pipeline) not specified.");
        if(!isRemoteUrlEmpty && !isRemoteUrlSet)  throw new AbortException(String.format(
                    "The 'Override remote host URL' parameter value (remoteJenkinsUrl: '%s') is no valid URL", this.remoteJenkinsUrl));

        if(isJobUrl) {
            // Full job URL configured - get remote Jenkins root URL from there
            if(server == null) server = new RemoteJenkinsServer();
            server.setAddress(getRootUrlFromJobUrl(expandedJob));

        } else if(isRemoteUrlSet) {
            // Remote Jenkins root URL overridden locally in Job/Pipeline
            if(server == null) server = new RemoteJenkinsServer();
            server.setAddress(this.remoteJenkinsUrl);

        }

        if (server == null) {
            if(!isJobUrl) {
                if(!isRemoteUrlSet && isRemoteNameEmpty)
                    throw new AbortException("Configuration of the remote Jenkins host is missing.");
                if(!isRemoteUrlSet && !isRemoteNameEmpty && globallyConfiguredServer == null)
                    throw new AbortException(String.format(
                                "Could get remote host with ID '%s' configured in Jenkins global configuration. Please check your global configuration.",
                                this.remoteJenkinsName));
            }
            //Generic error message
            throw new AbortException(String.format(
                        "Could not identify remote host - neither via 'Remote Job Name or URL' (job:'%s'), globally configured"
                        + " remote host (remoteJenkinsName:'%s') nor 'Override remote host URL' (remoteJenkinsUrl:'%s').",
                        expandedJob, this.remoteJenkinsName, this.remoteJenkinsUrl));
        }
        return server;
    }

    /**
     * Lookup up the globally configured Remote Jenkins Server based on display name
     *
     * @param displayName
     *            Name of the configuration you are looking for
     * @return A deep-copy of the RemoteJenkinsServer object configured globally
     */
    public @Nullable @CheckForNull RemoteJenkinsServer findRemoteHost(String displayName) {
        if(isEmpty(displayName)) return null;
        RemoteJenkinsServer server = null;
        for (RemoteJenkinsServer host : this.getDescriptor().remoteSites) {
            // if we find a match, then stop looping
            if (displayName.equals(host.getDisplayName())) {
                try {
                    server = host.clone();
                    break;
                } catch(CloneNotSupportedException e) {
                    // Clone is supported by RemoteJenkinsServer
                    throw new RuntimeException(e);
                }
            }
        }
        return server;
    }

    protected static String removeTrailingSlashes(String string)
    {
        if(isEmpty(string)) return string;
        string = string.trim();
        while(string.endsWith("/")) string = string.substring(0, string.length() - 1);
        return string;
    }

    protected static String removeQueryParameters(String string)
    {
        if(isEmpty(string)) return string;
        string = string.trim();
        int idx = string.indexOf("?");
        if(idx > 0) string = string.substring(0, idx);
        return string;
    }

    protected static String removeHashParameters(String string)
    {
        if(isEmpty(string)) return string;
        string = string.trim();
        int idx = string.indexOf("#");
        if(idx > 0) string = string.substring(0, idx);
        return string;
    }

    private String getRootUrlFromJobUrl(String jobUrl) throws MalformedURLException
    {
        if(isEmpty(jobUrl)) return null;
        if(FormValidationUtils.isURL(jobUrl)) {
            int index = jobUrl.indexOf("/job/");
            if(index < 0) throw new MalformedURLException("Expected '/job/' element in job URL but was: " + jobUrl);
            return jobUrl.substring(0, index);
        } else {
            return null;
        }
    }

    /**
     * Helper function to allow values to be added to the query string from any method.
     *
     * @param item
     */
    private String addToQueryString(String queryString, String item) {
        if (isBlank(queryString)) {
            return item;
        } else {
            return queryString + "&" + item;
        }
    }

    /**
     * Build the proper URL to trigger the remote build
     *
     * All passed in string have already had their tokens replaced with real values. All 'params' also have the proper
     * character encoding
     *
     * @param jobNameOrUrl
     *            Name of the remote job
     * @param securityToken
     *            Security token used to trigger remote job
     * @param params
     *            Parameters for the remote job
     * @return fully formed, fully qualified remote trigger URL
     * @throws MalformedURLException
     */
    private String buildTriggerUrl(String jobNameOrUrl, String securityToken, Collection<String> params, boolean isRemoteJobParameterized,
                BuildContext context) throws IOException {

        String triggerUrlString;
        String query = "";

        if (context.effectiveRemoteServer.getHasBuildTokenRootSupport()) {
          // start building the proper URL based on known capabiltiies of the remote server
            if (context.effectiveRemoteServer.getAddress() == null) {
                throw new AbortException("The remote server address can not be empty, or it must be overridden on the job configuration.");
            }
            triggerUrlString = context.effectiveRemoteServer.getAddress();
            triggerUrlString += buildTokenRootUrl;
            triggerUrlString += getBuildTypeUrl(isRemoteJobParameterized);
            query = addToQueryString(query, "job=" + encodeValue(jobNameOrUrl)); //TODO: does it work with full URL?

        } else {
            triggerUrlString = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
            triggerUrlString += getBuildTypeUrl(isRemoteJobParameterized);
        }

        // don't try to include a security token in the URL if none is provided
        if (!securityToken.equals("")) {
            query = addToQueryString(query, "token=" + encodeValue(securityToken));
        }

        // turn our Collection into a query string
        String buildParams = buildUrlQueryString(params);

        if (!buildParams.isEmpty()) {
            query = addToQueryString(query, buildParams);
        }

        // by adding "delay=0", this will (theoretically) force this job to the top of the remote queue
        query = addToQueryString(query, "delay=0");

        triggerUrlString += "?" + query;

        return triggerUrlString;
    }

    /**
     * Build the proper URL for GET calls.
     *
     * All passed in string have already had their tokens replaced with real values.
     *
     * @param jobNameOrUrl
     *            name or URL of the remote job.
     * @param securityToken
     *            security token used to trigger remote job.
     * @param context
     *            the context of this Builder/BuildStep.
     * @return String
     *            fully formed, fully qualified remote trigger URL.
     * @throws IOException
     *            if there is an error identifying the remote host.
     */
    private String buildGetUrl(String jobNameOrUrl, String securityToken, BuildContext context) throws IOException {

        String urlString = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
        // don't try to include a security token in the URL if none is provided
        if (!isEmpty(securityToken)) {
            urlString += "?token=" + encodeValue(securityToken);
        }
        return urlString;
    }

    /**
     * Convenience function to mark the build as failed. It's intended to only be called from this.perform().
     *
     * @param e
     *            exception that caused the build to fail.
     * @param logger
     *            build listener.
     * @throws IOException
     *            if the build fails and <code>shouldNotFailBuild</code> is not set.
     */
    protected void failBuild(Exception e, PrintStream logger) throws IOException {
        StringBuilder msg = new StringBuilder();
        if(e instanceof InterruptedException) {
            Thread current = Thread.currentThread();
            msg.append(String.format("[Thread: %s/%s]: ", current.getId(), current.getName()));
        }
        msg.append(String.format("Remote build failed with '%s' for the following reason: '%s'.%s",
            e.getClass().getSimpleName(),
            e.getMessage(),
            this.getShouldNotFailBuild() ? " But the build will continue." : ""));
        if(enhancedLogging) {
          msg.append(NL).append(ExceptionUtils.getFullStackTrace(e));
        }
        if(logger != null) logger.println("ERROR: " + msg.toString());
        if (!this.getShouldNotFailBuild()) {
            throw new AbortException(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException,
            IOException, IllegalArgumentException
    {
        FilePath workspace = build.getWorkspace();
        if (workspace == null) throw new IllegalArgumentException("The workspace can not be null");
        perform(build, workspace, launcher, listener);
        return true;
    }

    /**
     * Triggers the remote job and, waits until completion if <code>blockBuildUntilComplete</code> is set.
     *
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     * @throws IOException
     *            if there is an error retrieving the remote build data, or,
     *            if there is an error retrieving the remote build status, or,
     *            if there is an error retrieving the console output of the remote build, or,
     *            if the remote build does not succeed.
     */
    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
          throws InterruptedException, IOException
    {
        RemoteJenkinsServer effectiveRemoteServer = evaluateEffectiveRemoteHost(new BasicBuildContext(build, workspace, listener));
        BuildContext context = new BuildContext(build, workspace, listener, listener.getLogger(), effectiveRemoteServer);
        Handle handle = performTriggerAndGetQueueId(context);
        performWaitForBuild(context, handle);
    }

    /**
     * Triggers the remote job, identifies the queue ID and, returns a <code>Handle</code> to this remote execution.
     *
     * @param context
     *            the context of this Builder/BuildStep.
     * @return Handle
     *            to further tracking of the remote build status.
     * @throws IOException
     *            if there is an error triggering the remote job.
     */
    public Handle performTriggerAndGetQueueId(BuildContext context)
          throws IOException
    {
        List<String> cleanedParams = getCleanedParameters(getParameterList(context));
        String jobNameOrUrl = this.getJob();
        String securityToken = this.getToken();
        try {
            cleanedParams = TokenMacroUtils.applyTokenMacroReplacements(cleanedParams, context);
            jobNameOrUrl = TokenMacroUtils.applyTokenMacroReplacements(jobNameOrUrl, context);
            securityToken = TokenMacroUtils.applyTokenMacroReplacements(securityToken, context);
        } catch(IOException e) {
            this.failBuild(e, context.logger);
        }

        logConfiguration(context, cleanedParams);

        final JSONObject remoteJobMetadata = getRemoteJobMetadata(jobNameOrUrl, context);
        boolean isRemoteParameterized = isRemoteJobParameterized(remoteJobMetadata);

        final String triggerUrlString = this.buildTriggerUrl(jobNameOrUrl, securityToken, cleanedParams, isRemoteParameterized, context);
        final String jobUrlString = this.buildGetUrl(jobNameOrUrl, securityToken, context);

        // Trigger remote job
        context.logger.println(String.format("Triggering %s remote job '%s'",
                    (isRemoteParameterized ? "parameterized" : "non-parameterized"), jobUrlString ));

        logAuthInformation(context);

        // get the ID of the Next Job to run.
        if (this.getPreventRemoteBuildQueue()) {
            context.logger.println("  Checking if the remote job " + jobNameOrUrl + " is currently running.");
            String preCheckUrlString = jobUrlString;
            preCheckUrlString += "/lastBuild";
            preCheckUrlString += "/api/json/";
            JSONObject preCheckResponse = sendHTTPCall(preCheckUrlString, "GET", context);

            if ( preCheckResponse != null ) {
                // check the latest build on the remote server to see if it's running - if so wait until it has stopped.
                // if building is true then the build is running
                // if result is null the build hasn't finished - but might not have started running.
                while (preCheckResponse.getBoolean("building") == true || preCheckResponse.getString("result") == null) {
                    context.logger.println(String.format(
                                "  Remote build is currently running - waiting for it to finish. Next try in %s seconds.",
                                this.pollInterval));
                    try {
                        Thread.sleep(this.pollInterval * 1000);
                    } catch (InterruptedException e) {
                        this.failBuild(e, context.logger);
                    }
                    preCheckResponse = sendHTTPCall(preCheckUrlString, "GET", context);
                }
                context.logger.println("  Remote job " + jobNameOrUrl + " is currently not building.");
            } else {
                this.failBuild(new Exception("Got a blank response from Remote Jenkins Server, cannot continue."), context.logger);
            }

        }

        RemoteBuildInfo buildInfo = new RemoteBuildInfo(); // QueueStatus.NOT_QUEUED

        context.logger.println("Triggering remote job now.");

        ConnectionResponse responseRemoteJob = sendHTTPCall(triggerUrlString, "POST", context, 1);
        QueueItem queueItem = new QueueItem(responseRemoteJob.getHeader());
        buildInfo.setQueueId(queueItem.getId()); // QueueStatus.QUEUED
        buildInfo = updateBuildInfo(buildInfo, context);

        return new Handle(this, buildInfo, context.currentItem, context.effectiveRemoteServer, remoteJobMetadata);
    }

    /**
     * Checks the remote build status and, waits for completion if <code>blockBuildUntilComplete</code> is set.
     *
     * @param context
     *            the context of this Builder/BuildStep.
     * @param handle
     *            the handle to the remote execution.
     * @throws InterruptedException
     *            if any thread has interrupted the current thread.
     * @throws IOException
     *            if there is an error retrieving the remote build data, or,
     *            if there is an error retrieving the remote build status, or,
     *            if there is an error retrieving the console output of the remote build, or,
     *            if the remote build does not succeed.
     */
    public void performWaitForBuild(BuildContext context, Handle handle)
        throws InterruptedException, IOException
    {
        String jobName = handle.getJobName();

        RemoteBuildInfo buildInfo = handle.getBuildInfo();
        String queueId = buildInfo.getQueueId();
        if (queueId == null) {
            throw new AbortException(String.format("Unexpected status: %s. The queue id was not found.", buildInfo.toString()));
        }
        context.logger.println("  Remote job queue number: " + buildInfo.getQueueId());

        if (buildInfo.isQueued()) {
            context.logger.println("Waiting for remote build to be executed...");
        }

        while (buildInfo.isQueued())
        {
            context.logger.println("Waiting for " + this.pollInterval + " seconds until next poll.");
            Thread.sleep(this.pollInterval * 1000);
            buildInfo = updateBuildInfo(buildInfo, context);
            handle.setBuildInfo(buildInfo);
        }

        BuildData buildData = buildInfo.getBuildData();
        if (buildData == null) {
            throw new AbortException(String.format("Unexpected status: %s", buildInfo.toString()));
        }

        int jobNumber = buildData.getBuildNumber();
        URL jobURL = buildData.getURL();

        context.logger.println("  Remote build URL: " + jobURL);
        context.logger.println("  Remote build number: " + jobNumber);

        if(context.run != null) BuildInfoExporterAction.addBuildInfoExporterAction(context.run, jobName, jobNumber, jobURL, buildInfo);

        // If we are told to block until remoteBuildComplete:
        if (this.getBlockBuildUntilComplete()) {
          context.logger.println("Blocking local job until remote job completes.");

          buildInfo = updateBuildInfo(buildInfo, context);
          handle.setBuildInfo(buildInfo);

          if (buildInfo.isNotStarted())
            context.logger.println("Waiting for remote build to start ...");

          while (buildInfo.isNotStarted()) {
              context.logger.println("  Waiting for " + this.pollInterval + " seconds until next poll.");
              // Sleep for 'pollInterval' seconds.
              // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
              try {
                  // Could do with a better way of sleeping...
                  Thread.sleep(this.pollInterval * 1000);
              } catch (InterruptedException e) {
                  this.failBuild(e, context.logger);
              }
              buildInfo = updateBuildInfo(buildInfo, context);
              handle.setBuildInfo(buildInfo);
          }

          if (buildInfo.isRunning()) {
              context.logger.println("Remote build started!");
              context.logger.println("Waiting for remote build to finish ...");
          }

          while (buildInfo.isRunning()) {
              context.logger.println("  Waiting for " + this.pollInterval + " seconds until next poll.");
              // Sleep for 'pollInterval' seconds.
              // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
              try {
                  // Could do with a better way of sleeping...
                  Thread.sleep(this.pollInterval * 1000);
              } catch (InterruptedException e) {
                  this.failBuild(e, context.logger);
              }
              buildInfo = updateBuildInfo(buildInfo, context);
              handle.setBuildInfo(buildInfo);
          }
          context.logger.println("Remote build finished with status " + buildInfo.getResult().toString() + ".");
          if(context.run != null) BuildInfoExporterAction.addBuildInfoExporterAction(context.run, jobName, jobNumber, jobURL, buildInfo);

          if (this.getEnhancedLogging()) {
              String consoleOutput = getConsoleOutput(jobURL, context);

              context.logger.println();
              context.logger.println("Console output of remote job:");
              context.logger.println("--------------------------------------------------------------------------------");
              context.logger.println(consoleOutput);
              context.logger.println("--------------------------------------------------------------------------------");
          }

          // If build did not finish with 'success' then fail build step.
          if (buildInfo.getResult() != Result.SUCCESS) {
              // failBuild will check if the 'shouldNotFailBuild' parameter is set or not, so will decide how to
              // handle the failure.
              this.failBuild(new Exception("The remote job did not succeed."), context.logger);
          }
        } else {
          context.logger.println("Not blocking local job until remote job completes - fire and forget.");
        }
    }

    /**
     * Sends a HTTP request to the API of the remote server requesting a queue item.
     *
     * @param queueId
     *            the id of the remote job on the queue.
     * @param context
     *            the context of this Builder/BuildStep.
     * @return {@link QueueItemData}
     *            the queue item data.
     * @throws IOException
     *            if there is an error identifying the remote host, or
     *            if there is an error setting the authorization header, or
     *            if the request fails due to an unknown host, unauthorized credentials, or another reason, or
     *            if there is an invalid queue response.
     */
    @Nonnull
    private QueueItemData getQueueItemData(@Nonnull String queueId, @Nonnull BuildContext context)
            throws IOException {

      if (context.effectiveRemoteServer.getAddress() == null) {
          throw new AbortException("The remote server address can not be empty, or it must be overridden on the job configuration.");
      }
      String queueQuery = String.format("%s/queue/item/%s/api/json/", context.effectiveRemoteServer.getAddress(), queueId);
      ConnectionResponse response = sendHTTPCall( queueQuery, "GET", context, 1 );
      JSONObject queueResponse = response.getBody();

      if (queueResponse == null || queueResponse.isNullObject()) {
          throw new AbortException(String.format("Unexpected queue item response: code %s for request %s", response.getResponseCode(), queueQuery));
      }

      QueueItemData queueItem = new QueueItemData(context, queueResponse);

      if (queueItem.isBlocked())
        context.logger.println("The remote job is blocked. Reason: " + queueItem.getWhy() + ".");

      if (queueItem.isPending())
        context.logger.println("The remote job is pending. Reason: " + queueItem.getWhy() + ".");

      if (queueItem.isBuildable())
        context.logger.println("The remote job is buildable. Reason: " + queueItem.getWhy() + ".");

      if (queueItem.isCancelled())
        throw new AbortException("The remote job was canceled");

      return queueItem;
    }

    @Nonnull
    public RemoteBuildInfo updateBuildInfo(@Nonnull RemoteBuildInfo buildInfo, @Nonnull BuildContext context) throws IOException {

        if (buildInfo.isNotQueued()) return buildInfo;

        if (buildInfo.isQueued()) {
            String queueId = buildInfo.getQueueId();
            if (queueId == null) {
                throw new AbortException(String.format("Unexpected status: %s. The queue id was not found.", buildInfo.toString()));
            }
            QueueItemData queueItem = getQueueItemData(queueId, context);
            BuildData buildData = queueItem.getBuildData(context);
            if (queueItem.isExecutable() && buildData!=null) {
                buildInfo.setBuildData(buildData);  // QueueStatus.EXECUTED
            }
            return buildInfo;
        }

        // QueueStatus.EXECUTED
        BuildData buildData = buildInfo.getBuildData();
        if (buildData == null) {
            throw new AbortException(String.format("Unexpected status: %s", buildInfo.toString()));
        }
        String buildUrlString = buildData.getURL() + "api/json/";
        JSONObject responseObject = sendHTTPCall(buildUrlString, "GET", context);

        try {
            if (responseObject == null || responseObject.getString("result") == null && !responseObject.getBoolean("building")) {
                return buildInfo;
            } else if (responseObject.getBoolean("building")) {
                buildInfo.setBuildStatus(RemoteBuildStatus.RUNNING);
            } else if (responseObject.getString("result") != null) {
                buildInfo.setBuildResult(responseObject.getString("result"));
            } else {
                context.logger.println("WARNING: Unhandled condition!");
            }
        } catch (Exception ex) {
        }
        return buildInfo;
    }

    private String getConsoleOutput(URL url, BuildContext context)
            throws IOException {

            return getConsoleOutput( url, context, 1 );
    }

    /**
     * Orchestrates all calls to the remote server.
     * Also takes care of any credentials or failed-connection retries.
     *
     * @param urlString
     *            the URL that needs to be called.
     * @param requestType
     *            the type of request (GET, POST, etc).
     * @param context
     *            the context of this Builder/BuildStep.
     * @return JSONObject
     *            a valid JSON object, or null.
     * @throws IOException
     *            if there is an error identifying the remote host, or
     *            if there is an error setting the authorization header, or
     *            if the request fails due to an unknown host, unauthorized credentials, or another reason.
     */
    public JSONObject sendHTTPCall(String urlString, String requestType, BuildContext context)
            throws IOException {

            return sendHTTPCall( urlString, requestType, context, 1 ).getBody();
    }

    private String getConsoleOutput(URL url, BuildContext context, int numberOfAttempts)
            throws IOException {

        int retryLimit = this.getConnectionRetryLimit();

        String consoleOutput = null;

        URL buildUrl = new URL(url, "consoleText");

        HttpURLConnection connection = getAuthorizedConnection(context, buildUrl);

        int responseCode = 0;
        try {
            connection.setDoInput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("GET");
            // wait up to 5 seconds for the connection to be open
            connection.setConnectTimeout(5000);
            connection.connect();
            responseCode = connection.getResponseCode();
            if(responseCode == 401) {
                throw new UnauthorizedException(buildUrl);
            } else if(responseCode == 403) {
                throw new ForbiddenException(buildUrl);
            } else {
                consoleOutput = readInputStream(connection);
            }
        } catch (UnknownHostException e) {
            this.failBuild(e, context.logger);
        } catch (UnauthorizedException e) {
            this.failBuild(e, context.logger);
        } catch (ForbiddenException e) {
            this.failBuild(e, context.logger);
        } catch (IOException e) {

            //If we have connectionRetryLimit set to > 0 then retry that many times.
            if( numberOfAttempts <= retryLimit) {
                context.logger.println(String.format(
                      "Connection to remote server failed %s, waiting for to retry - %s seconds until next attempt. URL: %s",
                      (responseCode == 0 ? "" : "[" + responseCode + "]"), this.pollInterval, url));
                e.printStackTrace();

                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException ex) {
                    this.failBuild(ex, context.logger);
                }

                context.logger.println("Retry attempt #" + numberOfAttempts + " out of " + retryLimit );
                numberOfAttempts++;
                consoleOutput = getConsoleOutput(url, context, numberOfAttempts);
            } else if(numberOfAttempts > retryLimit){
                //reached the maximum number of retries, time to fail
                this.failBuild(new Exception("Max number of connection retries have been exeeded."), context.logger);
            } else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
                this.failBuild(e, context.logger);
            }

        } finally {
            // always make sure we close the connection
            if (connection != null) {
                connection.disconnect();
            }
        }
        return consoleOutput;
    }

    /**
     * Same as sendHTTPCall, but keeps track of the number of failed connection attempts (aka: the number of times this
     * method has been called).
     * In the case of a failed connection, the method calls it self recursively and increments the number of attempts.
     *
     * @see sendHTTPCall
     * @param urlString
     *            the URL that needs to be called.
     * @param requestType
     *            the type of request (GET, POST, etc).
     * @param context
     *            the context of this Builder/BuildStep.
     * @param numberOfAttempts
     *            number of time that the connection has been attempted.
     * @return {@link ConnectionResponse}
     *            the response to the HTTP request.
     * @throws IOException
     *            if there is an error identifying the remote host, or
     *            if there is an error setting the authorization header, or
     *            if the request fails due to an unknown host or unauthorized credentials, or
     *            if the request fails due to another reason and the number of attempts is exceeded.
     */
    private ConnectionResponse sendHTTPCall(String urlString, String requestType, BuildContext context, int numberOfAttempts)
            throws IOException {

        int retryLimit = this.getConnectionRetryLimit();

        JSONObject responseObject = null;
        Map<String,List<String>> responseHeader = null;
        int responseCode = 0;

        URL url = new URL(urlString);
        HttpURLConnection connection = getAuthorizedConnection(context, url);

        try {
            connection.setDoInput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod(requestType);
            addCrumbToConnection(connection, context);
            // wait up to 5 seconds for the connection to be open
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.connect();
            responseHeader = connection.getHeaderFields();
            responseCode = connection.getResponseCode();
            if(responseCode == 401) {
                throw new UnauthorizedException(url);
            } else if(responseCode == 403) {
                throw new ForbiddenException(url);
            } else {
                String response = trimToNull(readInputStream(connection));

                // JSONSerializer serializer = new JSONSerializer();
                // need to parse the data we get back into struct
                //listener.getLogger().println("Called URL: '" + urlString +  "', got response: '" + response.toString() + "'");

                //Solving issue reported in this comment: https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/3#issuecomment-39369194
                //Seems like in Jenkins version 1.547, when using "/build" (job API for non-parameterized jobs), it returns a string indicating the status.
                //But in newer versions of Jenkins, it just returns an empty response.
                //So we need to compensate and check for both.
                if ( responseCode >= 400 || JSONUtils.mayBeJSON(response) == false) {
                    return new ConnectionResponse(responseHeader, responseCode);
                } else {
                    responseObject = (JSONObject) JSONSerializer.toJSON(response);
                }
            }

        } catch (UnknownHostException e) {
            this.failBuild(e, context.logger);
        } catch (UnauthorizedException e) {
            this.failBuild(e, context.logger);
        } catch (ForbiddenException e) {
            this.failBuild(e, context.logger);
        } catch (IOException e) {

            //E.g. "HTTP/1.1 403 No valid crumb was included in the request"
            List<String> hints = responseHeader != null ? responseHeader.get(null) : null;
            String hintsString = (hints != null && hints.size() > 0) ? " - " + hints.toString() : "";

            context.logger.println(e.getMessage() + hintsString);
            //If we have connectionRetryLimit set to > 0 then retry that many times.
            if( numberOfAttempts <= retryLimit) {
                context.logger.println(String.format(
                      "Connection to remote server failed %s, waiting for to retry - %s seconds until next attempt. URL: %s",
                      (responseCode == 0 ? "" : "[" + responseCode + "]"), this.pollInterval, urlString));
                e.printStackTrace();

                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException ex) {
                    this.failBuild(ex, context.logger);
                }

                context.logger.println("Retry attempt #" + numberOfAttempts + " out of " + retryLimit );
                numberOfAttempts++;
                responseObject = sendHTTPCall(urlString, requestType, context, numberOfAttempts).getBody();
            }else if(numberOfAttempts > retryLimit){
                //reached the maximum number of retries, time to fail
                this.failBuild(new Exception("Max number of connection retries have been exeeded."), context.logger);
            }else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
                this.failBuild(e, context.logger);
            }

        } finally {
            // always make sure we close the connection
            if (connection != null) {
                connection.disconnect();
            }
        }
        return new ConnectionResponse(responseHeader, responseObject, responseCode);
    }

    /**
     * For POST requests a crumb is needed. This methods gets a crumb and sets it in the header.
     * https://wiki.jenkins.io/display/JENKINS/Remote+access+API#RemoteaccessAPI-CSRFProtection
     *
     * @param connection
     * @param context
     * @throws IOException
     */
    private void addCrumbToConnection(HttpURLConnection connection, BuildContext context) throws IOException
    {
        String method = connection.getRequestMethod();
        if(method != null && method.equalsIgnoreCase("POST")) {
            JenkinsCrumb crumb = getCrumb(context);
            if (crumb.isEnabledOnRemote()) {
                connection.setRequestProperty(crumb.getHeaderId(), crumb.getCrumbValue());
            }
        }
    }

    private String readInputStream(HttpURLConnection connection) throws IOException
    {
        BufferedReader rd = null;
        try {

         InputStream is;
         try {
             is = connection.getInputStream();
         } catch (FileNotFoundException e) {
             // In case of a e.g. 404 status
             is = connection.getErrorStream();
         }

         rd = new BufferedReader(new InputStreamReader(is, "UTF-8"));
         String line;
         StringBuilder response = new StringBuilder();
         while ((line = rd.readLine()) != null) {
             if(response.length() > 0) response.append(NL);
             response.append(line);
         }
         return response.toString();

        } finally {
            closeQuietly(rd);
        }
    }

    /**
     * Tries to obtain a Jenkins Crumb from the remote Jenkins server.
     *
     * @param effectiveRemoteServer
     *            the remote Jenkins server.
     * @param context
     *            the context of this Builder/BuildStep.
     * @return {@link JenkinsCrumb}
     *            a JenkinsCrumb.
     * @throws IOException
     *            if the request failed.
     */
    @Nonnull
    private JenkinsCrumb getCrumb(BuildContext context) throws IOException
    {
        String address = context.effectiveRemoteServer.getAddress();
        if (address == null) {
            throw new AbortException("The remote server address can not be empty, or it must be overridden on the job configuration.");
        }
        URL crumbProviderUrl;
        try {
            String xpathValue = URLEncoder.encode("concat(//crumbRequestField,\":\",//crumb)", "UTF-8");
            crumbProviderUrl = new URL(address.concat("/crumbIssuer/api/xml?xpath=").concat(xpathValue));
            HttpURLConnection connection = getAuthorizedConnection(context, crumbProviderUrl);
            int responseCode = connection.getResponseCode();
            if(responseCode == 401) {
                throw new UnauthorizedException(crumbProviderUrl);
            } else if(responseCode == 403) {
                throw new ForbiddenException(crumbProviderUrl);
            } else if(responseCode == 404) {
                context.logger.println("CSRF protection is disabled on the remote server.");
                return new JenkinsCrumb();
            } else if(responseCode == 200){
                context.logger.println("CSRF protection is enabled on the remote server.");
                String response = readInputStream(connection);
                String[] split = response.split(":");
                return new JenkinsCrumb(split[0], split[1]);
            } else {
                throw new RuntimeException(String.format("Unexpected response. Response code: %s. Response message: %s", responseCode, connection.getResponseMessage()));
            }
        } catch (FileNotFoundException e) {
            context.logger.println("CSRF protection is disabled on the remote server.");
            return new JenkinsCrumb();
        }
    }

    private HttpURLConnection getAuthorizedConnection(BuildContext context, URL url) throws IOException
    {
        URLConnection connection = url.openConnection();

        Auth2 serverAuth = context.effectiveRemoteServer.getAuth2();
        Auth2 overrideAuth = this.getAuth2();

        if(overrideAuth != null && !(overrideAuth instanceof NullAuth)) {
            //Override Authorization Header if configured locally
            overrideAuth.setAuthorizationHeader(connection, context);
        } else if (serverAuth != null) {
            //Set Authorization Header configured globally for remoteServer
            serverAuth.setAuthorizationHeader(connection, context);
        }

        return (HttpURLConnection)connection;
    }

    private void logAuthInformation(BuildContext context) throws IOException {

        Auth2 serverAuth = context.effectiveRemoteServer.getAuth2();
        Auth2 localAuth = this.getAuth2();
        if(localAuth != null && !(localAuth instanceof NullAuth)) {
            String authString = (context.run == null) ? localAuth.getDescriptor().getDisplayName() : localAuth.toString((Item)context.run.getParent());
            context.logger.println(String.format("  Using job-level defined " + authString ));
        } else if(serverAuth != null && !(serverAuth instanceof NullAuth)) {
            String authString = (context.run == null) ? serverAuth.getDescriptor().getDisplayName() : serverAuth.toString((Item)context.run.getParent());
            context.logger.println(String.format("  Using globally defined " + authString));
        } else {
            context.logger.println("  No credentials configured");
        }
    }

    private void logConfiguration(BuildContext context, List<String> effectiveParams) throws IOException {
        String _job = getJob();
        String _jobExpanded = getJobExpanded(context);
        String _jobExpandedLogEntry = (_job.equals(_jobExpanded)) ? "" : "(" + _jobExpanded + ")";
        String _remoteJenkinsName = getRemoteJenkinsName();
        String _remoteJenkinsUrl = getRemoteJenkinsUrl();
        Auth2 _auth = getAuth2();
        int _connectionRetryLimit = getConnectionRetryLimit();
        boolean _blockBuildUntilComplete = getBlockBuildUntilComplete();
        String _parameterFile = getParameterFile();
        String _parameters = (effectiveParams == null || effectiveParams.size() <= 0) ? "" : effectiveParams.toString();
        boolean _loadParamsFromFile = getLoadParamsFromFile();
        context.logger.println("################################################################################################################");
        context.logger.println("  Parameterized Remote Trigger Configuration:");
        context.logger.println(
                    String.format("    - job:                     %s %s", _job, _jobExpandedLogEntry));
        if(!isEmpty(_remoteJenkinsName)) {
            context.logger.println(
                    String.format("    - remoteJenkinsName:       %s", _remoteJenkinsName));
        }
        if(!isEmpty(_remoteJenkinsUrl)) {
            context.logger.println(
                    String.format("    - remoteJenkinsUrl:        %s", _remoteJenkinsUrl));
        }
        if(_auth != null && !(_auth instanceof NullAuth)) {
            String authString = context.run == null ? _auth.getDescriptor().getDisplayName() : _auth.toString((Item)context.run.getParent());
            context.logger.println(
                    String.format("    - auth:                    %s", authString));
        }
        context.logger.println(
                    String.format("    - parameters:              %s", _parameters));
        if(_loadParamsFromFile) {
            context.logger.println(
                    String.format("    - loadParamsFromFile:      %s", _loadParamsFromFile));
            context.logger.println(
                    String.format("    - parameterFile:           %s", _parameterFile));
        }
        context.logger.println(
                    String.format("    - blockBuildUntilComplete: %s", _blockBuildUntilComplete));
        context.logger.println(
                    String.format("    - connectionRetryLimit:    %s", _connectionRetryLimit));
        context.logger.println("################################################################################################################");
    }

    /**
     * Helper function for character encoding
     *
     * @param dirtyValue
     * @return encoded value
     */
    private static String encodeValue(String dirtyValue) {
        String cleanValue = "";

        try {
            cleanValue = URLEncoder.encode(dirtyValue, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return cleanValue;
    }

    /**
     * @return the configured remote Jenkins name. That's the ID of a globally configured remote host.
     */
    public String getRemoteJenkinsName() {
        return remoteJenkinsName;
    }

    /**
     * @return the configured remote Jenkins URL. This is not necessarily the effective Jenkins URL, e.g. if a full URL is specified for <code>job</code>!
     */
    public String getRemoteJenkinsUrl()
    {
        return trimToNull(remoteJenkinsUrl);
    }

    /**
     * @return true, if the authorization is overridden in the job configuration, otherwise false.
     * @deprecated since 2.3.0-SNAPSHOT - use {@link #getAuth2()} instead.
     */
    public boolean getOverrideAuth() {
        if(auth2 == null) return false;
        if(auth2 instanceof NullAuth) return false;
        return true;
    }

    public Auth2 getAuth2() {
        return (auth2 != null) ? auth2 : DEFAULT_AUTH;
    }

    public boolean getShouldNotFailBuild() {
        return shouldNotFailBuild;
    }

    public boolean getPreventRemoteBuildQueue() {
        return preventRemoteBuildQueue;
    }

    public int getPollInterval() {
        return pollInterval;
    }

    public boolean getBlockBuildUntilComplete() {
        return blockBuildUntilComplete;
    }

    /**
     * @return the configured <code>job</code> value. Can be a job name or full job URL.
     */
    public String getJob() {
        return trimToEmpty(job);
    }

    /**
     * @return job value with expanded env vars.
     * @throws IOException
     *             if there is an error replacing tokens.
     */
    private String getJobExpanded(BasicBuildContext context) throws IOException {
        return TokenMacroUtils.applyTokenMacroReplacements(getJob(), context);
    }

    public String getToken() {
        return trimToEmpty(token);
    }

    public String getParameters() {
        return trimToEmpty(parameters);
    }

    public boolean getEnhancedLogging() {
        return enhancedLogging;
    }

    public boolean getLoadParamsFromFile() {
        return loadParamsFromFile;
    }

    public String getParameterFile() {
        return trimToEmpty(parameterFile);
    }

    public int getConnectionRetryLimit() {
        return connectionRetryLimit; // For now, this is a constant
    }

    /**
     * Same as above, but takes in to consideration if the remote server has any default parameters set or not
     * @param isRemoteJobParameterized Boolean indicating if the remote job is parameterized or not
     * @return A string which represents a portion of the build URL
     */
    private String getBuildTypeUrl(boolean isRemoteJobParameterized) {
        boolean isParameterized = false;

        if(isRemoteJobParameterized || (this.getParameters().length() > 0)) {
            isParameterized = true;
        }

        if (isParameterized) {
            return paramerizedBuildUrl;
        } else {
            return normalBuildUrl;
        }
    }

    private @Nonnull JSONObject getRemoteJobMetadata(String jobNameOrUrl, BuildContext context) throws IOException {

        String remoteJobUrl = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
        remoteJobUrl += "/api/json";

        ConnectionResponse response = sendHTTPCall( remoteJobUrl, "GET", context, 1 );
        if(response.getResponseCode() < 400 && response.getBody() != null) {

            return response.getBody();

        } else if(response.getResponseCode() == 401 || response.getResponseCode() == 403) {
          throw new AbortException("Unauthorized to trigger " + remoteJobUrl + " - status code " + response.getResponseCode());
        } else if(response.getResponseCode() == 404) {
          throw new AbortException("Remote job does not exist " + remoteJobUrl + " - status code " + response.getResponseCode());
        } else {
          throw new AbortException("Unexpected response from " + remoteJobUrl + " - status code " + response.getResponseCode());
        }
    }

    /**
     * Pokes the remote server to see if it has default parameters defined or not.
     *
     * @param remoteJobMetadata
     *             from {@link #getRemoteJobMetadata(String, BuildContext)}.
     * @return true if the remote job has parameters, otherwise false.
     * @throws IOException
     *             if it is not possible to identify if the job is parameterized.
     */
    private boolean isRemoteJobParameterized(JSONObject remoteJobMetadata) throws IOException
    {
        boolean isParameterized = false;
        if (remoteJobMetadata != null) {
            if (remoteJobMetadata.getJSONArray("actions").size() >= 1) {
                for(Object obj : remoteJobMetadata.getJSONArray("actions")) {
                    if (obj instanceof JSONObject && ((JSONObject) obj).get("parameterDefinitions") != null) {
                        isParameterized = true;
                    }
                }
            }
        }
        else {
            throw new AbortException("Could not identify if job is parameterized. Job metadata not accessible or with unexpected content.");
        }
        return isParameterized;
    }

    protected static String generateJobUrl(RemoteJenkinsServer remoteServer, String jobNameOrUrl) throws AbortException
    {
        if(isEmpty(jobNameOrUrl)) throw new IllegalArgumentException("Invalid job name/url: " + jobNameOrUrl);
        String remoteJobUrl;
        String _jobNameOrUrl = jobNameOrUrl.trim();
        if(FormValidationUtils.isURL(_jobNameOrUrl)) {
            remoteJobUrl = _jobNameOrUrl;
        } else {
            remoteJobUrl = remoteServer.getAddress();
            if (remoteJobUrl == null) {
                throw new AbortException("The remote server address can not be empty, or it must be overridden on the job configuration.");
            }
            while(remoteJobUrl.endsWith("/")) remoteJobUrl = remoteJobUrl.substring(0, remoteJobUrl.length()-1);

            String[] split = _jobNameOrUrl.trim().split("/");
            for(String segment : split) {
                remoteJobUrl = String.format("%s/job/%s", remoteJobUrl, encodeValue(segment));
            }
        }
        return remoteJobUrl;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    // This indicates to Jenkins that this is an implementation of an extension
    // point.
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * To persist global configuration information, simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
        private CopyOnWriteList<RemoteJenkinsServer> remoteSites = new CopyOnWriteList<RemoteJenkinsServer>();

        /**
         * In order to load the persisted global configuration, you have to call load() in the constructor.
         */
        public DescriptorImpl() {
            this(true);
        }

        private DescriptorImpl(boolean load) {
            if(load) load();
        }

        public static DescriptorImpl newInstanceForTests()
        {
            return new DescriptorImpl(false);
        }

        /*
        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value
         *            This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         */
        /*
         * public FormValidation doCheckName(@QueryParameter String value) throws IOException, ServletException { if
         * (value.length() == 0) return FormValidation.error("Please set a name"); if (value.length() < 4) return
         * FormValidation.warning("Isn't the name too short?"); return FormValidation.ok(); }
         */

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project
            // types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Trigger a remote parameterized job";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

            remoteSites.replaceBy(req.bindJSONToList(RemoteJenkinsServer.class, formData.get("remoteSites")));
            save();

            return super.configure(req, formData);
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckJob(
                    @QueryParameter("job") final String value,
                    @QueryParameter("remoteJenkinsUrl") final String remoteJenkinsUrl,
                    @QueryParameter("remoteJenkinsName") final String remoteJenkinsName) {
            RemoteURLCombinationsResult result = FormValidationUtils.checkRemoteURLCombinations(remoteJenkinsUrl, remoteJenkinsName, value);
            if(result.isAffected(AffectedField.JOB_NAME_OR_URL)) return result.formValidation;
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckRemoteJenkinsUrl(
                    @QueryParameter("remoteJenkinsUrl") final String value,
                    @QueryParameter("remoteJenkinsName") final String remoteJenkinsName,
                    @QueryParameter("job") final String job) {
            RemoteURLCombinationsResult result = FormValidationUtils.checkRemoteURLCombinations(value, remoteJenkinsName, job);
            if(result.isAffected(AffectedField.REMOTE_JENKINS_URL)) return result.formValidation;
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckRemoteJenkinsName(
                    @QueryParameter("remoteJenkinsName") final String value,
                    @QueryParameter("remoteJenkinsUrl") final String remoteJenkinsUrl,
                    @QueryParameter("job") final String job) {
            RemoteURLCombinationsResult result = FormValidationUtils.checkRemoteURLCombinations(remoteJenkinsUrl, value, job);
            if(result.isAffected(AffectedField.REMOTE_JENKINS_NAME)) return result.formValidation;
            return FormValidation.ok();
        }

        @Restricted(NoExternalUse.class)
        @Nonnull
        public ListBoxModel doFillRemoteJenkinsNameItems() {
            ListBoxModel model = new ListBoxModel();

            model.add("");
            for (RemoteJenkinsServer site : getRemoteSites()) {
                model.add(site.getDisplayName());
            }

            return model;
        }

        public RemoteJenkinsServer[] getRemoteSites() {

            return remoteSites.toArray(new RemoteJenkinsServer[this.remoteSites.size()]);
        }

        public void setRemoteSites(RemoteJenkinsServer... remoteSites) {
            this.remoteSites.replaceBy(remoteSites);
        }

        public static List<Auth2Descriptor> getAuth2Descriptors() {
            return Auth2.all();
        }

        public static Auth2Descriptor getDefaultAuth2Descriptor() {
            return NullAuth.DESCRIPTOR;
        }

    }

}
