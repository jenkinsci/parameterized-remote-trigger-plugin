package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

/**
 * Holds everything regarding the remote server we wish to connect to, including validations and what not.
 * 
 * @author Maurice W.
 * 
 */
public class RemoteJenkinsServer extends AbstractDescribableImpl<RemoteJenkinsServer> {

    private final URL             address;
    private final String          displayName;
    private final boolean         hasBuildTokenRootSupport;
    private final String          username;
    private final String          apiToken;

    private CopyOnWriteList<Auth> auth = new CopyOnWriteList<Auth>();

    @DataBoundConstructor
    public RemoteJenkinsServer(String address, String displayName, boolean hasBuildTokenRootSupport, JSONObject auth)
            throws MalformedURLException {

        this.address = new URL(address);
        this.displayName = displayName.trim();
        this.hasBuildTokenRootSupport = hasBuildTokenRootSupport;

        // Holding on to both of these variables for legacy purposes. The seemingly 'dirty' getters for these properties
        // are for the same reason.
        this.username = "";
        this.apiToken = "";

        // this.auth = new Auth(auth);
        this.auth.replaceBy(new Auth(auth));

    }

    // Getters

    public Auth[] getAuth() {
        return auth.toArray(new Auth[this.auth.size()]);
    }

    public String getDisplayName() {
        String displayName = null;

        if (this.displayName == null || this.displayName.trim().equals("")) {
            displayName = this.getAddress().toString();
        } else {
            displayName = this.displayName;
        }
        return displayName;
    }

    public URL getAddress() {
        return address;
    }

    public boolean getHasBuildTokenRootSupport() {
        return this.hasBuildTokenRootSupport;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<RemoteJenkinsServer> {

        private JSONObject authenticationMode;

        /**
         * In order to load the persisted global configuration, you have to call load() in the constructor.
         */
        /*
         * public DescriptorImpl() { load(); }
         */

        public String getDisplayName() {
            return "";
        }

        public ListBoxModel doFillCredsItems() {
            // StandardUsernameListBoxModel model = new StandardUsernameListBoxModel();

            // Item item = Stapler.getCurrentRequest().findAncestorObject(Item.class);
            // model.withAll(CredentialsProvider.lookupCredentials(StandardUsernameCredentials.class, item, ACL.SYSTEM,
            // Collections.<DomainRequirement>emptyList()));

            return Auth.DescriptorImpl.doFillCredsItems();

            // return model;
        }

        public JSONObject doFillAuthenticationMode() {
            return this.authenticationMode.getJSONObject("authenticationType");
        }

        /**
         * Validates the given address to see that it's well-formed, and is reachable.
         * 
         * @param address
         *            Remote address to be validated
         * @return FormValidation object
         */
        public FormValidation doValidateAddress(@QueryParameter String address) {

            URL host = null;

            // no empty addresses allowed
            if (address == null || address.trim().equals("")) {
                return FormValidation.error("The remote address can not be left empty.");
            }

            URI uri;
            // check if we have a valid, well-formed URL
            try {
                host = new URL(address);
                uri = host.toURI();
            } catch (Exception e) {
                return FormValidation.error("Malformed address (" + address + "), please double-check it.");
            }

            // check that the host is reachable
            int connectionTimeoutInSeconds = 5;

            try {
                HttpURLConnection connection = (HttpURLConnection) host.openConnection();
                connection.setConnectTimeout(connectionTimeoutInSeconds * 1000);
                connection.connect();

            } catch(SSLException e){
                e.printStackTrace();
                return FormValidation.warning("Could not negotiate SSL Connection. Exception raised: " + e.getClass().getCanonicalName() + " with message: " + e.getMessage());
            } catch(SocketTimeoutException e) {
                e.printStackTrace();
                return FormValidation.warning("Could not connect to server within " + connectionTimeoutInSeconds + " seconds, connection timed out.");
            } catch(UnknownHostException e)
            {
                e.printStackTrace();
                return FormValidation.warning("Could not resolve hostname: " + uri.getHost() );
            } catch (IOException e)
            {
                e.printStackTrace();
                return FormValidation.warning("Address looks good, but we were not able to connect to it. Exception:" + e.getClass() +  " Message: " + e.getMessage());
            } catch(Exception e)
            {
                // The old version of the code captured runtime exceptions like this
                // I am not sure if this is best, but I will do the same here.
                e.printStackTrace();
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                return FormValidation.error("An unexpected occurred: " + e.getClass() +  " Message: "  + e.getMessage() + "\n Stack Trace:" + sw.toString() );
            }

            return FormValidation.okWithMarkup("Address looks good");
        }
    }

}
