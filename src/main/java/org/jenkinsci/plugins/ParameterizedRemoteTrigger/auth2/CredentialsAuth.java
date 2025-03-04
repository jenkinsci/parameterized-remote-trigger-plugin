package org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2;

import static org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.Base64Utils.AUTHTYPE_BASIC;

import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.CredentialsNotFoundException;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.Base64Utils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CredentialsAuth extends Auth2 {

    private static final long serialVersionUID = -2650007108928532552L;

    @Extension
    public static final Auth2Descriptor DESCRIPTOR = new CredentialsAuthDescriptor();

    private String credentials;

    @DataBoundConstructor
    public CredentialsAuth() {
        this.credentials = null;
    }

    @DataBoundSetter
    public void setCredentials(String credentials) {
        this.credentials = credentials;
    }

    public String getCredentials() {
        return credentials;
    }

    /**
     * Tries to find the Jenkins Credential and returns the user name.
     * @param item the Item (Job, Pipeline,...) we are currently running in.
     *      The item is required to also get Credentials which are defined in the items scope and not Jenkins globally.
     *      Value can be null, but Credentials e.g. configured on a Folder will not be found in this case, only globally configured Credentials.
     * @return The user name configured in this Credential
     * @throws CredentialsNotFoundException if credential could not be found.
     */
    public String getUserName(Item item) throws CredentialsNotFoundException {
        UsernamePasswordCredentials creds = _getCredentials(item);
        return creds.getUsername();
    }

    /**
     * Tries to find the Jenkins Credential and returns the password.
     * @param item the Item (Job, Pipeline,...) we are currently running in.
     *      The item is required to also get Credentials which are defined in the items scope and not Jenkins globally.
     *      Value can be null, but Credentials e.g. configured on a Folder will not be found in this case, only globally configured Credentials.
     * @return The password configured in this Credential
     * @throws CredentialsNotFoundException if credential could not be found.
     */
    public String getPassword(Item item) throws CredentialsNotFoundException {
        UsernamePasswordCredentials creds = _getCredentials(item);
        return creds.getPassword().getPlainText();
    }

    @Override
    public String toString() {
        return toString(null);
    }

    @Override
    public String toString(Item item) {
        try {
            String userName = getUserName(item);
            return String.format("'%s' as user '%s' (Credentials ID '%s')", getDescriptor().getDisplayName(), userName, credentials);
        }
        catch (CredentialsNotFoundException e) {
            return String.format("'%s'. WARNING! No credentials found with ID '%s'!", getDescriptor().getDisplayName(), credentials);
        }
    }

    /**
     * Looks up the credentialsID attached to this object in the Global Credentials plugin datastore
     * @param item the Item (Job, Pipeline,...) we are currently running in.
     *      The item is required to also get Credentials which are defined in the items scope and not Jenkins globally.
     *      Value can be null, but Credentials e.g. configured on a Folder will not be found in this case, only globally configured Credentials.
     * @return the matched credentials
     * @throws CredentialsNotFoundException if not found
     */
    private UsernamePasswordCredentials _getCredentials(Item item) throws CredentialsNotFoundException {
        List<StandardUsernameCredentials> listOfCredentials = CredentialsProvider.lookupCredentials(
                StandardUsernameCredentials.class, item, ACL.SYSTEM, Collections.<DomainRequirement> emptyList());

        return (UsernamePasswordCredentials) _findCredential(credentials, listOfCredentials);
    }

    private StandardUsernameCredentials _findCredential(String credentialId, List<StandardUsernameCredentials> listOfCredentials) throws CredentialsNotFoundException{
        for (StandardUsernameCredentials cred : listOfCredentials) {
            if (credentialId.equals(cred.getId())) {
                return cred;
            }
        }
        throw new CredentialsNotFoundException(credentialId);
    }

    @Override
    public void setAuthorizationHeader(URLConnection connection, BuildContext context) throws IOException {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            Item item = jenkins.getItem(context.currentItem, jenkins.getItem("/"));
            String authHeaderValue = Base64Utils.generateAuthorizationHeaderValue(AUTHTYPE_BASIC, getUserName(item), getPassword(item), context, false);
            connection.setRequestProperty("Authorization", authHeaderValue);
        }
    }

    @Override
    public Auth2Descriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Symbol("CredentialsAuth")
    public static class CredentialsAuthDescriptor extends Auth2Descriptor {
        @Override
        public String getDisplayName() {
            return "Credentials Authentication";
        }

        public static ListBoxModel doFillCredentialsItems() {
            StandardUsernameListBoxModel model = new StandardUsernameListBoxModel();

            Item item = Stapler.getCurrentRequest2().findAncestorObject(Item.class);

            List<StandardUsernameCredentials> listOfAllCredentails = CredentialsProvider.lookupCredentials(
                    StandardUsernameCredentials.class, item, ACL.SYSTEM, Collections.<DomainRequirement> emptyList());

            List<StandardUsernameCredentials> listOfSandardUsernameCredentials = new ArrayList<StandardUsernameCredentials>();

            // since we only care about 'UsernamePasswordCredentials' objects, lets seek those out and ignore the rest.
            for (StandardUsernameCredentials c : listOfAllCredentails) {
                if (c instanceof UsernamePasswordCredentials) {
                    listOfSandardUsernameCredentials.add(c);
                }
            }
            model.withAll(listOfSandardUsernameCredentials);

            return model;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((credentials == null) ? 0 : credentials.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!this.getClass().isInstance(obj))
            return false;
        CredentialsAuth other = (CredentialsAuth) obj;
        if (credentials == null) {
            if (other.credentials != null)
                return false;
        } else if (!credentials.equals(other.credentials))
            return false;
        return true;
    }

}
