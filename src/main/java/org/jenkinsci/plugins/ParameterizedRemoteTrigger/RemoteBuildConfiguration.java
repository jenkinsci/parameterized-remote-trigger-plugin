package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static java.lang.Math.min;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.StringTools.NL;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2.Auth2Descriptor;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NullAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.parameters2.JobParameters;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.parameters2.JobParameters.ParametersDescriptor;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.parameters2.MapParameters;
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
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.OtelUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.RestUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.TokenMacroUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;

/**
 * @author Maurice W.
 */
public class RemoteBuildConfiguration extends Builder implements SimpleBuildStep, Serializable {

	private static final long serialVersionUID = -4059001060991775146L;

	/**
	 * Default for this class is "no auth configured" since we do not want to
	 * override potential global config
	 */
	private final static Auth2 DEFAULT_AUTH = NullAuth.INSTANCE;
	private final static JobParameters DEFAULT_PARAMETERS = new MapParameters();

	private static final int DEFAULT_HTTP_GET_READ_TIMEOUT = 10000;
	private static final int DEFAULT_HTTP_POST_READ_TIMEOUT = 30000;

	/**
	 * The TTL value of all `queued` items is only 5 minutes. That is why we have to
	 * ignore user specified poll interval for such items.
	 */
	private static final int QUEUED_ITEMS_POLLINTERVALL = 30;

	private static final int DEFAULT_POLLINTERVALL = 10;
	private static final int connectionRetryLimit = 5;

	/**
	 * We need to keep this for compatibility - old config deserialization!
	 *
	 * @deprecated since 2.3.0-SNAPSHOT - use {@link Auth2} instead.
	 */
	@SuppressWarnings("DeprecatedIsStillUsed")
	private transient List<Auth> auth;

	private String remoteJenkinsName;
	private String remoteJenkinsUrl;
	private Auth2 auth2;

	private JobParameters parameters2;
	private boolean shouldNotFailBuild;
	private boolean trustAllCertificates;
	private boolean overrideTrustAllCertificates;
	private boolean preventRemoteBuildQueue;
	private int httpPostReadTimeout;
	private int httpGetReadTimeout;
	private int pollInterval;
	private boolean blockBuildUntilComplete;
	private String job;
	private String token;
	/**
	 * We need to keep this for compatibility - old config deserialization!
	 *
	 * @deprecated since 3.1.6-SNAPSHOT - use {@link JobParameters} instead.
	 */
	@SuppressWarnings("DeprecatedIsStillUsed")
	private String parameters;
	private boolean enhancedLogging;
	/**
	 * We need to keep this for compatibility - old config deserialization!
	 *
	 * @deprecated since 3.1.6-SNAPSHOT - use {@link JobParameters} instead.
	 */
	@SuppressWarnings("DeprecatedIsStillUsed")
	private String parameterFile;
	private int maxConn = 1;
	private boolean useCrumbCache;
	private boolean useJobInfoCache;
	private boolean abortTriggeredJob;
	private boolean disabled;

	private transient Map<String, Semaphore> hostLocks = new HashMap<>();
	private Map<String, Integer> hostPermits = new HashMap<>();

	private static Logger logger = Logger.getLogger(RemoteBuildConfiguration.class.getName());

	@DataBoundConstructor
	public RemoteBuildConfiguration() {
		httpGetReadTimeout = DEFAULT_HTTP_GET_READ_TIMEOUT;
		httpPostReadTimeout = DEFAULT_HTTP_POST_READ_TIMEOUT;
		pollInterval = DEFAULT_POLLINTERVALL;
		CookieManager cookieManager = new CookieManager();
		CookieHandler.setDefault(cookieManager);
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

		// migrate parameters/parameterFile to JobParameters
		if (parameters2 == null) {
			parameters2 = JobParameters.migrateOldParameters(parameters, parameterFile);
		}
		parameters = null;
		parameterFile = null;

		if (hostLocks == null) {
			hostLocks = new HashMap<>();
		}
		if (hostPermits == null) {
			hostPermits = new HashMap<>();
		}
		return this;
	}

	@DataBoundSetter
	public void setTrustAllCertificates(boolean trustAllCertificates) {
		this.trustAllCertificates = trustAllCertificates;
	}

	@DataBoundSetter
	public void setOverrideTrustAllCertificates(boolean overrideTrustAllCertificates) {
		this.overrideTrustAllCertificates = overrideTrustAllCertificates;
	}

	@DataBoundSetter
	public void setAbortTriggeredJob(boolean abortTriggeredJob) {
		this.abortTriggeredJob = abortTriggeredJob;
	}

	@DataBoundSetter
	public void setMaxConn(int maxConn) {
		this.maxConn = (maxConn > 5) ? 5 : Math.max(maxConn, 1);
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
	public void setParameters2(JobParameters parameters2) {
		this.parameters2 = parameters2;
		parameters = null; // Disable old parameters
		parameterFile = null; // Disable old parameters
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
	public void setHttpGetReadTimeout(int readTimeout) {
		if (readTimeout < 1000)
			this.httpGetReadTimeout = DEFAULT_HTTP_GET_READ_TIMEOUT;
		else
			this.httpGetReadTimeout = readTimeout;
	}

	@DataBoundSetter
	public void setHttpPostReadTimeout(int readTimeout) {
		if (readTimeout < 1000)
			this.httpPostReadTimeout = DEFAULT_HTTP_POST_READ_TIMEOUT;
		else
			this.httpPostReadTimeout = readTimeout;
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
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}

	@DataBoundSetter
	public void setUseJobInfoCache(boolean useJobInfoCache) {
		this.useJobInfoCache = useJobInfoCache;
	}

	@DataBoundSetter
	public void setUseCrumbCache(boolean useCrumbCache) {
		this.useCrumbCache = useCrumbCache;
	}

	public Map<String, String> getParameterMap(BuildContext context) throws AbortException {
		return getParameters2()
				.getParametersMap(context);
	}

	/**
	 * Tries to identify the effective Remote Host configuration based on the
	 * different parameters like <code>remoteJenkinsName</code> and the globally
	 * configured remote host, <code>remoteJenkinsURL</code> which overrides the
	 * address locally or <code>job</code> which can be a full job URL.
	 *
	 * @param context the context of this Builder/BuildStep.
	 * @return {@link RemoteJenkinsServer} a RemoteJenkinsServer object, never null.
	 * @throws AbortException        if no server found and remoteJenkinsUrl empty.
	 * @throws MalformedURLException if <code>remoteJenkinsName</code> no valid URL
	 *                               or <code>job</code> an URL but nor valid.
	 */
	@NonNull
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

		if (this.overrideTrustAllCertificates) {
			server.setTrustAllCertificates(this.trustAllCertificates);
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
	 * @param displayName Name of the configuration you are looking for
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
			int index;
			if (jobUrl.contains("/view/")) {
				index = min(jobUrl.indexOf("/view/"), jobUrl.indexOf("/job/"));
			} else {
				index = jobUrl.indexOf("/job/");
			}
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
	 * @param e      exception that caused the build to fail.
	 * @param logger build listener.
	 * @throws IOException if the build fails and <code>shouldNotFailBuild</code> is
	 *                     not set.
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
		if (!isStepDisabled(listener.getLogger())) {
			perform(build, workspace, launcher, listener);
		}
		return true;
	}

	public boolean isStepDisabled(PrintStream printStream) {
		if (isDisabled()) {
			printStream.println("remote trigger step was disabled. skipping...");
			return true;
		}
		return false;
	}

	/**
	 * Triggers the remote job and, waits until completion if
	 * <code>blockBuildUntilComplete</code> is set.
	 *
	 * @throws InterruptedException if any thread has interrupted the current
	 *                              thread.
	 * @throws IOException          if there is an error retrieving the remote build
	 *                              data, or, if there is an error retrieving the
	 *                              remote build status, or, if there is an error
	 *                              retrieving the console output of the remote
	 *                              build, or, if the remote build does not succeed.
	 */
	@Override
	public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
			throws InterruptedException, IOException {
		Handle handle = null;
		BuildContext context = null;
		RemoteJenkinsServer effectiveRemoteServer = null;
		try (AutoCloseable ignored = OtelUtils.isOpenTelemetryAvailable() ? OtelUtils.activeSpanIfAvailable(build) : OtelUtils.noop()) {
			effectiveRemoteServer = evaluateEffectiveRemoteHost(new BasicBuildContext(build, workspace, listener));
			context = new BuildContext(build, workspace, listener, listener.getLogger(), effectiveRemoteServer);
			handle = performTriggerAndGetQueueId(context);
			performWaitForBuild(context, handle);
		} catch (InterruptedException e) {
			this.abortRemoteTask(effectiveRemoteServer, handle, context);
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Triggers the remote job, identifies the queue ID and, returns a
	 * <code>Handle</code> to this remote execution.
	 *
	 * @param context the context of this Builder/BuildStep.
	 * @return Handle to further tracking of the remote build status.
	 * @throws IOException          if there is an error triggering the remote job.
	 * @throws InterruptedException if any thread has interrupted the current
	 *                              thread.
	 *
	 */
	public Handle performTriggerAndGetQueueId(@NonNull BuildContext context) throws IOException, InterruptedException {
		Map<String, String> parameters = getParameterMap(context);
		String jobNameOrUrl = this.getJob();
		String securityToken = this.getToken();
		try {
			parameters = TokenMacroUtils.applyTokenMacroReplacements(parameters, context);
			jobNameOrUrl = TokenMacroUtils.applyTokenMacroReplacements(jobNameOrUrl, context);
			securityToken = TokenMacroUtils.applyTokenMacroReplacements(securityToken, context);
		} catch (IOException e) {
			this.failBuild(e, context.logger);
		}

		logConfiguration(context, parameters);

		final JSONObject remoteJobMetadata = getRemoteJobMetadata(jobNameOrUrl, context);
		boolean isRemoteParameterized = isRemoteJobParameterized(remoteJobMetadata);

		String triggerUrlString = HttpHelper.buildTriggerUrl(jobNameOrUrl, securityToken,
				isRemoteParameterized, context);

		// token shouldn't be exposed in the console
		final String jobUrlString = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
		context.logger.println(String.format("Triggering %s remote job '%s'",
				(isRemoteParameterized ? "parameterized" : "non-parameterized"), jobUrlString));

		logAuthInformation(context);

		RemoteBuildInfo buildInfo = new RemoteBuildInfo();

		context.logger.println("Triggering remote job now.");

		try {
			ConnectionResponse responseRemoteJob = HttpHelper.tryPost(triggerUrlString, context, parameters,
					this.getHttpPostReadTimeout(), this.getPollInterval(buildInfo.getStatus()),
					this.getConnectionRetryLimit(), this.getAuth2(), getLock(triggerUrlString), isUseCrumbCache());
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
	 * @param context the context of this Builder/BuildStep.
	 * @param handle  the handle to the remote execution.
	 * @throws InterruptedException if any thread has interrupted the current
	 *                              thread.
	 * @throws IOException          if any HTTP error or business logic error
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
			context.logger.println(
					"Waiting for " + this.getPollInterval(buildInfo.getStatus()) + " seconds until next poll.");
			Thread.sleep(this.getPollInterval(buildInfo.getStatus()) * 1000);
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

			String consoleOffset = "0";
			if (this.getEnhancedLogging()) {
				context.logger
						.println("--------------------------------------------------------------------------------");
				context.logger.println();
				context.logger.println("Console output of remote job:");
				consoleOffset = printOffsetConsoleOutput(context, consoleOffset, buildInfo);
			}
			while (buildInfo.isRunning()) {
				if (this.getEnhancedLogging()) {
					consoleOffset = printOffsetConsoleOutput(context, consoleOffset, buildInfo);
				} else {
					context.logger.println("  Waiting for " + this.getPollInterval(buildInfo.getStatus())
							+ " seconds until next poll.");
				}
				Thread.sleep(this.getPollInterval(buildInfo.getStatus()) * 1000);
				buildInfo = updateBuildInfo(buildInfo, context);
				handle.setBuildInfo(buildInfo);
			}
			if (this.getEnhancedLogging()) {
				consoleOffset = printOffsetConsoleOutput(context, consoleOffset, buildInfo);
				context.logger
						.println("--------------------------------------------------------------------------------");
			}

			context.logger.println("Remote build finished with status " + buildInfo.getResult().toString() + ".");
			if (context.run != null)
				RemoteBuildInfoExporterAction.addBuildInfoExporterAction(context.run, jobName, jobNumber, jobURL,
						buildInfo);

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
	 * @param queueId the id of the remote job on the queue.
	 * @param context the context of this Builder/BuildStep.
	 * @return {@link QueueItemData} the queue item data.
	 * @throws IOException          if there is an error identifying the remote
	 *                              host, or if there is an error setting the
	 *                              authorization header, or if the request fails
	 *                              due to an unknown host, unauthorized
	 *                              credentials, or another reason, or if there is
	 *                              an invalid queue response.
	 * @throws InterruptedException if any thread has interrupted the current
	 *                              thread.
	 */
	@NonNull
	private QueueItemData getQueueItemData(@NonNull String queueId, @NonNull BuildContext context)
			throws IOException, InterruptedException {

		if (context.effectiveRemoteServer.getAddress() == null) {
			throw new AbortException(
					"The remote server address can not be empty, or it must be overridden on the job configuration.");
		}
		String queueQuery = String.format("%s/queue/item/%s/api/json/", context.effectiveRemoteServer.getAddress(),
				queueId);
		ConnectionResponse response = doGet(queueQuery, context, RemoteBuildStatus.QUEUED);
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

	@NonNull
	public RemoteBuildInfo updateBuildInfo(@NonNull RemoteBuildInfo buildInfo, @NonNull BuildContext context)
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
				URL remoteBuildURL = queueItem.getBuildURL();
				String effectiveRemoteServerAddress = context.effectiveRemoteServer.getAddress();
				URL effectiveRemoteBuildURL = generateEffectiveRemoteBuildURL(remoteBuildURL,
						effectiveRemoteServerAddress);
				buildInfo.setBuildData(queueItem.getBuildNumber(), effectiveRemoteBuildURL);
			}
			return buildInfo;
		}

		// Only avoid url cache while loop inquiry
		String buildUrlString = String.format("%sapi/json/?tree=result,building&seed=%d", buildInfo.getBuildURL(),
				System.currentTimeMillis());
		JSONObject responseObject = doGet(buildUrlString, context, buildInfo.getStatus()).getBody();

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
		} catch (Exception ignored) {
		}
		return buildInfo;
	}

	protected static URL generateEffectiveRemoteBuildURL(URL remoteBuildURL, String effectiveRemoteServerAddress)
			throws AbortException {
		if (effectiveRemoteServerAddress == null || remoteBuildURL == null) {
			return remoteBuildURL;
		}

		try {
			URI effectiveUri = new URI(effectiveRemoteServerAddress);
			return new URL(effectiveUri.getScheme(), effectiveUri.getHost(), effectiveUri.getPort(),
					remoteBuildURL.getPath());
		} catch (URISyntaxException | MalformedURLException ex) {
			throw new AbortException(String.format("Unexpected syntax error: %s.", ex.toString()));
		}
	}

	private String printOffsetConsoleOutput(BuildContext context, String offset, RemoteBuildInfo buildInfo)
			throws IOException, InterruptedException {
		if (offset == null || offset.equals("-1")) {
			return "-1";
		}
		String buildUrlString = String.format("%slogText/progressiveText?start=%s", buildInfo.getBuildURL(), offset);
		ConnectionResponse response = doGet(buildUrlString, context, buildInfo.getStatus());

		String rawBody = response.getRawBody();
		if (rawBody != null && !rawBody.equals("")) {
			context.logger.println(rawBody);
		}

		Map<String, List<String>> header = response.getHeader();
		if (header.containsKey("X-More-Data") && header.containsKey("X-Text-Size")) {
			return header.get("X-Text-Size").get(0);
		} else {
			return "-1";
		}
	}

	/**
	 * Orchestrates all calls to the remote server. Also takes care of any
	 * credentials or failed-connection retries.
	 *
	 * @param urlString         the URL that needs to be called.
	 * @param context           the context of this Builder/BuildStep.
	 * @param remoteBuildStatus the build status of a remote build.
	 * @return JSONObject a valid JSON object, or null.
	 * @throws InterruptedException if any thread has interrupted the current
	 *                              thread.
	 * @throws IOException          if any HTTP error occurred.
	 */
	public ConnectionResponse doGet(String urlString, BuildContext context, RemoteBuildStatus remoteBuildStatus)
			throws IOException, InterruptedException {
		return HttpHelper.tryGet(urlString, context, this.getHttpGetReadTimeout(),
				this.getPollInterval(remoteBuildStatus), this.getConnectionRetryLimit(), this.getAuth2(),
				getLock(urlString));
	}

	private void logAuthInformation(@NonNull BuildContext context) {

		Auth2 serverAuth = context.effectiveRemoteServer.getAuth2();
		Auth2 localAuth = this.getAuth2();
		if (localAuth != null && !(localAuth instanceof NullAuth)) {
			String authString = (context.run == null) ? localAuth.getDescriptor().getDisplayName()
					: localAuth.toString(context.run.getParent());
			context.logger.printf("  Using job-level defined " + authString + "%n");
		} else if (serverAuth != null && !(serverAuth instanceof NullAuth)) {
			String authString = (context.run == null) ? serverAuth.getDescriptor().getDisplayName()
					: serverAuth.toString(context.run.getParent());
			context.logger.printf("  Using globally defined " + authString + "%n");
		} else {
			context.logger.println("  No credentials configured");
		}
	}

	private void logConfiguration(@NonNull BuildContext context, Map<String, String> effectiveParams) throws IOException {
		String _job = getJob();
		String _jobExpanded = getJobExpanded(context);
		String _jobExpandedLogEntry = (_job.equals(_jobExpanded)) ? "" : "(" + _jobExpanded + ")";
		String _remoteJenkinsName = getRemoteJenkinsName();
		String _remoteJenkinsUrl = getRemoteJenkinsUrl();
		boolean _trustAllCertificates = context.effectiveRemoteServer.getTrustAllCertificates();

		Auth2 _auth = getAuth2();
		int _connectionRetryLimit = getConnectionRetryLimit();
		boolean _blockBuildUntilComplete = getBlockBuildUntilComplete();
		context.logger.println(
				"################################################################################################################");
		context.logger.println("  Parameterized Remote Trigger Configuration:");
		context.logger.printf("    - job:                     %s %s%n", _job, _jobExpandedLogEntry);
		if (!isEmpty(_remoteJenkinsName)) {
			context.logger.printf("    - remoteJenkinsName:       %s%n", _remoteJenkinsName);
		}
		if (!isEmpty(_remoteJenkinsUrl)) {
			context.logger.printf("    - remoteJenkinsUrl:        %s%n", _remoteJenkinsUrl);
		}
		if (_auth != null && !(_auth instanceof NullAuth)) {
			final String authString = context.run == null
					? _auth.getDescriptor().getDisplayName()
					: _auth.toString(context.run.getParent());
			context.logger.printf("    - auth:                    %s%n", authString);
		}
		context.logger.printf("    - parameters:              %s%n", effectiveParams);
		context.logger.printf("    - blockBuildUntilComplete: %s%n", _blockBuildUntilComplete);
		context.logger.printf("    - connectionRetryLimit:    %s%n", _connectionRetryLimit);
		context.logger.printf("    - trustAllCertificates:    %s%n", _trustAllCertificates);
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

	public int getHttpGetReadTimeout() {
		return httpGetReadTimeout;
	}

	public int getHttpPostReadTimeout() {
		return httpPostReadTimeout;
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

	public int getPollInterval(RemoteBuildStatus remoteBuildStatus) {
		switch (remoteBuildStatus) {
		case NOT_TRIGGERED:
		case QUEUED:
			return QUEUED_ITEMS_POLLINTERVALL;
		default:
			return pollInterval;
		}
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
	 * @throws IOException if there is an error replacing tokens.
	 */
	private String getJobExpanded(BasicBuildContext context) throws IOException {
		return TokenMacroUtils.applyTokenMacroReplacements(getJob(), context);
	}

	public String getToken() {
		return trimToEmpty(token);
	}

	public boolean getEnhancedLogging() {
		return enhancedLogging;
	}

	public JobParameters getParameters2() {
		return (parameters2 != null) ? parameters2 : DEFAULT_PARAMETERS;
	}

	public int getConnectionRetryLimit() {
		return connectionRetryLimit; // For now, this is a constant
	}

	public boolean isDisabled() {
		return disabled;
	}

	private @NonNull JSONObject getRemoteJobMetadata(String jobNameOrUrl, @NonNull BuildContext context)
			throws IOException, InterruptedException {

		String remoteJobUrl = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
		Map<String, String> parameters = singletonMap("tree", "actions[parameterDefinitions],property[parameterDefinitions],name,fullName,displayName,fullDisplayName,url");
		remoteJobUrl += "/api/json?" + HttpHelper.buildUrlQueryString(parameters);

		JSONObject jsonObject = DropCachePeriodicWork.safeGetJobInfo(remoteJobUrl, isUseJobInfoCache());
		if (jsonObject != null) {
			return jsonObject;
		}

		ConnectionResponse response = doGet(remoteJobUrl, context, RemoteBuildStatus.FINISHED);
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
	 * @param remoteJobMetadata from
	 *                          {@link #getRemoteJobMetadata(String, BuildContext)}.
	 * @return true if the remote job has parameters, otherwise false.
	 * @throws IOException if it is not possible to identify if the job is
	 *                     parameterized.
	 */
	private boolean isRemoteJobParameterized(final JSONObject remoteJobMetadata) throws IOException {
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

	public boolean isUseJobInfoCache() {
		return useJobInfoCache;
	}

	public boolean getTrustAllCertificates() {
		return trustAllCertificates;
	}

	public boolean getOverrideTrustAllCertificates() {
		return overrideTrustAllCertificates;
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
		private CopyOnWriteList<RemoteJenkinsServer> remoteSites = new CopyOnWriteList<>();

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

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/**
		 * This human-readable name is used in the configuration screen.
		 */
		@NonNull
		@Override
		public String getDisplayName() {
			return "Trigger a remote parameterized job";
		}

		@Override
		public boolean configure(StaplerRequest2 req, JSONObject formData) throws FormException {

			remoteSites.replaceBy(req.bindJSONToList(RemoteJenkinsServer.class, formData.get("remoteSites")));
			save();

			return super.configure(req, formData);
		}

		@Restricted(NoExternalUse.class)
		public FormValidation doCheckTrustAllCertificates(@QueryParameter("trustAllCertificates") final boolean value) {
			if (value) {
				return FormValidation.warning("Accepting all certificates is potentially unsafe.");
			}
			return FormValidation.ok();
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
		@NonNull
		public ListBoxModel doFillRemoteJenkinsNameItems() {
			ListBoxModel model = new ListBoxModel();

			model.add("");
			Arrays.stream(getRemoteSites())
					.filter(Objects::nonNull)
					.map(RemoteJenkinsServer::getDisplayName)
					.forEach(model::add);
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

		public static List<ParametersDescriptor> getParametersDescriptors() {
			return JobParameters.all();
		}

		public static Auth2Descriptor getDefaultAuth2Descriptor() {
			return NullAuth.DESCRIPTOR;
		}

		public static ParametersDescriptor getDefaultParametersDescriptor() {
			return MapParameters.DESCRIPTOR;
		}

	}

}
