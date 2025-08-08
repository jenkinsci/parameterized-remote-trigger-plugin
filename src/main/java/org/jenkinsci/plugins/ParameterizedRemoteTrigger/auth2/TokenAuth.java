package org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2;

import static org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.Base64Utils.AUTHTYPE_BASIC;

import java.io.IOException;
import java.net.URLConnection;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils.Base64Utils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.Item;
import hudson.util.Secret;

public class TokenAuth extends Auth2 {

    private static final long serialVersionUID = 7912089565969112023L;

    @Extension
    public static final Auth2Descriptor DESCRIPTOR = new TokenAuthDescriptor();

    private String userName;
    private Secret apiToken;

    @DataBoundConstructor
    public TokenAuth() {
        this.userName = null;
        this.apiToken = null;
    }

    @DataBoundSetter
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return this.userName;
    }

    @DataBoundSetter
    public void setApiToken(Secret apiToken) {
        this.apiToken = apiToken;
    }

    public Secret getApiToken() {
        return this.apiToken;
    }

    @Override
    public void setAuthorizationHeader(URLConnection connection, BuildContext context) throws IOException {
        String authHeaderValue = Base64Utils.generateAuthorizationHeaderValue(AUTHTYPE_BASIC, getUserName(), getApiToken().getPlainText(), context, true);
        connection.setRequestProperty("Authorization", authHeaderValue);
    }

    @Override
    public boolean requiresCrumb() {
        return false;
    }

    @Override
    public String toString() {
        return "'" + getDescriptor().getDisplayName() + "' as user '" + getUserName() + "'";
    }

    @Override
    public String toString(Item item) {
        return toString();
    }

    @Override
    public Auth2Descriptor getDescriptor() {
        return DESCRIPTOR;
    }

    @Symbol("TokenAuth")
    public static class TokenAuthDescriptor extends Auth2Descriptor {
        @Override
        public String getDisplayName() {
            return "Token Authentication";
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((apiToken == null) ? 0 : apiToken.hashCode());
        result = prime * result + ((userName == null) ? 0 : userName.hashCode());
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
        TokenAuth other = (TokenAuth) obj;
        if (apiToken == null) {
            if (other.apiToken != null)
                return false;
        } else if (!apiToken.equals(other.apiToken)) {
            return false;
        }
        if (userName == null) {
            if (other.userName != null)
                return false;
        } else if (!userName.equals(other.userName)) {
            return false;
        }
        return true;
    }

}
