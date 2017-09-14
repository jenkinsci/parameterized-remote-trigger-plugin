package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import hudson.AbortException;
import hudson.FilePath;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Extension;
import hudson.util.CopyOnWriteList;
import hudson.util.ListBoxModel;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import net.sf.json.JSONSerializer;
//import net.sf.json.
//import net.sf.json.

import net.sf.json.util.JSONUtils;

import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

/**
 * 
 * @author Maurice W.
 * 
 */
public class RemoteBuildConfiguration extends Builder {

    private final String          token;
    private final String          remoteJenkinsName;
    private final String          job;

    private final boolean         shouldNotFailBuild;
    private final int             pollInterval;
    private final int             connectionRetryLimit = 5;
    private final boolean         preventRemoteBuildQueue;
    private final boolean         blockBuildUntilComplete;
    private final boolean         enhancedLogging;

    // "parameters" is the raw string entered by the user
    private final String          parameters;
    // "parameterList" is the cleaned-up version of "parameters" (stripped out comments, character encoding, etc)

    private final List<String>    parameterList;

    private static String         paramerizedBuildUrl = "/buildWithParameters";
    private static String         normalBuildUrl      = "/build";
    //private static String         normalBuildUrl      = "/buildWithParameters";
    private static String         buildTokenRootUrl   = "/buildByToken";

    private final boolean         overrideAuth;
    private CopyOnWriteList<Auth> auth                = new CopyOnWriteList<Auth>();

    private final boolean         loadParamsFromFile;
    private String                parameterFile       = "";

    private String                queryString         = "";

    @DataBoundConstructor
    public RemoteBuildConfiguration(String remoteJenkinsName, boolean shouldNotFailBuild, String job, String token,
            String parameters, boolean enhancedLogging, JSONObject overrideAuth, JSONObject loadParamsFromFile, boolean preventRemoteBuildQueue,
            boolean blockBuildUntilComplete, int pollInterval) throws MalformedURLException {

        this.token = token.trim();
        this.remoteJenkinsName = remoteJenkinsName;
        this.job = job.trim();
        this.shouldNotFailBuild = shouldNotFailBuild;
        this.preventRemoteBuildQueue = preventRemoteBuildQueue;
        this.blockBuildUntilComplete = blockBuildUntilComplete;
        this.pollInterval = pollInterval;
        this.enhancedLogging = enhancedLogging;

        if (overrideAuth != null && overrideAuth.has("auth")) {
            this.overrideAuth = true;
            this.auth.replaceBy(new Auth(overrideAuth.getJSONObject("auth")));
        } else {
            this.overrideAuth = false;
            this.auth.replaceBy(new Auth(new JSONObject()));
        }

        if (loadParamsFromFile != null && loadParamsFromFile.has("parameterFile")) {
            this.loadParamsFromFile = true;
            this.parameterFile = loadParamsFromFile.getString("parameterFile");
            this.parameters = "";
            //manually add a leading-slash if we don't have one
            if( this.parameterFile.charAt(0) != '/' ){
                this.parameterFile = "/" + this.parameterFile;
            }
        } else {
            this.loadParamsFromFile = false;
            this.parameters = parameters;
        }

        // TODO: clean this up a bit
        // split the parameter-string into an array based on the new-line character
        String[] params = parameters.split("\n");

        // convert the String array into a List of Strings, and remove any empty entries
        this.parameterList = new ArrayList<String>(Arrays.asList(params));

    }

    public RemoteBuildConfiguration(String remoteJenkinsName, boolean shouldNotFailBuild,
            boolean preventRemoteBuildQueue, boolean blockBuildUntilComplete, int pollInterval, String job,
            String token, String parameters, boolean enhancedLogging) throws MalformedURLException {

        this.token = token.trim();
        this.remoteJenkinsName = remoteJenkinsName;
        this.parameters = parameters;
        this.enhancedLogging = enhancedLogging;
        this.job = job.trim();
        this.shouldNotFailBuild = shouldNotFailBuild;
        this.preventRemoteBuildQueue = preventRemoteBuildQueue;
        this.blockBuildUntilComplete = blockBuildUntilComplete;
        this.pollInterval = pollInterval;
        this.overrideAuth = false;
        this.auth.replaceBy(new Auth(null));

        this.loadParamsFromFile = false;

        // split the parameter-string into an array based on the new-line character
        String[] params = parameters.split("\n");

        // convert the String array into a List of Strings, and remove any empty entries
        this.parameterList = new ArrayList<String>(Arrays.asList(params));

    }

    /**
     * Reads a file from the jobs workspace, and loads the list of parameters from with in it. It will also call
     * ```getCleanedParameters``` before returning.
     * 
     * @param build
     * @return List<String> of build parameters
     */
    private List<String> loadExternalParameterFile(AbstractBuild<?, ?> build) {

        FilePath workspace = build.getWorkspace();
        BufferedReader br = null;
        List<String> ParameterList = new ArrayList<String>();
        try {

            String filePath = workspace + this.getParameterFile();
            String sCurrentLine;
            String fileContent = "";

            br = new BufferedReader(new FileReader(filePath));

            while ((sCurrentLine = br.readLine()) != null) {
                // fileContent += sCurrentLine;
                ParameterList.add(sCurrentLine);
            }

            // ParameterList = new ArrayList<String>(Arrays.asList(fileContent));

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        // FilePath.
        return getCleanedParameters(ParameterList);
    }

    /**
     * Strip out any empty strings from the parameterList
     */
    private void removeEmptyElements(Collection<String> collection) {
        collection.removeAll(Arrays.asList(null, ""));
        collection.removeAll(Arrays.asList(null, " "));
    }

    /**
     * Convenience method
     * 
     * @return List<String> of build parameters
     */
    private List<String> getCleanedParameters() {

        return getCleanedParameters(this.getParameterList());
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
     * Similar to "replaceToken", but acts on a list in place of just a single string
     * 
     * @param build
     * @param listener
     * @param params
     *            List<String> of params to be tokenized/replaced
     * @return List<String> of resolved variables/tokens
     */
    private List<String> replaceTokens(AbstractBuild<?, ?> build, BuildListener listener, List<String> params) {
        List<String> tokenizedParams = new ArrayList<String>();

        for (int i = 0; i < params.size(); i++) {
            tokenizedParams.add(replaceToken(build, listener, params.get(i)));
            // params.set(i, replaceToken(build, listener, params.get(i)));
        }

        return tokenizedParams;
    }

    /**
     * Resolves any environment variables in the string
     * 
     * @param build
     * @param listener
     * @param input
     *            String to be tokenized/replaced
     * @return String with resolved Environment variables
     */
    private String replaceToken(AbstractBuild<?, ?> build, BuildListener listener, String input) {
        try {
            return TokenMacro.expandAll(build, listener, input);
        } catch (Exception e) {
            listener.getLogger().println(
                    String.format("Failed to resolve parameters in string %s due to following error:\n%s", input,
                            e.getMessage()));
        }
        return input;
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
     * Return the Collection<String> in an encoded query-string
     * 
     * @return query-parameter-formated URL-encoded string
     * @throws InterruptedException
     * @throws IOException
     * @throws MacroEvaluationException
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
     * Lookup up a Remote Jenkins Server based on display name
     * 
     * @param displayName
     *            Name of the configuration you are looking for
     * @return A RemoteSitez object
     */
    public RemoteJenkinsServer findRemoteHost(String displayName) {
        RemoteJenkinsServer match = null;

        for (RemoteJenkinsServer host : this.getDescriptor().remoteSites) {
            // if we find a match, then stop looping
            if (displayName.equals(host.getDisplayName())) {
                match = host;
                break;
            }
        }

        return match;
    }

    /**
     * Helper function to allow values to be added to the query string from any method.
     * 
     * @param item
     */
    private void addToQueryString(String item) {
        String currentQueryString = this.getQueryString();
        String newQueryString = "";

        if (currentQueryString == null || currentQueryString.equals("")) {
            newQueryString = item;
        } else {
            newQueryString = currentQueryString + "&" + item;
        }
        this.setQueryString(newQueryString);
    }

    /**
     * Build the proper URL to trigger the remote build
     * 
     * All passed in string have already had their tokens replaced with real values. All 'params' also have the proper
     * character encoding
     * 
     * @param job
     *            Name of the remote job
     * @param securityToken
     *            Security token used to trigger remote job
     * @param params
     *            Parameters for the remote job
     * @return fully formed, fully qualified remote trigger URL
     */
    private String buildTriggerUrl(String job, String securityToken, Collection<String> params, boolean isRemoteJobParameterized) {
        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());
        String triggerUrlString = remoteServer.getAddress().toString();

        // start building the proper URL based on known capabiltiies of the remote server
        if (remoteServer.getHasBuildTokenRootSupport()) {
            triggerUrlString += buildTokenRootUrl;
            triggerUrlString += getBuildTypeUrl(isRemoteJobParameterized);

            this.addToQueryString("job=" + this.encodeValue(job));

        } else {
            triggerUrlString += "/job/";
            triggerUrlString += this.encodeValue(job);
            triggerUrlString += getBuildTypeUrl(isRemoteJobParameterized);
        }

        // don't try to include a security token in the URL if none is provided
        if (!securityToken.equals("")) {
            this.addToQueryString("token=" + encodeValue(securityToken));
        }

        // turn our Collection into a query string
        String buildParams = buildUrlQueryString(params);

        if (!buildParams.isEmpty()) {
            this.addToQueryString(buildParams);
        }

        // by adding "delay=0", this will (theoretically) force this job to the top of the remote queue
        this.addToQueryString("delay=0");

        triggerUrlString += "?" + this.getQueryString();

        return triggerUrlString;
    }

    /**
     * Build the proper URL for GET calls
     * 
     * All passed in string have already had their tokens replaced with real values.
     * 
     * @param job
     *            Name of the remote job
     * @param securityToken
     *            Security token used to trigger remote job
     * @return fully formed, fully qualified remote trigger URL
     */
    private String buildGetUrl(String job, String securityToken) {

        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());
        String urlString = remoteServer.getAddress().toString();

        urlString += "/job/";
        urlString += this.encodeValue(job);

        // don't try to include a security token in the URL if none is provided
        if (!securityToken.equals("")) {
            this.addToQueryString("token=" + encodeValue(securityToken));
        }
        return urlString;
    }

    /**
     * Build the proper URL for GET calls for queue
     *
     * All passed in string have already had their tokens replaced with real values.
     *
     * @return fully formed, fully qualified remote trigger URL
     */
    private String buildGetUrlForQueue() {

        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());
        String urlString = remoteServer.getAddress().toString();

        urlString += "/queue/api/json";

        return urlString;
    }

    /**
     * Convenience function to mark the build as failed. It's intended to only be called from this.perform();
     * 
     * @param e
     *            Exception that caused the build to fail
     * @param listener
     *            Build Listener
     * @throws IOException
     */
    private void failBuild(Exception e, BuildListener listener) throws IOException {
        System.out.print(e.getStackTrace());
        if (this.getShouldNotFailBuild()) {
            listener.error("Remote build failed for the following reason, but the build will continue:");
            listener.error(e.getMessage());
        } else {
            listener.error("Remote build failed for the following reason:");
            throw new AbortException(e.getMessage());
        }
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException,
            IOException, IllegalArgumentException {

        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());

        // Stores the status of the remote build
        String buildStatusStr = "UNKNOWN";

        if (remoteServer == null) {
            this.failBuild(new Exception("No remote host is defined for this job."), listener);
            return true;
        }
        String remoteServerURL = remoteServer.getAddress().toString();
        List<String> cleanedParams = null;

        if (this.getLoadParamsFromFile()) {
            cleanedParams = loadExternalParameterFile(build);
        } else {
            // tokenize all variables and encode all variables, then build the fully-qualified trigger URL
            cleanedParams = getCleanedParameters();
            cleanedParams = replaceTokens(build, listener, cleanedParams);
        }

        String jobName = replaceToken(build, listener, this.getJob());

        String securityToken = replaceToken(build, listener, this.getToken());

        boolean isRemoteParameterized = isRemoteJobParameterized(jobName, build, listener);
        String triggerUrlString = this.buildTriggerUrl(jobName, securityToken, cleanedParams, isRemoteParameterized);

        // Trigger remote job
        // print out some debugging information to the console

        //listener.getLogger().println("URL: " + triggerUrlString);
        listener.getLogger().println("Triggering this remote job: " + jobName);

        // get the ID of the Next Job to run.
        if (this.getPreventRemoteBuildQueue()) {
            listener.getLogger().println("Checking that the remote job " + jobName + " is not currently building.");
            String preCheckUrlString = this.buildGetUrl(jobName, securityToken);
            preCheckUrlString += "/lastBuild";
            preCheckUrlString += "/api/json/";
            JSONObject preCheckResponse = sendHTTPCall(preCheckUrlString, "GET", build, listener);
            
            if ( preCheckResponse != null ) {
                // check the latest build on the remote server to see if it's running - if so wait until it has stopped.
                // if building is true then the build is running
                // if result is null the build hasn't finished - but might not have started running.
                while (preCheckResponse.getBoolean("building") == true || preCheckResponse.getString("result") == null) {
                    listener.getLogger().println("Remote build is currently running - waiting for it to finish.");
                    preCheckResponse = sendHTTPCall(preCheckUrlString, "POST", build, listener);
                    listener.getLogger().println("Waiting for " + this.pollInterval + " seconds until next retry.");

                    // Sleep for 'pollInterval' seconds.
                    // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                    try {
                        Thread.sleep(this.pollInterval * 1000);
                    } catch (InterruptedException e) {
                        this.failBuild(e, listener);
                    }
                }
                listener.getLogger().println("Remote job " + jobName + " is not currently building.");
            } else {
                this.failBuild(new Exception("Got a blank response from Remote Jenkins Server, cannot continue."), listener);
            }

        } else {
            listener.getLogger().println("Not checking if the remote job " + jobName + " is building.");
        }

        String queryUrlString = this.buildGetUrl(jobName, securityToken);
        queryUrlString += "/api/json/";

        //listener.getLogger().println("Getting ID of next job to build. URL: " + queryUrlString);
        JSONObject queryResponseObject = sendHTTPCall(queryUrlString, "GET", build, listener);
        if (queryResponseObject == null) {
            //This should not happen as this page should return a JSON object
            this.failBuild(new Exception("Got a blank response from Remote Jenkins Server [" + remoteServerURL + "], cannot continue."), listener);
        }

        int nextBuildNumber = queryResponseObject.getInt("nextBuildNumber");

        if (this.getOverrideAuth()) {
            listener.getLogger().println(
                    "Using job-level defined credentials in place of those from remote Jenkins config ["
                            + this.getRemoteJenkinsName() + "]");
        }

        listener.getLogger().println("Triggering remote job now.");
        JSONObject triggeredBuildResponse = sendHTTPCall(triggerUrlString, "POST", build, listener);
        
        int queueID = -1;
        boolean foundInQueue = false;
        // Wait for remote triggered job not to be in queue before checking the actual build for 20 mins
        // checkingQueue: for (int timeout = 0; timeout < 20 * 60 * 1000; timeout += this.pollInterval * 1000) {
        checkingQueue: for (int attempts = 20; attempts > 0; attempts--) {
            foundInQueue = false;

            String queueUrlString = this.buildGetUrlForQueue();
            JSONObject queueResponseObject = sendHTTPCall(queueUrlString, "GET", build, listener);
            if (queueResponseObject == null) {
                listener.getLogger().println("Queue Query failed.");
                continue;
            }

            JSONArray queued_items = queueResponseObject.getJSONArray("items");
            listener.getLogger().println("\nNumber of Queued Builds: " + queued_items.size());

            if (queued_items.size() == 0) {
                listener.getLogger().println("\nNothing in queue. It should be running already.\n");
                break checkingQueue;
            }
            foundInQueue: for (int i = 0; i < queued_items.size(); i++) {
                JSONObject item = queued_items.getJSONObject(i);
                listener.getLogger().println("Checking parameters of queued builds #" + i);
                JSONArray actions = item.getJSONArray("actions");
                for (int j = 0; j < actions.size(); j++) {
                    JSONObject action = actions.getJSONObject(j);
                    if (!action.has("parameters")) continue;
                    JSONArray parameters = action.getJSONArray("parameters");
                    // Check if the parameters match
                    if (compareParameters(listener, parameters, cleanedParams)) {
                        // We now have a very high degree of confidence that this is the correct item in queue.
                        // It is still possible that this is a false positive if there are no parameters,
                        // or multiple jobs use the same parameters.
                        queueID = item.getInt("id");
                        listener.getLogger().println("Queue ID of the matching build: " + queueID + "\n");
                        foundInQueue = true;
                        break foundInQueue;
                    }
                }
            }

            if (foundInQueue == false) {
                listener.getLogger().println("\nCannot find a matching build in queue. It should be running already.\n");
                break checkingQueue;
            }
            // Sleep for 'pollInterval' seconds.
            // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
            try {
                Thread.sleep(this.pollInterval * 1000);
            } catch (InterruptedException e) {
                this.failBuild(e, listener);
            }
        }

        // If the build is still in queue after 20 polls, fail the build?
        if (foundInQueue == true) {
            this.failBuild(new Exception("The build is still in queue after " + 20 * this.pollInterval + "seconds. Please check if the remote Jenkins is working properly."), listener);
        }

        // Remote Build should now be building (Not in queue anymore)
        // Get the next build number now after confirming the matching build is not in queue
        // JSONObject queryResponseObject = sendHTTPCall(queryUrlString, "GET", build, listener);
        // int nextBuildNumber = queryResponseObject.getInt("nextBuildNumber");

        boolean isBuildFound = false;
        // Validate the build number via parameters
        foundIt: for (int buildNumber : new SearchPattern(nextBuildNumber, 10)) {
            listener.getLogger().println("Checking Build #" + buildNumber);
            String validateUrlString = this.buildGetUrl(jobName, securityToken) + "/" + buildNumber + "/api/json/";
            JSONObject validateResponse = sendHTTPCall(validateUrlString, "GET", build, listener);
            if (validateResponse == null) {
                listener.getLogger().println("Query failed.");
                continue;
            }
            // If queueID was not found in first place because there was no queue
            if (queueID == -1) {
                listener.getLogger().println("Checking parameters of #" + buildNumber);
                JSONArray actions = validateResponse.getJSONArray("actions");
                for (int i = 0; i < actions.size(); i++) {
                    JSONObject action = actions.getJSONObject(i);
                    if (!action.has("parameters")) continue;
                    JSONArray parameters = action.getJSONArray("parameters");
                    // Check if the parameters match
                    if (compareParameters(listener, parameters, cleanedParams)) {
                        // We now have a very high degree of confidence that this is the correct build.
                        // It is still possible that this is a false positive if there are no parameters,
                        // or multiple jobs use the same parameters.
                        nextBuildNumber = buildNumber;
                        isBuildFound = true;
                        break foundIt;
                    }
                    // This is the wrong build
                    break;
                }

                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                try {
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException e) {
                    this.failBuild(e, listener);
                }
            } else { // If queueID was found, use queueID to find
                listener.getLogger().println("Checking Queue ID of #" + buildNumber);
                if (queueID == validateResponse.getInt("queueId")) {
                    nextBuildNumber = buildNumber;
                    isBuildFound = true;
                    break foundIt;
                }
            }
        }

        if (isBuildFound == false) {
            this.failBuild(new Exception("Cannot find the correct build within +/- 10 builds. Please investigate if it has run or not."), listener);
        }

        listener.getLogger().println("This job is build #[" + Integer.toString(nextBuildNumber) + "] on the remote server.");
        BuildInfoExporterAction.addBuildInfoExporterAction(build, jobName, nextBuildNumber, Result.NOT_BUILT);
        
        //Have to form the string ourselves, as we might not get a response from non-parameterized builds
        String jobURL = remoteServerURL + "/job/" + this.encodeValue(jobName) + "/";

        // This is only for Debug
        // This output whether there is another job running on the remote host that this job had conflicted with.
        // The first condition is what is expected, The second is what would happen if two jobs launched jobs at the
        // same time (and two remote builds were triggered).
        // The third is what would happen if this job was triggers and the remote queue was already full (as the 'next
        // build bumber' would still be the same after this job has triggered the remote job)
        // int newNextBuildNumber = responseObject.getInt( "nextBuildNumber" ); // This should be nextBuildNumber + 1 OR
        // there has been another job scheduled.
        // if (newNextBuildNumber == (nextBuildNumber + 1)) {
        // listener.getLogger().println("DEBUG: No other jobs triggered" );
        // } else if( newNextBuildNumber > (nextBuildNumber + 1) ) {
        // listener.getLogger().println("DEBUG: WARNING Other jobs triggered," + newNextBuildNumber + ", " +
        // nextBuildNumber );
        // } else {
        // listener.getLogger().println("DEBUG: WARNING Did not get the correct build number for the triggered job, previous nextBuildNumber:"
        // + newNextBuildNumber + ", newNextBuildNumber" + nextBuildNumber );
        // }

        // If we are told to block until remoteBuildComplete:
        if (this.getBlockBuildUntilComplete()) {
            listener.getLogger().println("Blocking local job until remote job completes");
            // Form the URL for the triggered job
            String jobLocation = jobURL + nextBuildNumber + "/api/json";

            buildStatusStr = getBuildStatus(jobLocation, build, listener);

            while (buildStatusStr.equals("not started")) {
                listener.getLogger().println("Waiting for remote build to start.");
                listener.getLogger().println("Waiting for " + this.pollInterval + " seconds until next poll.");
                buildStatusStr = getBuildStatus(jobLocation, build, listener);
                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException e) {
                    this.failBuild(e, listener);
                }
            }

            listener.getLogger().println("Remote build started!");
            while (buildStatusStr.equals("running")) {
                listener.getLogger().println("Waiting for remote build to finish.");
                listener.getLogger().println("Waiting for " + this.pollInterval + " seconds until next poll.");
                buildStatusStr = getBuildStatus(jobLocation, build, listener);
                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException e) {
                    this.failBuild(e, listener);
                }
            }
            listener.getLogger().println("Remote build finished with status " + buildStatusStr + ".");
            BuildInfoExporterAction.addBuildInfoExporterAction(build, jobName, nextBuildNumber, Result.fromString(buildStatusStr));

            if (this.getEnhancedLogging()) {
                String buildUrl = getBuildUrl(jobLocation, build, listener);
                String consoleOutput = getConsoleOutput(buildUrl, "GET", build, listener);

                listener.getLogger().println();
                listener.getLogger().println("Console output of remote job:");
                listener.getLogger().println("--------------------------------------------------------------------------------");
                listener.getLogger().println(consoleOutput);
                listener.getLogger().println("--------------------------------------------------------------------------------");
            }

            // If build did not finish with 'success' then fail build step.
            if (!buildStatusStr.equals("SUCCESS")) {
                // failBuild will check if the 'shouldNotFailBuild' parameter is set or not, so will decide how to
                // handle the failure.
                this.failBuild(new Exception("The remote job did not succeed."), listener);
            }
        } else {
            listener.getLogger().println("Not blocking local job until remote job completes - fire and forget.");
        }

        return true;
    }

    private String findParameter(String parameter, List<String> parameters) {
        for (String search : parameters) {
            if (search.startsWith(parameter + "=")) {
                return search.substring(parameter.length() + 1);
            }
        }
        return null;
    }

    private boolean compareParameters(BuildListener listener, JSONArray parameters, List<String> expectedParams) {
        for (int j = 0; j < parameters.size(); j++) {
            JSONObject parameter = parameters.getJSONObject(j);
            String name = parameter.getString("name");
            String expected = findParameter(name, expectedParams);

            if (expected == null) {
                // If we didn't specify all of the parameters, this will happen, so we can not infer that this it he wrong build
                listener.getLogger().println("Unable to find expected value for " + name);
                continue;
            }

            String value = parameter.getString("value");

            listener.getLogger().println("Build Parameters: " + name + " => " + value);
            // If we got the expected value, skip to the next parameter
            if (expected.equals(value)) continue;

            // We didn't get the expected value
            listener.getLogger().println("Param " + name + " doesn't match!");
            return false;
        }
        // All found parameters matched. This if there are no uniquely identifying parameters, this could still be a false positive.
        return true;
    }

    public String getBuildStatus(String buildUrlString, AbstractBuild build, BuildListener listener) throws IOException {
        String buildStatus = "UNKNOWN";

        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());

        if (remoteServer == null) {
            this.failBuild(new Exception("No remote host is defined for this job."), listener);
            return null;
        }

        // print out some debugging information to the console
        //listener.getLogger().println("Checking Status of this job: " + buildUrlString);
        if (this.getOverrideAuth()) {
            listener.getLogger().println(
                    "Using job-level defined credentails in place of those from remote Jenkins config ["
                            + this.getRemoteJenkinsName() + "]");
        }

        JSONObject responseObject = sendHTTPCall(buildUrlString, "GET", build, listener);

        // get the next build from the location

        if (responseObject == null || responseObject.getString("result") == null && responseObject.getBoolean("building") == false) {
            // build not started
            buildStatus = "not started";
        } else if (responseObject.getBoolean("building")) {
            // build running
            buildStatus = "running";
        } else if (responseObject.getString("result") != null) {
            // build finished
            buildStatus = responseObject.getString("result");
        } else {
            // Add additional else to check for unhandled conditions
            listener.getLogger().println("WARNING: Unhandled condition!");
        }

        return buildStatus;
    }

    public String getBuildUrl(String buildUrlString, AbstractBuild build, BuildListener listener) throws IOException {
        String buildUrl = "";

        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());

        if (remoteServer == null) {
            this.failBuild(new Exception("No remote host is defined for this job."), listener);
            return null;
        }

        // print out some debugging information to the console
        //listener.getLogger().println("Checking Status of this job: " + buildUrlString);
        if (this.getOverrideAuth()) {
            listener.getLogger().println(
                    "Using job-level defined credentails in place of those from remote Jenkins config ["
                            + this.getRemoteJenkinsName() + "]");
        }

        JSONObject responseObject = sendHTTPCall(buildUrlString, "GET", build, listener);

        // get the next build from the location

        if (responseObject != null && responseObject.getString("url") != null) {
            buildUrl = responseObject.getString("url");
        } else {
            // Add additional else to check for unhandled conditions
            listener.getLogger().println("WARNING: URL not found in JSON Response!");
            return null;
        }

        return buildUrl;
    }

    public String getConsoleOutput(String urlString, String requestType, AbstractBuild build, BuildListener listener)
            throws IOException {
        
            return getConsoleOutput( urlString, requestType, build, listener, 1 );
    }

    /**
     * Orchestrates all calls to the remote server.
     * Also takes care of any credentials or failed-connection retries.
     * 
     * @param urlString     the URL that needs to be called
     * @param requestType   the type of request (GET, POST, etc)
     * @param build         the build that is being triggered
     * @param listener      build listener
     * @return              a valid JSON object, or null
     * @throws IOException
     */
    public JSONObject sendHTTPCall(String urlString, String requestType, AbstractBuild build, BuildListener listener)
            throws IOException {
        
            return sendHTTPCall( urlString, requestType, build, listener, 1 );
    }

    public String getConsoleOutput(String urlString, String requestType, AbstractBuild build, BuildListener listener, int numberOfAttempts)
            throws IOException {
        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());
        int retryLimit = this.getConnectionRetryLimit();
        
        if (remoteServer == null) {
            this.failBuild(new Exception("No remote host is defined for this job."), listener);
            return null;
        }

        HttpURLConnection connection = null;

        String consoleOutput = null;

        URL buildUrl = new URL(urlString+"consoleText");
        connection = (HttpURLConnection) buildUrl.openConnection();

        // if there is a username + apiToken defined for this remote host, then use it
        String usernameTokenConcat;

        if (this.getOverrideAuth()) {
            usernameTokenConcat = this.getAuth()[0].getUsername() + ":" + this.getAuth()[0].getPassword();
        } else {
            usernameTokenConcat = remoteServer.getAuth()[0].getUsername() + ":"
                    + remoteServer.getAuth()[0].getPassword();
        }

        if (!usernameTokenConcat.equals(":")) {
            // token-macro replacment
            try {
                usernameTokenConcat = TokenMacro.expandAll(build, listener, usernameTokenConcat);
            } catch (MacroEvaluationException e) {
                this.failBuild(e, listener);
            } catch (InterruptedException e) {
                this.failBuild(e, listener);
            }

            byte[] encodedAuthKey = Base64.encodeBase64(usernameTokenConcat.getBytes());
            connection.setRequestProperty("Authorization", "Basic " + new String(encodedAuthKey));
        }

        try {
            connection.setDoInput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod(requestType);
            // wait up to 5 seconds for the connection to be open
            connection.setConnectTimeout(5000);
            connection.connect();
            
            InputStream is;
            try {
                is = connection.getInputStream();
            } catch (FileNotFoundException e) {
                // In case of a e.g. 404 status
                is = connection.getErrorStream();
            }
            
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            // String response = "";
            StringBuilder response = new StringBuilder();
        
            while ((line = rd.readLine()) != null) {
                response.append(line+"\n");
            }
            rd.close();
            

            consoleOutput = response.toString();
        } catch (IOException e) {
            
            //If we have connectionRetryLimit set to > 0 then retry that many times.
            if( numberOfAttempts <= retryLimit) {
                listener.getLogger().println("Connection to remote server failed, waiting for to retry - " + this.pollInterval + " seconds until next attempt.");
                e.printStackTrace();
                
                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException ex) {
                    this.failBuild(ex, listener);
                }

 
                listener.getLogger().println("Retry attempt #" + numberOfAttempts + " out of " + retryLimit );
                numberOfAttempts++;
                consoleOutput = getConsoleOutput(urlString, requestType, build, listener, numberOfAttempts);
            } else if(numberOfAttempts > retryLimit){
                //reached the maximum number of retries, time to fail
                this.failBuild(new Exception("Max number of connection retries have been exeeded."), listener);
            } else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
                this.failBuild(e, listener);
            }
            
        } finally {
            // always make sure we close the connection
            if (connection != null) {
                connection.disconnect();
            }
            // and always clear the query string and remove some "global" values
            this.clearQueryString();
            // this.build = null;
            // this.listener = null;

        }
        return consoleOutput;
    }

    /**
     * Same as sendHTTPCall, but keeps track of the number of failed connection attempts (aka: the number of times this
     * method has been called).
     * In the case of a failed connection, the method calls it self recursively and increments  numberOfAttempts
     * 
     * @see sendHTTPCall
     * @param numberOfAttempts  number of time that the connection has been attempted
     * @return
     * @throws IOException
     */
    public JSONObject sendHTTPCall(String urlString, String requestType, AbstractBuild build, BuildListener listener, int numberOfAttempts)
            throws IOException {
        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());
        int retryLimit = this.getConnectionRetryLimit();
        
        if (remoteServer == null) {
            this.failBuild(new Exception("No remote host is defined for this job."), listener);
            return null;
        }

        HttpURLConnection connection = null;

        JSONObject responseObject = null;

            URL buildUrl = new URL(urlString);
            connection = (HttpURLConnection) buildUrl.openConnection();

            // if there is a username + apiToken defined for this remote host, then use it
            String usernameTokenConcat;

            if (this.getOverrideAuth()) {
                usernameTokenConcat = this.getAuth()[0].getUsername() + ":" + this.getAuth()[0].getPassword();
            } else {
                usernameTokenConcat = remoteServer.getAuth()[0].getUsername() + ":"
                        + remoteServer.getAuth()[0].getPassword();
            }

            if (!usernameTokenConcat.equals(":")) {
                // token-macro replacment
                try {
                    usernameTokenConcat = TokenMacro.expandAll(build, listener, usernameTokenConcat);
                } catch (MacroEvaluationException e) {
                    this.failBuild(e, listener);
                } catch (InterruptedException e) {
                    this.failBuild(e, listener);
                }

                byte[] encodedAuthKey = Base64.encodeBase64(usernameTokenConcat.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + new String(encodedAuthKey));
            }

        try {
            connection.setDoInput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod(requestType);
            // wait up to 5 seconds for the connection to be open
            connection.setConnectTimeout(5000);
            connection.connect();
            
            InputStream is;
            try {
                is = connection.getInputStream();
            } catch (FileNotFoundException e) {
                // In case of a e.g. 404 status
                is = connection.getErrorStream();
            }
            
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            String line;
            // String response = "";
            StringBuilder response = new StringBuilder();
        
            while ((line = rd.readLine()) != null) {
                response.append(line);
            }
            rd.close();
            
            // JSONSerializer serializer = new JSONSerializer();
            // need to parse the data we get back into struct
            //listener.getLogger().println("Called URL: '" + urlString +  "', got response: '" + response.toString() + "'");

            //Solving issue reported in this comment: https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/3#issuecomment-39369194
            //Seems like in Jenkins version 1.547, when using "/build" (job API for non-parameterized jobs), it returns a string indicating the status.
            //But in newer versions of Jenkins, it just returns an empty response.
            //So we need to compensate and check for both.
            if ( JSONUtils.mayBeJSON(response.toString()) == false) {
                listener.getLogger().println("Remote Jenkins server returned empty response or invalid JSON - but we can still proceed with the remote build.");
                return null;
            } else {
                responseObject = (JSONObject) JSONSerializer.toJSON(response.toString());
            }

        } catch (IOException e) {
            listener.getLogger().println(e.getMessage());
            //If we have connectionRetryLimit set to > 0 then retry that many times.
            if( numberOfAttempts <= retryLimit) {
                listener.getLogger().println("Connection to remote server failed, waiting for to retry - " + this.pollInterval + " seconds until next attempt.");
                e.printStackTrace();
                
                // Sleep for 'pollInterval' seconds.
                // Sleep takes miliseconds so need to convert this.pollInterval to milisecopnds (x 1000)
                try {
                    // Could do with a better way of sleeping...
                    Thread.sleep(this.pollInterval * 1000);
                } catch (InterruptedException ex) {
                    this.failBuild(ex, listener);
                }

 
                listener.getLogger().println("Retry attempt #" + numberOfAttempts + " out of " + retryLimit );
                numberOfAttempts++;
                responseObject = sendHTTPCall(urlString, requestType, build, listener, numberOfAttempts);
            }else if(numberOfAttempts > retryLimit){
                //reached the maximum number of retries, time to fail
                this.failBuild(new Exception("Max number of connection retries have been exeeded."), listener);
            }else{
                //something failed with the connection and we retried the max amount of times... so throw an exception to mark the build as failed.
                this.failBuild(e, listener);
            }
            
        } finally {
            // always make sure we close the connection
            if (connection != null) {
                connection.disconnect();
            }
            // and always clear the query string and remove some "global" values
            this.clearQueryString();
            // this.build = null;
            // this.listener = null;

        }
        return responseObject;
    }

    /**
     * Helper function for character encoding
     * 
     * @param dirtyValue
     * @return encoded value
     */
    private String encodeValue(String dirtyValue) {
        String cleanValue = "";

        try {
            cleanValue = URLEncoder.encode(dirtyValue, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return cleanValue;
    }

    // Getters
    public String getRemoteJenkinsName() {
        return this.remoteJenkinsName;
    }

    public String getJob() {
        return this.job;
    }

    public boolean getShouldNotFailBuild() {
        return this.shouldNotFailBuild;
    }

    public boolean getEnhancedLogging() {
        return this.enhancedLogging;
    }

    public boolean getPreventRemoteBuildQueue() {
        return this.preventRemoteBuildQueue;
    }

    public boolean getBlockBuildUntilComplete() {
        return this.blockBuildUntilComplete;
    }

    public int getPollInterval() {
        return this.pollInterval;
    }

    /**
     * @return the connectionRetryLimit
     */
    public int getConnectionRetryLimit() {
        return connectionRetryLimit;
    }

    public String getToken() {
        return this.token;
    }

    public boolean getLoadParamsFromFile() {
        return this.loadParamsFromFile;
    }
    
    public String getParameterFile() {
        return this.parameterFile;
    }

    /**
     * Based on the number of parameters set (and only on params set), returns the proper URL string 
     * @return A string which represents a portion of the build URL
     */
    private String getBuildTypeUrl() {
        boolean isParameterized = (this.getParameters().length() > 0);

        if (isParameterized) {
            return RemoteBuildConfiguration.paramerizedBuildUrl;
        } else {
            return RemoteBuildConfiguration.normalBuildUrl;
        }
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
            return RemoteBuildConfiguration.paramerizedBuildUrl;
        } else {
            return RemoteBuildConfiguration.normalBuildUrl;
        }
    }
    
    /**
     * Pokes the remote server to see if it has default parameters defined or not.
     * 
     * @param jobName Name of the remote job to test
     * @param build Build object
     * @param listener listner object
     * @return true if the remote job has default parameters set, otherwise false
     */
    private boolean isRemoteJobParameterized(String jobName, AbstractBuild build, BuildListener listener) {
        boolean isParameterized = false;
        
        //build the proper URL to inspect the remote job
        RemoteJenkinsServer remoteServer = this.findRemoteHost(this.getRemoteJenkinsName());
        String remoteServerUrl = remoteServer.getAddress().toString();
        remoteServerUrl += "/job/" + encodeValue(jobName);
        remoteServerUrl += "/api/json";
        
        try {
            JSONObject response = sendHTTPCall(remoteServerUrl, "GET", build, listener);

            if(response.getJSONArray("actions").size() >= 1){
                isParameterized = true;
            }
            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return isParameterized;
    }

    public boolean getOverrideAuth() {
        return this.overrideAuth;
    }

    public Auth[] getAuth() {
        return auth.toArray(new Auth[this.auth.size()]);

    }

    public String getParameters() {
        return this.parameters;
    }

    private List<String> getParameterList() {
        return this.parameterList;
    }

    public String getQueryString() {
        return this.queryString;
    }

    private void setQueryString(String string) {
        this.queryString = string.trim();
    }

    /**
     * Convenience function for setting the query string to empty
     */
    private void clearQueryString() {
        this.setQueryString("");
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
v         */
        private CopyOnWriteList<RemoteJenkinsServer> remoteSites = new CopyOnWriteList<RemoteJenkinsServer>();

        /**
         * In order to load the persisted global configuration, you have to call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }

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

        public ListBoxModel doFillRemoteJenkinsNameItems() {
            ListBoxModel model = new ListBoxModel();

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
    }
}
