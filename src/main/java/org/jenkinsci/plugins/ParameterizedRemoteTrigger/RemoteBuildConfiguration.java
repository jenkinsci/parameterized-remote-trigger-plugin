package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.apache.commons.lang.StringUtils.stripAll;
import static org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.StringTools.NL;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNullableByDefault;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2.Auth2Descriptor;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NullAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.pipeline.Handle;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.QueueItem;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.QueueItemData;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.RemoteBuildInfo;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.RemoteBuildInfoExporterAction;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.remoteJob.RemoteBuildStatus;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.DropCachePeriodicWork;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.FormValidationUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.FormValidationUtils.AffectedField;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.FormValidationUtils.RemoteURLCombinationsResult;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.HttpHelper;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.RestUtils;
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

/**
 *
 * @author Maurice W.
 *
 */
@ParametersAreNullableByDefault
public class RemoteBuildConfiguration extends Builder implements SimpleBuildStep, Serializable {

	private static final long serialVersionUID = -4059001060991775146L;

	/**
	 * Default for this class is "no auth configured" since we do not want to
	 * override potential global config
	 */
	private final static Auth2 DEFAULT_AUTH = NullAuth.INSTANCE;

	private static final int DEFAULT_POLLINTERVALL = 10;
	private static final int connectionRetryLimit = 5;

	/**
	 * We need to keep this for compatibility - old config deserialization!
	 * 
	 * @deprecated since 2.3.0-SNAPSHOT - use {@link Auth2} instead.
	 */
	private transient List<Auth> auth;

	private String remoteJenkinsName;
	private String remoteJenkinsUrl;
	private Auth2 auth2;
	private boolean shouldNotFailBuild;
	private boolean preventRemoteBuildQueue;
	private int pollInterval;
	private boolean blockBuildUntilComplete;
	private String job;
	private String token;
	private String parameters;
	private boolean enhancedLogging;
	private boolean loadParamsFromFile;
	private String parameterFile;
	private int maxConn;
	private boolean useCrumbCache;
	private boolean useJobInfoCache;
	private boolean abortTriggeredJob;
	
	private Map<String, Semaphore> hostLocks = new HashMap<>();
	private Map<String, Integer> hostPermits = new HashMap<>();

	private static Logger logger = Logger.getLogger(RemoteBuildConfiguration.class.getName());

	@DataBoundConstructor
	public RemoteBuildConfiguration() {
		pollInterval = DEFAULT_POLLINTERVALL;
	}

	/*
	 * see https://wiki.jenkins.io/display/JENKINS/Hint+on+retaining+backward+
	 * compatibility
	 */
	@SuppressWarnings("deprecation")
	protected Object readResolve() {
		// migrate Auth To Auth2
		if (auth2 == null) {
			if (auth == null || auth.size() <= 0) {
				auth2 = DEFAULT_AUTH;
			} else {
				auth2 = Auth.authToAuth2(auth);
			}
		}
		auth = null;
		if (hostLocks == null) {
			hostLocks = new HashMap<>();
		}
		if (hostPermits == null) {
			hostPermits = new HashMap<>();
		}
		return this;
	}
	
	@DataBoundSetter
	public void setAbortTriggeredJob(boolean abortTriggeredJob) {
		this.abortTriggeredJob = abortTriggeredJob;
	}

	@DataBoundSetter
	public void setMaxConn(int maxConn) {
		this.maxConn = (maxConn > 5) ? 5 : maxConn;
	}

	@DataBoundSetter
	public void setRemoteJenkinsName(String remoteJenkinsName) {
		this.remoteJenkinsName = trimToNull(remoteJenkinsName);
	}

	@DataBoundSetter
	public void setRemoteJenkinsUrl(String remoteJenkinsUrl) {
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
		if (pollInterval <= 0)
			this.pollInterval = DEFAULT_POLLINTERVALL;
		else
			this.pollInterval = pollInterval;
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
		if (token == null)
			this.token = "";
		else
			this.token = token.trim();
	}

	@DataBoundSetter
	public void setParameters(String parameters) {
		if (parameters == null)
			this.parameters = "";
		else
			this.parameters = parameters;
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

		if (parameterFile == null)
			this.parameterFile = "";
		else
			this.parameterFile = parameterFile;
	}

	public List<String> getParameterList(BuildContext context) {
		String params = getParameters();
		if (!params.isEmpty()) {
			String[] parameterArray = params.split("\n");
			parameterArray = stripAll(parameterArray);
			return new ArrayList<String>(Arrays.asList(parameterArray));
		} else if (loadParamsFromFile) {
			return loadExternalParameterFile(context);
		} else {
			return new ArrayList<String>();
		}
	}

	/**
	 * Reads a file from the jobs workspace, and loads the list of parameters from
	 * with in it. It will also call ```getCleanedParameters``` before returning.
	 *
	 * @param build
	 * @return List<String> of build parameters
	 */
	private List<String> loadExternalParameterFile(BuildContext context) {

		BufferedReader br = null;
		List<String> parameterList = new ArrayList<String>();
		try {
			if (context.workspace != null) {
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
	 * Same as "getParameterList", but removes comments and empty strings Notice
	 * that no type of character encoding is happening at this step. All encoding
	 * happens in the "buildUrlQueryString" method.
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
	 * Strip out any comments (lines that start with a #) from the collection that
	 * is passed in.
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
	 * Tries to identify the effective Remote Host configuration based on the
	 * different parameters like <code>remoteJenkinsName</code> and the globally
	 * configured remote host, <code>remoteJenkinsURL</code> which overrides the
	 * address locally or <code>job</code> which can be a full job URL.
	 *
	 * @param context
	 *            the context of this Builder/BuildStep.
	 * @return {@link RemoteJenkinsServer} a RemoteJenkinsServer object, never null.
	 * @throws AbortException
	 *             if no server found and remoteJenkinsUrl empty.
	 * @throws MalformedURLException
	 *             if <code>remoteJenkinsName</code> no valid URL or
	 *             <code>job</code> an URL but nor valid.
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

		if (isJobEmpty)
			throw new AbortException("Parameter 'Remote Job Name or URL' ('job' variable in Pipeline) not specified.");
		if (!isRemoteUrlEmpty && !isRemoteUrlSet)
			throw new AbortException(String.format(
					"The 'Override remote host URL' parameter value (remoteJenkinsUrl: '%s') is no valid URL",
					this.remoteJenkinsUrl));

		if (isJobUrl) {
			// Full job URL configured - get remote Jenkins root URL from there
			if (server == null)
				server = new RemoteJenkinsServer();
			server.setAddress(getRootUrlFromJobUrl(expandedJob));

		} else if (isRemoteUrlSet) {
			// Remote Jenkins root URL overridden locally in Job/Pipeline
			if (server == null)
				server = new RemoteJenkinsServer();
			server.setAddress(this.remoteJenkinsUrl);

		}

		if (server == null) {
			if (!isJobUrl) {
				if (!isRemoteUrlSet && isRemoteNameEmpty)
					throw new AbortException("Configuration of the remote Jenkins host is missing.");
				if (!isRemoteUrlSet && !isRemoteNameEmpty && globallyConfiguredServer == null)
					throw new AbortException(String.format(
							"Could get remote host with ID '%s' configured in Jenkins global configuration. Please check your global configuration.",
							this.remoteJenkinsName));
			}
			// Generic error message
			throw new AbortException(String.format(
					"Could not identify remote host - neither via 'Remote Job Name or URL' (job:'%s'), globally configured"
							+ " remote host (remoteJenkinsName:'%s') nor 'Override remote host URL' (remoteJenkinsUrl:'%s').",
					expandedJob, this.remoteJenkinsName, this.remoteJenkinsUrl));
		}

		String addr = server.getAddress();
		if (addr != null) {
			URL url = new URL(addr);
			Semaphore s = hostLocks.get(url.getHost());
			Integer lastPermit = hostPermits.get(url.getHost());
			int maxConn = getMaxConn();
			if (s == null || lastPermit == null || maxConn != lastPermit) {
				s = new Semaphore(maxConn);
				hostLocks.put(url.getHost(), s);
				hostPermits.put(url.getHost(), maxConn);
			}
		}

		return server;
	}

	public Semaphore getLock(String addr) {
		Semaphore s = null;
		try {
			URL url = new URL(addr);
			s = hostLocks.get(url.getHost());
		} catch (Exception e) {
			logger.log(Level.WARNING, "Failed to setup resource lock", e);
		}
		return s;
	}

	/**
	 * Lookup up the globally configured Remote Jenkins Server based on display name
	 *
	 * @param displayName
	 *            Name of the configuration you are looking for
	 * @return A deep-copy of the RemoteJenkinsServer object configured globally
	 */
	public @Nullable @CheckForNull RemoteJenkinsServer findRemoteHost(String displayName) {
		if (isEmpty(displayName))
			return null;
		RemoteJenkinsServer server = null;
		for (RemoteJenkinsServer host : this.getDescriptor().remoteSites) {
			// if we find a match, then stop looping
			if (displayName.equals(host.getDisplayName())) {
				try {
					server = host.clone();
					break;
				} catch (CloneNotSupportedException e) {
					// Clone is supported by RemoteJenkinsServer
					throw new RuntimeException(e);
				}
			}
		}
		return server;
	}

	protected static String removeTrailingSlashes(String string) {
		if (isEmpty(string))
			return string;
		string = string.trim();
		while (string.endsWith("/"))
			string = string.substring(0, string.length() - 1);
		return string;
	}

	protected static String removeQueryParameters(String string) {
		if (isEmpty(string))
			return string;
		string = string.trim();
		int idx = string.indexOf("?");
		if (idx > 0)
			string = string.substring(0, idx);
		return string;
	}

	protected static String removeHashParameters(String string) {
		if (isEmpty(string))
			return string;
		string = string.trim();
		int idx = string.indexOf("#");
		if (idx > 0)
			string = string.substring(0, idx);
		return string;
	}

	private String getRootUrlFromJobUrl(String jobUrl) throws MalformedURLException {
		if (isEmpty(jobUrl))
			return null;
		if (FormValidationUtils.isURL(jobUrl)) {
			int index = jobUrl.indexOf("/job/");
			if (index < 0)
				throw new MalformedURLException("Expected '/job/' element in job URL but was: " + jobUrl);
			return jobUrl.substring(0, index);
		} else {
			return null;
		}
	}

	/**
	 * Convenience function to mark the build as failed. It's intended to only be
	 * called from this.perform().
	 *
	 * @param e
	 *            exception that caused the build to fail.
	 * @param logger
	 *            build listener.
	 * @throws IOException
	 *             if the build fails and <code>shouldNotFailBuild</code> is not
	 *             set.
	 */
	protected void failBuild(Exception e, PrintStream logger) throws IOException {
		StringBuilder msg = new StringBuilder();
		if (e instanceof InterruptedException) {
			Thread current = Thread.currentThread();
			msg.append(String.format("[Thread: %s/%s]: ", current.getId(), current.getName()));
		}
		msg.append(String.format("Remote build failed with '%s' for the following reason: '%s'.%s",
				e.getClass().getSimpleName(), e.getMessage(),
				this.getShouldNotFailBuild() ? " But the build will continue." : ""));
		if (enhancedLogging) {
			msg.append(NL).append(ExceptionUtils.getFullStackTrace(e));
		}
		if (logger != null)
			logger.println("ERROR: " + msg.toString());
		if (!this.getShouldNotFailBuild()) {
			throw new AbortException(e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}
	
	public void abortRemoteTask(RemoteJenkinsServer remoteServer, Handle handle, BuildContext context) 
			throws IOException, InterruptedException {
		if (isAbortTriggeredJob() && context != null && handle != null && !handle.isFinished()) {
			try {
				if (handle.isQueued()) {
					RestUtils.cancelQueueItem(remoteServer.getAddress(), handle, context, this);
				} else {
					RestUtils.stopRemoteJob(handle, context, this);
				}				
			} catch (IOException ex) {
				context.logger.println("Fail to abort remote job: " + ex.getMessage());
				logger.log(Level.WARNING, "Fail to abort remote job", ex);
			}
		}
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
			throws InterruptedException, IOException, IllegalArgumentException {
		FilePath workspace = build.getWorkspace();
		if (workspace == null)
			throw new IllegalArgumentException("The workspace can not be null");
		perform(build, workspace, launcher, listener);
		return true;
	}

	/**
	 * Triggers the remote job and, waits until completion if
	 * <code>blockBuildUntilComplete</code> is set.
	 *
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread.
	 * @throws IOException
	 *             if there is an error retrieving the remote build data, or, if
	 *             there is an error retrieving the remote build status, or, if
	 *             there is an error retrieving the console output of the remote
	 *             build, or, if the remote build does not succeed.
	 */
	@Override
	public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		Handle handle = null;
		BuildContext context = null;
		RemoteJenkinsServer effectiveRemoteServer = null;
		try {
			effectiveRemoteServer = evaluateEffectiveRemoteHost(
					new BasicBuildContext(build, workspace, listener));
			context = new BuildContext(build, workspace, listener, listener.getLogger(),
					effectiveRemoteServer);
			handle = performTriggerAndGetQueueId(context);
			performWaitForBuild(context, handle);
		} catch(InterruptedException e) {
			this.abortRemoteTask(effectiveRemoteServer, handle, context);
			throw e;
		}
	}

	/**
	 * Triggers the remote job, identifies the queue ID and, returns a
	 * <code>Handle</code> to this remote execution.
	 *
	 * @param context
	 *            the context of this Builder/BuildStep.
	 * @return Handle to further tracking of the remote build status.
	 * @throws IOException
	 *             if there is an error triggering the remote job.
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread.
	 * 
	 */
	public Handle performTriggerAndGetQueueId(BuildContext context) throws IOException, InterruptedException {
		List<String> cleanedParams = getCleanedParameters(getParameterList(context));
		String jobNameOrUrl = this.getJob();
		String securityToken = this.getToken();
		try {
			cleanedParams = TokenMacroUtils.applyTokenMacroReplacements(cleanedParams, context);
			jobNameOrUrl = TokenMacroUtils.applyTokenMacroReplacements(jobNameOrUrl, context);
			securityToken = TokenMacroUtils.applyTokenMacroReplacements(securityToken, context);
		} catch (IOException e) {
			this.failBuild(e, context.logger);
		}

		logConfiguration(context, cleanedParams);

		final JSONObject remoteJobMetadata = getRemoteJobMetadata(jobNameOrUrl, context);
		boolean isRemoteParameterized = isRemoteJobParameterized(remoteJobMetadata);

		final String triggerUrlString = HttpHelper.buildTriggerUrl(jobNameOrUrl, securityToken, null,
				isRemoteParameterized, context);

		// token shouldn't be exposed in the console
		final String jobUrlString = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
		context.logger.println(String.format("Triggering %s remote job '%s'",
				(isRemoteParameterized ? "parameterized" : "non-parameterized"), jobUrlString));

		logAuthInformation(context);

		RemoteBuildInfo buildInfo = new RemoteBuildInfo();

		context.logger.println("Triggering remote job now.");

		try {
			ConnectionResponse responseRemoteJob = HttpHelper.tryPost(triggerUrlString, context, cleanedParams,
					this.getPollInterval(), this.getConnectionRetryLimit(), this.getAuth2(), getLock(triggerUrlString),
					isUseCrumbCache());
			QueueItem queueItem = new QueueItem(responseRemoteJob.getHeader());
			buildInfo.setQueueId(queueItem.getId());
			buildInfo = updateBuildInfo(buildInfo, context);
		} catch (IOException | InterruptedException e) {
			this.failBuild(e, context.logger);
		}

		return new Handle(this, buildInfo, context.currentItem, context.effectiveRemoteServer, remoteJobMetadata);
	}

	/**
	 * Checks the remote build status and, waits for completion if
	 * <code>blockBuildUntilComplete</code> is set.
	 *
	 * @param context
	 *            the context of this Builder/BuildStep.
	 * @param handle
	 *            the handle to the remote execution.
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread.
	 * @throws IOException
	 *             if any HTTP error or business logic error
	 */
	public void performWaitForBuild(BuildContext context, Handle handle) throws IOException, InterruptedException {
		String jobName = handle.getJobName();

		RemoteBuildInfo buildInfo = handle.getBuildInfo();
		String queueId = buildInfo.getQueueId();
		if (queueId == null) {
			throw new AbortException(
					String.format("Unexpected status: %s. The queue id was not found.", buildInfo.toString()));
		}
		context.logger.println("  Remote job queue number: " + buildInfo.getQueueId());

		if (buildInfo.isQueued()) {
			context.logger.println("Waiting for remote build to be executed...");
		}

		while (buildInfo.isQueued()) {
			context.logger.println("Waiting for " + this.pollInterval + " seconds until next poll.");
			Thread.sleep(this.pollInterval * 1000);
			buildInfo = updateBuildInfo(buildInfo, context);
			handle.setBuildInfo(buildInfo);
		}

		URL jobURL = buildInfo.getBuildURL();
		int jobNumber = buildInfo.getBuildNumber();

		if (jobURL == null || jobNumber == 0) {
			throw new AbortException(String.format("Unexpected status: %s", buildInfo.toString()));
		}

		context.logger.println("Remote build started!");
		context.logger.println("  Remote build URL: " + jobURL);
		context.logger.println("  Remote build number: " + jobNumber);

		if (context.run != null)
			RemoteBuildInfoExporterAction.addBuildInfoExporterAction(context.run, jobName, jobNumber, jobURL,
					buildInfo);

		if (this.getBlockBuildUntilComplete()) {
			context.logger.println("Blocking local job until remote job completes.");

			buildInfo = updateBuildInfo(buildInfo, context);
			handle.setBuildInfo(buildInfo);

			if (buildInfo.isRunning()) {
				context.logger.println("Waiting for remote build to finish ...");
			}

			while (buildInfo.isRunning()) {
				context.logger.println("  Waiting for " + this.pollInterval + " seconds until next poll.");
				Thread.sleep(this.pollInterval * 1000);
				buildInfo = updateBuildInfo(buildInfo, context);
				handle.setBuildInfo(buildInfo);
			}

			context.logger.println("Remote build finished with status " + buildInfo.getResult().toString() + ".");
			if (context.run != null)
				RemoteBuildInfoExporterAction.addBuildInfoExporterAction(context.run, jobName, jobNumber, jobURL,
						buildInfo);

			if (this.getEnhancedLogging()) {
				String consoleOutput = getConsoleOutput(jobURL, context);

				context.logger.println();
				context.logger.println("Console output of remote job:");
				context.logger
						.println("--------------------------------------------------------------------------------");
				context.logger.println(consoleOutput);
				context.logger
						.println("--------------------------------------------------------------------------------");
			}

			// If build did not finish with 'success' or 'unstable' then fail build step.
			if (buildInfo.getResult() != Result.SUCCESS && buildInfo.getResult() != Result.UNSTABLE) {
				// failBuild will check if the 'shouldNotFailBuild' parameter is set or not, so
				// will decide how to
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
	 * @return {@link QueueItemData} the queue item data.
	 * @throws IOException
	 *             if there is an error identifying the remote host, or if there is
	 *             an error setting the authorization header, or if the request
	 *             fails due to an unknown host, unauthorized credentials, or
	 *             another reason, or if there is an invalid queue response.
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread.
	 */
	@Nonnull
	private QueueItemData getQueueItemData(@Nonnull String queueId, @Nonnull BuildContext context)
			throws IOException, InterruptedException {

		if (context.effectiveRemoteServer.getAddress() == null) {
			throw new AbortException(
					"The remote server address can not be empty, or it must be overridden on the job configuration.");
		}
		String queueQuery = String.format("%s/queue/item/%s/api/json/", context.effectiveRemoteServer.getAddress(),
				queueId);
		ConnectionResponse response = doGet(queueQuery, context);
		JSONObject queueResponse = response.getBody();

		if (queueResponse == null || queueResponse.isNullObject()) {
			throw new AbortException(String.format("Unexpected queue item response: code %s for request %s",
					response.getResponseCode(), queueQuery));
		}

		QueueItemData queueItem = new QueueItemData();
		queueItem.update(context, queueResponse);

		if (queueItem.isBlocked())
			context.logger.println(String.format("The remote job is blocked. %s.", queueItem.getWhy()));

		if (queueItem.isPending())
			context.logger.println(String.format("The remote job is pending. %s.", queueItem.getWhy()));

		if (queueItem.isBuildable())
			context.logger.println(String.format("The remote job is buildable. %s.", queueItem.getWhy()));

		if (queueItem.isCancelled())
			throw new AbortException("The remote job was canceled");

		return queueItem;
	}

	@Nonnull
	public RemoteBuildInfo updateBuildInfo(@Nonnull RemoteBuildInfo buildInfo, @Nonnull BuildContext context)
			throws IOException, InterruptedException {

		if (buildInfo.isNotTriggered())
			return buildInfo;

		if (buildInfo.isQueued()) {
			String queueId = buildInfo.getQueueId();
			if (queueId == null) {
				throw new AbortException(
						String.format("Unexpected status: %s. The queue id was not found.", buildInfo.toString()));
			}
			QueueItemData queueItem = getQueueItemData(queueId, context);
			if (queueItem.isExecuted()) {
				buildInfo.setBuildData(queueItem.getBuildNumber(), queueItem.getBuildURL());
			}
			return buildInfo;
		}

		// Only avoid url cache while loop inquiry
		String buildUrlString = String.format("%sapi/json/?seed=%d", buildInfo.getBuildURL(),
				System.currentTimeMillis());
		JSONObject responseObject = doGet(buildUrlString, context).getBody();

		try {
			if (responseObject == null
					|| responseObject.getString("result") == null && !responseObject.getBoolean("building")) {
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

	private String getConsoleOutput(URL url, BuildContext context) throws IOException, InterruptedException {
		URL buildUrl = new URL(url, "consoleText");
		return HttpHelper.tryGetRawResp(buildUrl.toString(), context, this.getPollInterval(),
				this.getConnectionRetryLimit(), this.getAuth2(), getLock(buildUrl.toString()));
	}

	/**
	 * Orchestrates all calls to the remote server. Also takes care of any
	 * credentials or failed-connection retries.
	 *
	 * @param urlString
	 *            the URL that needs to be called.
	 * @param context
	 *            the context of this Builder/BuildStep.
	 * @return JSONObject a valid JSON object, or null.
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread.
	 * @throws IOException
	 *             if any HTTP error occurred.
	 */
	public ConnectionResponse doGet(String urlString, BuildContext context) throws IOException, InterruptedException {
		return HttpHelper.tryGet(urlString, context, this.getPollInterval(), this.getConnectionRetryLimit(),
				this.getAuth2(), getLock(urlString));
	}

	private void logAuthInformation(BuildContext context) throws IOException {

		Auth2 serverAuth = context.effectiveRemoteServer.getAuth2();
		Auth2 localAuth = this.getAuth2();
		if (localAuth != null && !(localAuth instanceof NullAuth)) {
			String authString = (context.run == null) ? localAuth.getDescriptor().getDisplayName()
					: localAuth.toString((Item) context.run.getParent());
			context.logger.println(String.format("  Using job-level defined " + authString));
		} else if (serverAuth != null && !(serverAuth instanceof NullAuth)) {
			String authString = (context.run == null) ? serverAuth.getDescriptor().getDisplayName()
					: serverAuth.toString((Item) context.run.getParent());
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
		context.logger.println(
				"################################################################################################################");
		context.logger.println("  Parameterized Remote Trigger Configuration:");
		context.logger.println(String.format("    - job:                     %s %s", _job, _jobExpandedLogEntry));
		if (!isEmpty(_remoteJenkinsName)) {
			context.logger.println(String.format("    - remoteJenkinsName:       %s", _remoteJenkinsName));
		}
		if (!isEmpty(_remoteJenkinsUrl)) {
			context.logger.println(String.format("    - remoteJenkinsUrl:        %s", _remoteJenkinsUrl));
		}
		if (_auth != null && !(_auth instanceof NullAuth)) {
			String authString = context.run == null ? _auth.getDescriptor().getDisplayName()
					: _auth.toString((Item) context.run.getParent());
			context.logger.println(String.format("    - auth:                    %s", authString));
		}
		context.logger.println(String.format("    - parameters:              %s", _parameters));
		if (_loadParamsFromFile) {
			context.logger.println(String.format("    - loadParamsFromFile:      %s", _loadParamsFromFile));
			context.logger.println(String.format("    - parameterFile:           %s", _parameterFile));
		}
		context.logger.println(String.format("    - blockBuildUntilComplete: %s", _blockBuildUntilComplete));
		context.logger.println(String.format("    - connectionRetryLimit:    %s", _connectionRetryLimit));
		context.logger.println(
				"################################################################################################################");
	}
	
	public boolean isAbortTriggeredJob() {
		return abortTriggeredJob;
	}

	public int getMaxConn() {
		return maxConn;
	}

	/**
	 * @return the configured remote Jenkins name. That's the ID of a globally
	 *         configured remote host.
	 */
	public String getRemoteJenkinsName() {
		return remoteJenkinsName;
	}

	/**
	 * @return the configured remote Jenkins URL. This is not necessarily the
	 *         effective Jenkins URL, e.g. if a full URL is specified for
	 *         <code>job</code>!
	 */
	public String getRemoteJenkinsUrl() {
		return trimToNull(remoteJenkinsUrl);
	}

	/**
	 * @return true, if the authorization is overridden in the job configuration,
	 *         otherwise false.
	 * @deprecated since 2.3.0-SNAPSHOT - use {@link #getAuth2()} instead.
	 */
	public boolean getOverrideAuth() {
		if (auth2 == null)
			return false;
		if (auth2 instanceof NullAuth)
			return false;
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
	 * @return the configured <code>job</code> value. Can be a job name or full job
	 *         URL.
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

	private @Nonnull JSONObject getRemoteJobMetadata(String jobNameOrUrl, BuildContext context)
			throws IOException, InterruptedException {

		String remoteJobUrl = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
		remoteJobUrl += "/api/json?tree=actions[parameterDefinitions],property[parameterDefinitions],name,fullName,displayName,fullDisplayName,url";

		JSONObject jsonObject = DropCachePeriodicWork.safeGetJobInfo(remoteJobUrl, isUseJobInfoCache());
		if (jsonObject != null) {
			return jsonObject;
		}

		ConnectionResponse response = doGet(remoteJobUrl, context);
		if (response.getResponseCode() < 400 && response.getBody() != null) {
			return DropCachePeriodicWork.safePutJobInfo(remoteJobUrl, response.getBody(), isUseJobInfoCache());

		} else if (response.getResponseCode() == 401 || response.getResponseCode() == 403) {
			throw new AbortException(
					"Unauthorized to trigger " + remoteJobUrl + " - status code " + response.getResponseCode());
		} else if (response.getResponseCode() == 404) {
			throw new AbortException(
					"Remote job does not exist " + remoteJobUrl + " - status code " + response.getResponseCode());
		} else {
			throw new AbortException(
					"Unexpected response from " + remoteJobUrl + " - status code " + response.getResponseCode());
		}
	}

	/**
	 * Pokes the remote server to see if it has default parameters defined or not.
	 *
	 * @param remoteJobMetadata
	 *            from {@link #getRemoteJobMetadata(String, BuildContext)}.
	 * @return true if the remote job has parameters, otherwise false.
	 * @throws IOException
	 *             if it is not possible to identify if the job is parameterized.
	 */
	private boolean isRemoteJobParameterized(JSONObject remoteJobMetadata) throws IOException {
		boolean isParameterized = false;
		if (remoteJobMetadata != null) {
			if (remoteJobMetadata.getJSONArray("actions").size() >= 1) {
				for (Object obj : remoteJobMetadata.getJSONArray("actions")) {
					if (obj instanceof JSONObject && ((JSONObject) obj).get("parameterDefinitions") != null) {
						isParameterized = true;
					}
				}
			}

			if (!isParameterized && remoteJobMetadata.getJSONArray("property").size() >= 1) {
				for (Object obj : remoteJobMetadata.getJSONArray("property")) {
					if (obj instanceof JSONObject && ((JSONObject) obj).get("parameterDefinitions") != null) {
						isParameterized = true;
					}
				}
			}
		} else {
			throw new AbortException(
					"Could not identify if job is parameterized. Job metadata not accessible or with unexpected content.");
		}
		return isParameterized;
	}

	protected static String generateJobUrl(RemoteJenkinsServer remoteServer, String jobNameOrUrl)
			throws AbortException {
		if (isEmpty(jobNameOrUrl))
			throw new IllegalArgumentException("Invalid job name/url: " + jobNameOrUrl);
		String remoteJobUrl;
		String _jobNameOrUrl = jobNameOrUrl.trim();
		if (FormValidationUtils.isURL(_jobNameOrUrl)) {
			remoteJobUrl = _jobNameOrUrl;
		} else {
			remoteJobUrl = remoteServer.getAddress();
			if (remoteJobUrl == null) {
				throw new AbortException(
						"The remote server address can not be empty, or it must be overridden on the job configuration.");
			}
			while (remoteJobUrl.endsWith("/"))
				remoteJobUrl = remoteJobUrl.substring(0, remoteJobUrl.length() - 1);

			String[] split = _jobNameOrUrl.trim().split("/");
			for (String segment : split) {
				remoteJobUrl = String.format("%s/job/%s", remoteJobUrl, HttpHelper.encodeValue(segment));
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

	public boolean isUseCrumbCache() {
		return useCrumbCache;
	}

	@DataBoundSetter
	public void setUseCrumbCache(boolean useCrumbCache) {
		this.useCrumbCache = useCrumbCache;
	}

	public boolean isUseJobInfoCache() {
		return useJobInfoCache;
	}

	@DataBoundSetter
	public void setUseJobInfoCache(boolean useJobInfoCache) {
		this.useJobInfoCache = useJobInfoCache;
	}

	// This indicates to Jenkins that this is an implementation of an extension
	// point.
	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information, simply store it in a field and
		 * call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private CopyOnWriteList<RemoteJenkinsServer> remoteSites = new CopyOnWriteList<RemoteJenkinsServer>();

		/**
		 * In order to load the persisted global configuration, you have to call load()
		 * in the constructor.
		 */
		public DescriptorImpl() {
			this(true);
		}

		private DescriptorImpl(boolean load) {
			if (load)
				load();
		}

		public static DescriptorImpl newInstanceForTests() {
			return new DescriptorImpl(false);
		}

		/*
		 * /** Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value This parameter receives the value that the user has typed.
		 * 
		 * @return Indicates the outcome of the validation. This is sent to the browser.
		 */
		/*
		 * public FormValidation doCheckName(@QueryParameter String value) throws
		 * IOException, ServletException { if (value.length() == 0) return
		 * FormValidation.error("Please set a name"); if (value.length() < 4) return
		 * FormValidation.warning("Isn't the name too short?"); return
		 * FormValidation.ok(); }
		 */

		public boolean isApplicable(@SuppressWarnings("rawtypes") Class<? extends AbstractProject> aClass) {
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
		public FormValidation doCheckJob(@QueryParameter("job") final String value,
				@QueryParameter("remoteJenkinsUrl") final String remoteJenkinsUrl,
				@QueryParameter("remoteJenkinsName") final String remoteJenkinsName) {
			RemoteURLCombinationsResult result = FormValidationUtils.checkRemoteURLCombinations(remoteJenkinsUrl,
					remoteJenkinsName, value);
			if (result.isAffected(AffectedField.JOB_NAME_OR_URL))
				return result.formValidation;
			return FormValidation.ok();
		}

		@Restricted(NoExternalUse.class)
		public FormValidation doCheckRemoteJenkinsUrl(@QueryParameter("remoteJenkinsUrl") final String value,
				@QueryParameter("remoteJenkinsName") final String remoteJenkinsName,
				@QueryParameter("job") final String job) {
			RemoteURLCombinationsResult result = FormValidationUtils.checkRemoteURLCombinations(value,
					remoteJenkinsName, job);
			if (result.isAffected(AffectedField.REMOTE_JENKINS_URL))
				return result.formValidation;
			return FormValidation.ok();
		}

		@Restricted(NoExternalUse.class)
		public FormValidation doCheckRemoteJenkinsName(@QueryParameter("remoteJenkinsName") final String value,
				@QueryParameter("remoteJenkinsUrl") final String remoteJenkinsUrl,
				@QueryParameter("job") final String job) {
			RemoteURLCombinationsResult result = FormValidationUtils.checkRemoteURLCombinations(remoteJenkinsUrl, value,
					job);
			if (result.isAffected(AffectedField.REMOTE_JENKINS_NAME))
				return result.formValidation;
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
