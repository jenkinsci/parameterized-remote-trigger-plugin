package org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.apache.commons.lang.StringUtils.trimToNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.util.FormValidation;

@Restricted(NoExternalUse.class)
public class FormValidationUtils
{

    public static enum AffectedField {
        JOB_NAME_OR_URL, REMOTE_JENKINS_URL, REMOTE_JENKINS_NAME
    }

    public static class RemoteURLCombinationsResult {

        public final FormValidation formValidation;
        public final AffectedField[] affectedFields;

        public RemoteURLCombinationsResult(FormValidation formValidation, AffectedField...affectedFields) {
            this.formValidation = formValidation;
            this.affectedFields = affectedFields;
        }

        public boolean isAffected(AffectedField field) {
            return Arrays.asList(affectedFields).contains(field);
        }

        public static RemoteURLCombinationsResult OK()
        {
            return new RemoteURLCombinationsResult(FormValidation.ok(), AffectedField.values());
        }

    }

    public static RemoteURLCombinationsResult checkRemoteURLCombinations(String remoteJenkinsUrl, String remoteJenkinsName, String jobNameOrUrl) {
        remoteJenkinsUrl = trimToNull(remoteJenkinsUrl);
        remoteJenkinsName = trimToNull(remoteJenkinsName);
        jobNameOrUrl = trimToNull(jobNameOrUrl);
        boolean remoteUrl_setAndValidUrl = isEmpty(remoteJenkinsUrl) ? false : isURL(remoteJenkinsUrl);
        boolean remoteName_setAndValid = !isEmpty(remoteJenkinsName);
        boolean job_setAndValidUrl = isEmpty(jobNameOrUrl) ? false : isURL(jobNameOrUrl);
        boolean job_setAndNoUrl = isEmpty(jobNameOrUrl) ? false : !isURL(jobNameOrUrl);
        boolean job_containsVariable = isEmpty(jobNameOrUrl) ? false : jobNameOrUrl.indexOf("$") >= 0;
        final String TEXT_WARNING_JOB_VARIABLE = "You are using a variable in the 'Remote Job Name or URL' ('job') field. You have to make sure the value at runtime results in the full job URL";
        final String TEXT_ERROR_NO_URL_AT_ALL = "You have to configure either 'Select a remote host' ('remoteJenkinsName'), 'Override remote host URL' ('remoteJenkinsUrl') or specify a full job URL 'Remote Job Name or URL' ('job')";

        // TODO warn about URLs ending with slash
        if(isEmpty(jobNameOrUrl)) {
            return new RemoteURLCombinationsResult(
                        FormValidation.error("'Remote Job Name or URL' ('job') not specified"),
                        AffectedField.JOB_NAME_OR_URL);
        } else if(!isEmpty(remoteJenkinsUrl) && !isURL(remoteJenkinsUrl)) {
                return new RemoteURLCombinationsResult(
                            FormValidation.error("Invalid URL in 'Override remote host URL' ('remoteJenkinsUrl')"),
                            AffectedField.REMOTE_JENKINS_URL);
        } else if(!remoteUrl_setAndValidUrl && !remoteName_setAndValid && !job_setAndValidUrl) {
            //Root URL or full job URL not specified at all
            if(job_containsVariable) {
                return new RemoteURLCombinationsResult(FormValidation.warning(TEXT_WARNING_JOB_VARIABLE), AffectedField.JOB_NAME_OR_URL);
            } else {
                return new RemoteURLCombinationsResult(FormValidation.error(TEXT_ERROR_NO_URL_AT_ALL),
                            AffectedField.JOB_NAME_OR_URL, AffectedField.REMOTE_JENKINS_NAME, AffectedField.REMOTE_JENKINS_URL);
            }
        } else if(job_setAndValidUrl) {
            return RemoteURLCombinationsResult.OK();
        } else if(remoteUrl_setAndValidUrl && job_setAndNoUrl) {
            if(job_containsVariable) {
                return new RemoteURLCombinationsResult(FormValidation.warning(TEXT_WARNING_JOB_VARIABLE), AffectedField.JOB_NAME_OR_URL);
            } else {
                return RemoteURLCombinationsResult.OK();
            }
        } else if(remoteName_setAndValid && job_setAndNoUrl) {
            if(job_containsVariable) {
                return new RemoteURLCombinationsResult(FormValidation.warning(TEXT_WARNING_JOB_VARIABLE), AffectedField.JOB_NAME_OR_URL);
            } else {
                return RemoteURLCombinationsResult.OK();
            }
        } else {
            return new RemoteURLCombinationsResult(FormValidation.error(TEXT_ERROR_NO_URL_AT_ALL),
                        AffectedField.JOB_NAME_OR_URL, AffectedField.REMOTE_JENKINS_NAME, AffectedField.REMOTE_JENKINS_URL);
        }
    }

    /**
     * Checks if a string is a valid http/https URL.
     *
     * @param string
     *            the url to check.
     * @return true if parameter is a valid http/https URL.
     */
    public static boolean isURL(String string) {
        if(isEmpty(trimToNull(string))) return false;
        String stringLower = string.toLowerCase();
        if(stringLower.startsWith("http://") || stringLower.startsWith("https://")) {
            if(stringLower.indexOf("://") >= stringLower.length()-3) {
                return false; //URL ends after protocol
            }
            if(stringLower.indexOf("$") >= 0) {
                return false; //We interpret $ in URLs as variables which need to be replaced. TODO: What about URI standard which allows $?
            }
            try {
                new URL(string);
                return true;
            }
            catch (MalformedURLException e) {
                return false;
            }
        } else {
            return false;
        }
    }

}
