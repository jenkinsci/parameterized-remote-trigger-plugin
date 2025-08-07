package org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2;

import java.io.IOException;
import java.net.URLConnection;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.Item;
import hudson.util.Secret;


public class BearerTokenAuth extends Auth2 {

    private static final long serialVersionUID = 3614172320192170597L;

    @Extension
    public static final Auth2Descriptor DESCRIPTOR = new BearerTokenAuthDescriptor();

    private Secret token;

    @DataBoundConstructor
    public BearerTokenAuth() {
        this.token = null;
    }

    @DataBoundSetter
    public void setToken(Secret token) {
        this.token = token;
    }

    public Secret getToken() {
        return this.token;
    }

    @Override
    public void setAuthorizationHeader(URLConnection connection, BuildContext context) throws IOException {
        connection.setRequestProperty("Authorization", "Bearer " + getToken().getPlainText());
    }

    @Override
    public String toString() {
        return "'" + getDescriptor().getDisplayName() + "'";
    }

    @Override
    public String toString(Item item) {
        return toString();
    }

    @Override
    public Auth2Descriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Symbol("BearerTokenAuth")
    public static class BearerTokenAuthDescriptor extends Auth2Descriptor {
        @Override
        public String getDisplayName() {
            return "Bearer Token Authentication";
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((token == null) ? 0 : token.hashCode());
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
        BearerTokenAuth other = (BearerTokenAuth) obj;
        if (token == null) {
            if (other.token == null) {
                return true;
            } else {
                return false;
            }
        }
        return token.equals(other.token);
    }
}
