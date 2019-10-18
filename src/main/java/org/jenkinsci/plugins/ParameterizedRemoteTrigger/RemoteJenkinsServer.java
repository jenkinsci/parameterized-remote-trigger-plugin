package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static org.apache.commons.lang.StringUtils.trimToEmpty;

import java.io.Serializable;
import javax.net.ssl.*;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;

import javax.annotation.CheckForNull;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2.Auth2Descriptor;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NoneAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.NaiveTrustManager;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

/**
 * Holds everything regarding the remote server we wish to connect to, including validations and what not.
 *
 * @author Maurice W.
 *
 */
public class RemoteJenkinsServer extends AbstractDescribableImpl<RemoteJenkinsServer> implements Cloneable, Serializable {

    private static final long serialVersionUID = -9211781849078964416L;

    /**
     * Default for this class is No Authentication
     */
    private static final Auth2 DEFAULT_AUTH = NoneAuth.INSTANCE;

    /**
     * We need to keep this for compatibility - old config deserialization!
     * @deprecated since 2.3.0-SNAPSHOT - use {@link Auth2} instead.
     */
    @CheckForNull
    private transient List<Auth> auth;

    @CheckForNull
    private String     displayName;
    private boolean    hasBuildTokenRootSupport;
    private boolean    trustAllCertificates;
    private boolean    overrideTrustAllCertificates;

    @CheckForNull
    private Auth2      auth2;
    @CheckForNull
    private String     address;
    private boolean    useProxy;

    @DataBoundConstructor
    public RemoteJenkinsServer() {
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
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        this.trustAllCertificates = trustAllCertificates;
    }

    @DataBoundSetter
    public void setOverrideTrustAllCertificates(boolean overrideTrustAllCertificates) {
        this.overrideTrustAllCertificates = overrideTrustAllCertificates;
    }

    @DataBoundSetter
    public void setDisplayName(String displayName)
    {
        this.displayName = trimToEmpty(displayName);
    }

    @DataBoundSetter
    public void setHasBuildTokenRootSupport(boolean hasBuildTokenRootSupport)
    {
        this.hasBuildTokenRootSupport = hasBuildTokenRootSupport;
    }

    @DataBoundSetter
    public void setUseProxy(boolean useProxy) {
        this.useProxy = useProxy;
    }

    @DataBoundSetter
    public void setAuth2(Auth2 auth2)
    {
        this.auth2 = (auth2 != null) ? auth2 : DEFAULT_AUTH;
    }

    @DataBoundSetter
    public void setAddress(String address)
    {
        this.address = address;
    }

    // Getters

    @CheckForNull
    public String getDisplayName() {
        String displayName = null;

        if (this.displayName == null || this.displayName.trim().equals("")) {
            if (address != null) displayName = address;
            else displayName = null;
        } else {
            displayName = this.displayName;
        }
        return displayName;
    }

    public boolean getHasBuildTokenRootSupport() {
        return hasBuildTokenRootSupport;
    }

    public boolean isUseProxy() {
        return useProxy;
    }

    @CheckForNull
    public Auth2 getAuth2() {
        return (auth2 != null) ? auth2 : NoneAuth.INSTANCE;
    }

    @CheckForNull
    public String getAddress() {
        return address;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public boolean getTrustAllCertificates() { return trustAllCertificates; }

    public boolean getOverrideTrustAllCertificates() { return overrideTrustAllCertificates; }


    @Extension
    public static class DescriptorImpl extends Descriptor<RemoteJenkinsServer> {

        public String getDisplayName() {
            return "";
        }

        /**
         * Sets the TrustManager to be a "NaiveTrustManager", allowing us to ignore untrusted certificates
         * Will set the connection to null, if a key management error occurred.
         *
         * ATTENTION: THIS IS VERY DANGEROUS AND SHOULD ONLY BE USED IF YOU KNOW WHAT YOU DO!
         * @param conn  The HttpsURLConnection you want to modify.
         * @param trustAllCertificates  A boolean, gotten from the Remote Hosts description
         */
        public void makeConnectionTrustAllCertificates(HttpsURLConnection conn, boolean trustAllCertificates)
                throws NoSuchAlgorithmException, KeyManagementException {
            if (trustAllCertificates) {
                SSLContext ctx = SSLContext.getInstance(SSLContext.getDefault().getProtocol());
                ctx.init(new KeyManager[0], new TrustManager[]{new NaiveTrustManager()}, new SecureRandom());
                conn.setSSLSocketFactory(ctx.getSocketFactory());

                // Trust every hostname
                HostnameVerifier allHostsValid = (hostname, session) -> true;
                conn.setHostnameVerifier(allHostsValid);
            }
        }

        /**
         * Validates the given address to see that it's well-formed, and is reachable.
         *
         * @param address
         *            Remote address to be validated
         * @return FormValidation object
         */
        @Restricted(NoExternalUse.class)
        public FormValidation doCheckAddress(@QueryParameter String address, @QueryParameter boolean trustAllCertificates) {

            URL host = null;

            // no empty addresses allowed
            if (address == null || address.trim().equals("")) {
                return FormValidation.warning("The remote address can not be empty, or it must be overridden on the job configuration.");
            }

            // check if we have a valid, well-formed URL
            try {
                host = new URL(address);
                host.toURI();
            } catch (Exception e) {
                return FormValidation.error("Malformed address (" + address + "). Remember to indicate the protocol, i.e. http, https, etc.");
            }

            // check that the host is reachable
            try {
                HttpsURLConnection conn = (HttpsURLConnection) host.openConnection();
                try {
                    makeConnectionTrustAllCertificates(conn, trustAllCertificates);
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    return FormValidation.error(e, "A key management error occurred.");
                }
                conn.setConnectTimeout(5000);
                conn.connect();

                if (trustAllCertificates) {
                    return FormValidation.warning(
                            "Connection established! Accepting all certificates is potentially unsafe."
                    );
                }
            } catch (Exception e) {
                return FormValidation.warning("Address looks good, but a connection could not be established.");
            }

            return FormValidation.ok();
        }

        public static List<Auth2Descriptor> getAuth2Descriptors() {
            return Auth2.all();
        }

        public static Auth2Descriptor getDefaultAuth2Descriptor() {
            return NoneAuth.DESCRIPTOR;
        }
    }

    @Override
    public RemoteJenkinsServer clone() throws CloneNotSupportedException {
        RemoteJenkinsServer clone = (RemoteJenkinsServer)super.clone();
        clone.auth2 = (auth2 == null) ? null : auth2.clone();
        return clone;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((address == null) ? 0 : address.hashCode());
        result = prime * result + ((auth2 == null) ? 0 : auth2.hashCode());
        result = prime * result + ((displayName == null) ? 0 : displayName.hashCode());
        result = prime * result + (hasBuildTokenRootSupport ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (! (obj instanceof RemoteJenkinsServer))
            return false;
        RemoteJenkinsServer other = (RemoteJenkinsServer) obj;
        if (address == null) {
            if (other.address != null)
                return false;
        } else if (!address.equals(other.address))
            return false;
        if (auth2 == null) {
            if (other.auth2 != null)
                return false;
        } else if (!auth2.equals(other.auth2))
            return false;
        if (displayName == null) {
            if (other.displayName != null)
                return false;
        } else if (!displayName.equals(other.displayName))
            return false;
        if (hasBuildTokenRootSupport != other.hasBuildTokenRootSupport)
            return false;
        return true;
    }
}
