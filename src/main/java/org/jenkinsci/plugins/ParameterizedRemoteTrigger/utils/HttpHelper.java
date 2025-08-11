package org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils;

import static java.util.Collections.emptyMap;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.StringTools.NL;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import net.sf.json.util.JSONUtils;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.ConnectionResponse;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.JenkinsCrumb;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.RemoteJenkinsServer;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NullAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.ExceedRetryLimitException;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.ForbiddenException;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.UnauthorizedException;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.UrlNotFoundException;

import hudson.AbortException;
import hudson.ProxyConfiguration;

public class HttpHelper {

	private static final String paramerizedBuildUrl = "/buildWithParameters";
	private static final String normalBuildUrl = "/build";
	private static final String buildTokenRootUrl = "/buildByToken";
	public static final String HTTP_GET = "GET";
	public static final String HTTP_POST = "POST";

	private static Logger logger = Logger.getLogger(HttpHelper.class.getName());

	/**
	 * Helper function to allow values to be added to the query string from any
	 * method.
	 *
	 * @param item
	 */
	private static String addToQueryString(String queryString, String item) {
		if (isBlank(queryString)) {
			return item;
		} else {
			return queryString + "&" + item;
		}
	}

	/**
	 * Return the Collection&lt;String&gt; in an encoded query-string.
	 *
	 * @param parameters
	 *            the parameters needed to trigger the remote job.
	 * @return query-parameter-formated URL-encoded string.
	 */
	public static String buildUrlQueryString(@NonNull final Map<String, String> parameters) {
		return parameters.entrySet()
				.stream()
				.map(entry -> String.format(
						"%s=%s",
						encodeValue(entry.getKey()),
						encodeValue(entry.getValue())
				))
				.collect(Collectors.joining("&"));
	}

	/**
	 * Same as above, but takes in to consideration if the remote server has any
	 * default parameters set or not
	 *
	 * @param isRemoteJobParameterized
	 *            Boolean indicating if the remote job is parameterized or not
	 * @return A string which represents a portion of the build URL
	 */
	private static String getBuildTypeUrl(boolean isRemoteJobParameterized, @NonNull Map<String, String> params) {
		boolean isParameterized = isRemoteJobParameterized || params.size() > 0;

		if (isParameterized) {
			return paramerizedBuildUrl;
		} else {
			return normalBuildUrl;
		}
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
				remoteJobUrl = String.format("%s/job/%s", remoteJobUrl, encodeValue(segment));
			}
		}
		return remoteJobUrl;
	}

	/**
	 * Helper function for character encoding
	 *
	 * @param dirtyValue
	 *            something that wasn't encoded in UTF-8
	 * @return encoded value
	 */
	public static String encodeValue(String dirtyValue) {
		String cleanValue = "";

		try {
			cleanValue = URLEncoder.encode(dirtyValue, "UTF-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return cleanValue;
	}

	private static String readInputStream(HttpURLConnection connection) throws IOException {
		BufferedReader rd = null;
		try {

			InputStream is;
			try {
				is = connection.getInputStream();
			} catch (FileNotFoundException e) {
				// In case of a e.g. 404 status
				is = connection.getErrorStream();
			}

			rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			String line;
			StringBuilder response = new StringBuilder();
			while ((line = rd.readLine()) != null) {
				if (response.length() > 0)
					response.append(NL);
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
	 * @param context
	 *            the context of this Builder/BuildStep.
	 * @return {@link JenkinsCrumb} a JenkinsCrumb.
	 * @throws IOException
	 *             if the request failed.
	 */
	@NonNull
	private static JenkinsCrumb getCrumb(BuildContext context, Auth2 auth, boolean isCacheEnabled)
			throws IOException {
		String address = context.effectiveRemoteServer.getAddress();
		if (address == null) {
			throw new AbortException(
					"The remote server address can not be empty, or it must be overridden on the job configuration.");
		}

		URL crumbProviderUrl;
		String globalHost = "";
		try {
			String xpathValue = URLEncoder.encode("concat(//crumbRequestField,\":\",//crumb)", "UTF-8");
			crumbProviderUrl = new URL(address.concat("/crumbIssuer/api/xml?xpath=").concat(xpathValue));
			globalHost = crumbProviderUrl.getHost();

			JenkinsCrumb jenkinsCrumb = DropCachePeriodicWork.safeGetCrumb(globalHost, isCacheEnabled);
			if (jenkinsCrumb != null) {
				context.logger.println("reuse cached crumb: " + globalHost);
				return jenkinsCrumb;
			}
			HttpURLConnection connection = (HttpURLConnection) getAuthorizedConnection(context, crumbProviderUrl, auth);
			int responseCode = connection.getResponseCode();
			if (responseCode == 401) {
				throw new UnauthorizedException(crumbProviderUrl);
			} else if (responseCode == 403) {
				throw new ForbiddenException(crumbProviderUrl);
			} else if (responseCode == 404) {
				context.logger.println("CSRF protection is disabled on the remote server.");
				return DropCachePeriodicWork.safePutCrumb(globalHost, new JenkinsCrumb(), isCacheEnabled);
			} else if (responseCode == 200) {
				context.logger.println("CSRF protection is enabled on the remote server.");
				String response = readInputStream(connection);
				String[] split = response.split(":");
				JenkinsCrumb crumb = new JenkinsCrumb(split[0], split[1]);
				return DropCachePeriodicWork.safePutCrumb(globalHost, crumb, isCacheEnabled);
			} else {
				throw new RuntimeException(String.format("Unexpected response. Response code: %s. Response message: %s",
						responseCode, connection.getResponseMessage()));
			}
		} catch (FileNotFoundException e) {
			context.logger.println("CSRF protection is disabled on the remote server.");
			return DropCachePeriodicWork.safePutCrumb(globalHost, new JenkinsCrumb(), isCacheEnabled);
		}
	}

	/**
	 * For POST requests a crumb is needed. This methods gets a crumb and sets it in
	 * the header.
	 * https://wiki.jenkins.io/display/JENKINS/Remote+access+API#RemoteaccessAPI-CSRFProtection
	 *
	 * @param connection
	 * @param context
	 * @throws IOException
	 */
	private static void addCrumbToConnection(HttpURLConnection connection, BuildContext context, Auth2 auth,
			boolean isCacheEnabled) throws IOException {
		String method = connection.getRequestMethod();
		if (method != null && method.equalsIgnoreCase("POST") && auth.requiresCrumb()) {
			JenkinsCrumb crumb = getCrumb(context, auth, isCacheEnabled);
			if (crumb.isEnabledOnRemote()) {
				connection.setRequestProperty(crumb.getHeaderId(), crumb.getCrumbValue());
			}
		}
	}

	/**
	 * Returns a URLConnection which can be casted to HttpUrlConnection or HttpsUrlConnection
	 * If the user wanted to trust all certificates, the TrustManager and HostVerifier of the connection
	 * will be set properly.
	 *
	 * ATTENTION: TRUSTING ALL CERTIFICATES IS VERY DANGEROUS AND SHOULD ONLY BE USED IF YOU KNOW WHAT YOU DO!
	 * @param context The build context
	 * @param url The url to the remote build
	 * @param auth
	 * @return An authorized connection with or without a NaiveTrustManager
	 * @throws IOException
	 */
	private static URLConnection getAuthorizedConnection(BuildContext context, URL url, Auth2 auth)
			throws IOException {
		URLConnection connection = context.effectiveRemoteServer.isUseProxy() ? ProxyConfiguration.open(url)
				: url.openConnection();

		auth.setAuthorizationHeader(connection, context);

		if (connection instanceof HttpsURLConnection) {
			HttpsURLConnection conn = (HttpsURLConnection) connection;
			if (context.effectiveRemoteServer.getTrustAllCertificates()) {
				// Installing the naive manage
				try {
					SSLContext ctx = SSLContext.getInstance("TLS");
					ctx.init(new KeyManager[0], new TrustManager[]{new NaiveTrustManager()}, new SecureRandom());
					conn.setSSLSocketFactory(ctx.getSocketFactory());

					// Trust every hostname
					HostnameVerifier allHostsValid = (hostname, session) -> true;
					conn.setHostnameVerifier(allHostsValid);
				} catch (NoSuchAlgorithmException | KeyManagementException e) {
					context.logger.println("Unable to trust all certificates.");
				}
			}
			return conn;
		}
		return connection;
	}

	private static String getUrlWithoutParameters(String url) {
		String result = url;
		try {
			URI uri = new URI(url);
			result = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, uri.getFragment()).toString();
		} catch (URISyntaxException e) {
			logger.log(Level.WARNING, e.getMessage(), e);
		}
		return result;
	}

	/**
	 * Build the proper URL to trigger the remote build
	 *
	 * All passed in string have already had their tokens replaced with real values.
	 * All 'params' also have the proper character encoding
	 *
	 * @param jobNameOrUrl
	 *            Name of the remote job
	 * @param securityToken
	 *            Security token used to trigger remote job
	 * @param isRemoteJobParameterized
	 *            Is the remote job parameterized
	 * @param context
	 *            The build context used in this plugin
	 * @return fully formed, fully qualified remote trigger URL
	 * @throws IOException
	 *             throw when it can't pass data checking
	 */
	public static String buildTriggerUrl(String jobNameOrUrl, String securityToken,
			boolean isRemoteJobParameterized, BuildContext context) throws IOException {

		String triggerUrlString;
		String query = "";

		if (context.effectiveRemoteServer.getHasBuildTokenRootSupport()) {
			// start building the proper URL based on known capabilities of the remote
			// server
			if (context.effectiveRemoteServer.getAddress() == null) {
				throw new AbortException(
						"The remote server address can not be empty, or it must be overridden on the job configuration.");
			}
			triggerUrlString = context.effectiveRemoteServer.getAddress();
			triggerUrlString += buildTokenRootUrl;
			triggerUrlString += getBuildTypeUrl(isRemoteJobParameterized, emptyMap());
			query = addToQueryString(query, "job=" + encodeValue(jobNameOrUrl)); // TODO: does it work with full URL?

		} else {
			triggerUrlString = generateJobUrl(context.effectiveRemoteServer, jobNameOrUrl);
			triggerUrlString += getBuildTypeUrl(isRemoteJobParameterized, emptyMap());
		}

		// don't try to include a security token in the URL if none is provided
		if (!securityToken.equals("")) {
			query = addToQueryString(query, "token=" + encodeValue(securityToken));
		}

		// by adding "delay=0", this will (theoretically) force this job to the top of
		// the remote queue
		query = addToQueryString(query, "delay=0");

		triggerUrlString += "?" + query;

		return triggerUrlString;
	}

	/**
	 * Same as sendHTTPCall, but keeps track of the number of failed connection
	 * attempts (aka: the number of times this method has been called). In the case
	 * of a failed connection, the method calls it self recursively and increments
	 * the number of attempts.
	 *
	 * @param urlString
	 *            the URL that needs to be called.
	 * @param requestType
	 *            the type of request (GET, POST, etc).
	 * @param context
	 *            the context of this Builder/BuildStep.
	 * @param postParams
	 *            parameters to post
	 * @param numberOfAttempts
	 *            number of time that the connection has been attempted
	 * @param readTimeout
	 *            read timeout in milliseconds
	 * @param pollInterval
	 *            interval between each retry in second
	 * @param retryLimit
	 *            the retry uplimit
	 * @param auth
	 *            auth used to overwrite the default auth
	 * @param rawRespRef
	 *            the raw http response
	 * @return {@link ConnectionResponse} the response to the HTTP request.
	 * @throws IOException
	 *             all the possibilities of HTTP exceptions
	 * @throws InterruptedException
	 *             if any thread has interrupted the current thread.
	 *
	 */
	private static ConnectionResponse sendHTTPCall(String urlString, String requestType, BuildContext context,
			Map<String, String> postParams, int readTimeout, int numberOfAttempts, int pollInterval, int retryLimit,
			Auth2 auth, StringBuilder rawRespRef, boolean isCrubmCacheEnabled)
			throws IOException, InterruptedException {

		JSONObject responseObject = null;
		Map<String, List<String>> responseHeader = null;
		int responseCode = 0;

		byte[] postDataBytes = new byte[]{};
		String parmsString = "";
		if (HTTP_POST.equalsIgnoreCase(requestType) && postParams != null && postParams.size() > 0) {
			parmsString = buildUrlQueryString(postParams);
			postDataBytes = parmsString.getBytes(StandardCharsets.UTF_8);
		}

		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) getAuthorizedConnection(context, url, auth);

		try {
			conn.setDoInput(true);
			conn.setRequestProperty("Accept", "application/json");
			conn.setRequestProperty("Accept-Language", "UTF-8");
			if (requestType.equals(HTTP_POST) && OtelUtils.isOpenTelemetryAvailable() ) {
				String traceParentHeader = OtelUtils.getTraceParent();
				if (StringUtils.isNotBlank(OtelUtils.getTraceParent())){
					conn.setRequestProperty("traceparent", traceParentHeader);
				}
			}
			conn.setRequestMethod(requestType);
			conn.setReadTimeout(readTimeout);
			addCrumbToConnection(conn, context, auth, isCrubmCacheEnabled);
			// wait up to 5 seconds for the connection to be open
			conn.setConnectTimeout(5000);
			if (HTTP_POST.equalsIgnoreCase(requestType)) {
				conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				conn.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));
				conn.setDoOutput(true);
				conn.getOutputStream().write(postDataBytes);
			}

			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

			logger.finer(String.format("%s begin: %s", urlString, sdf.format(new Date())));
			Instant before = Instant.now();
			conn.connect();
			Instant after = Instant.now();
			logger.finer(
					String.format("%s end: elapsed [%s] ms", urlString, Duration.between(before, after).toMillis()));
			responseHeader = conn.getHeaderFields();
			if (HTTP_POST.equalsIgnoreCase(requestType)) {
				// if connection to the server succeeded we should not perform any further retries
				// of a POST request since the data may have been transferred and since POST is not
				// idem-potent reposting is not a good idea.
				// Setting retryLimit to -1 will avoid potential double-POSTs due to timeouts during getResponseCode
				retryLimit = -1;
			}
			responseCode = conn.getResponseCode();

			if (responseCode == 401) {
				throw new UnauthorizedException(url);
			} else if (responseCode == 403) {
				throw new ForbiddenException(url);
			} else if (responseCode == 404) {
				throw new UrlNotFoundException(url);
			} else {
				String response = trimToNull(readInputStream(conn));
				if (rawRespRef != null) {
					rawRespRef.append(response);
				}

				// JSONSerializer serializer = new JSONSerializer();
				// need to parse the data we get back into struct
				// listener.getLogger().println("Called URL: '" + urlString + "', got response:
				// '" + response.toString() + "'");

				// Solving issue reported in this comment:
				// https://github.com/jenkinsci/parameterized-remote-trigger-plugin/pull/3#issuecomment-39369194
				// Seems like in Jenkins version 1.547, when using "/build" (job API for
				// non-parameterized jobs), it returns a string indicating the status.
				// But in newer versions of Jenkins, it just returns an empty response.
				// So we need to compensate and check for both.
				if (responseCode >= 400 || !JSONUtils.mayBeJSON(response)) {
					return new ConnectionResponse(responseHeader, response, responseCode);
				} else {
					try {
						responseObject = (JSONObject) JSONSerializer.toJSON(response);
					} catch (JSONException e) {
						// despite JSONUtils.mayBeJSON believing that this might be JSON, it looks like it wasn't
						return new ConnectionResponse(responseHeader, response, responseCode);
					}
				}
			}

		} catch (SSLHandshakeException handshakeException) {
			context.logger.println("An SSLHandshakeException occured. The certificate might not be trusted!\n" +
					"Set 'Trust all certificates' and try again, if you want to accept untrusted certificates.\n");
			throw handshakeException;
		} catch (IOException e) {

			// E.g. "HTTP/1.1 403 No valid crumb was included in the request"
			List<String> hints = responseHeader != null ? responseHeader.get(null) : null;
			String hintsString = (hints != null && hints.size() > 0) ? " - " + hints.toString() : "";

			// Shouldn't expose the token in console
			logger.log(Level.WARNING, e.getMessage() + hintsString, e);
			// If we have connectionRetryLimit set to > 0 then retry that many times.
			if (numberOfAttempts <= retryLimit) {
				context.logger.println(String.format(
						"Connection to remote server failed [%s], waiting to retry - %s seconds until next attempt. URL: %s, parameters: %s",
						(responseCode == 0 ? e.getMessage() : responseCode), pollInterval,
						getUrlWithoutParameters(urlString), parmsString));

				// Sleep for 'pollInterval' seconds.
				// Sleep takes milliseconds so need to convert this.pollInterval to milliseconds
				// (x 1000)
				// Could do with a better way of sleeping...
				Thread.sleep(pollInterval * 1000);

				context.logger.println("Retry attempt #" + numberOfAttempts + " out of " + retryLimit);
				numberOfAttempts++;
				return sendHTTPCall(urlString, requestType, context, postParams, readTimeout,
						numberOfAttempts, pollInterval, retryLimit, auth, rawRespRef, isCrubmCacheEnabled);

			} else {
				context.logger.println(String.format(
						"Connection to remote server failed [%s], number of retries exceeded. URL: %s, parameters: %s",
						(responseCode == 0 ? e.getMessage() : responseCode),
						getUrlWithoutParameters(urlString), parmsString));

				// reached the maximum number of retries, time to fail
				throw new ExceedRetryLimitException();
			}

		} finally {
			// always make sure we close the connection
			if (conn != null) {
				conn.disconnect();
			}
		}
		return new ConnectionResponse(responseHeader, responseObject, responseCode);
	}

	private static ConnectionResponse tryCall(String urlString, String method, BuildContext context,
			Map<String, String> params, int readTimeout, int pollInterval, int retryLimit, Auth2 auth, StringBuilder rawRespRef,
			Semaphore lock, boolean isCrubmCacheEnabled) throws IOException, InterruptedException {
		if (lock == null) {
			context.logger.println("calling remote without locking...");
			return sendHTTPCall(urlString, method, context, null, readTimeout,
					1, pollInterval, retryLimit, auth, rawRespRef, isCrubmCacheEnabled);
		}
		Boolean isAcquired = null;
		try {
			try {
				isAcquired = lock.tryAcquire(pollInterval, TimeUnit.SECONDS);
				logger.log(Level.FINE, String.format("calling %s in semaphore...", urlString));

				// if we can't lock, just let it go.
			} catch (InterruptedException e) {
				logger.log(Level.WARNING, "fail to acquire lock because of interrupt, skip locking...", e);
			}
			if (isAcquired != null && !isAcquired) {
				logger.warning("fail to acquire lock because of timeout, skip locking...");
			}

			ConnectionResponse cr = sendHTTPCall(urlString, method, context, params, readTimeout,
					1, pollInterval, retryLimit, auth, rawRespRef, isCrubmCacheEnabled);
			return cr;

		} finally {
			if (isAcquired != null && isAcquired) {
				lock.release();
			}
		}
	}

	private static Auth2 effectiveAuth(BuildContext context, Auth2 overrideAuth) {
		if (overrideAuth != null && !(overrideAuth instanceof NullAuth)) {
			// use Authorization Header if configured locally
			return overrideAuth;
		} else {
			Auth2 serverAuth = context.effectiveRemoteServer.getAuth2();
			if (serverAuth != null) {
				// use Authorization Header configured globally for remoteServer
				return serverAuth;
			} else {
				return NullAuth.INSTANCE;
			}
		}
	}

	public static ConnectionResponse tryPost(String urlString, BuildContext context, Map<String, String> params,
			int readTimeout, int pollInterval, int retryLimit, Auth2 overrideAuth, Semaphore lock,
			boolean isCrubmCacheEnabled) throws IOException, InterruptedException {

		return tryCall(urlString, HTTP_POST, context, params, readTimeout, pollInterval, retryLimit,
				effectiveAuth(context, overrideAuth), null, lock, isCrubmCacheEnabled);
	}

	public static ConnectionResponse tryGet(String urlString, BuildContext context, int readTimeout,
			int pollInterval, int retryLimit, Auth2 overrideAuth, Semaphore lock)
			throws IOException, InterruptedException {
		return tryCall(urlString, HTTP_GET, context, null, readTimeout, pollInterval, retryLimit,
				effectiveAuth(context, overrideAuth), null, lock, false);
	}

}
