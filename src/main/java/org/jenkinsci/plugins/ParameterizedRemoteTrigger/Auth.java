package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.Auth2;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.CredentialsAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NoneAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.NullAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.TokenAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions.CredentialsNotFoundException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.Stapler;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;

/**
 * We need to keep this for compatibility - old config deserialization!
 * @deprecated since 2.3.0-SNAPSHOT - use {@link Auth2} instead.
 */
public class Auth extends AbstractDescribableImpl<Auth> implements Serializable {

    private static final long serialVersionUID = 5110932168554914718L;

    public static final String  NONE               = "none";
    public static final String  API_TOKEN          = "apiToken";
    public static final String  CREDENTIALS_PLUGIN = "credentialsPlugin";

    private final String authType;
    private final String username;
    private final String apiToken;
    private final String creds;

    @DataBoundConstructor
    public Auth(String authType, String username, String apiToken, String creds) {
        this.authType = authType;
        this.username = username;
        this.apiToken = apiToken;
        this.creds = creds;
    }

    public String getAuthType() {
        return authType;
    }

    public String getUsername() {
        return username;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getCreds() {
        return creds;
    }

    public Boolean isMatch(String value) {
        return authType.equals(value);
    }

    public String getUser(){
        if (authType.equals(API_TOKEN)){
            return username;
        } else if (authType.equals(CREDENTIALS_PLUGIN)){
            UsernamePasswordCredentials creds = getCredentials();
            return creds != null ? creds.getUsername() : "";
        } else {
            return "";
        }
    }

    public String getPassword(){
        if (authType.equals(API_TOKEN)){
            return apiToken;
        } else if (authType.equals(CREDENTIALS_PLUGIN)){
            UsernamePasswordCredentials creds = getCredentials();
            return creds != null ? creds.getPassword().getPlainText() : "";
        } else {
            return "";
        }
    }

    /**
     * Looks up the credentialsID attached to this object in the Global Credentials plugin datastore
     * @return the matched credentials
     */
    private UsernamePasswordCredentials getCredentials() {
        Item item = null;

        List<StandardUsernameCredentials> listOfCredentials = CredentialsProvider.lookupCredentials(
                StandardUsernameCredentials.class, item, ACL.SYSTEM, Collections.<DomainRequirement> emptyList());

        return (UsernamePasswordCredentials) findCredential(creds, listOfCredentials);
    }

    private StandardUsernameCredentials findCredential(String credetialId, List<StandardUsernameCredentials> listOfCredentials){
        for (StandardUsernameCredentials cred : listOfCredentials) {
            if (credetialId.equals(cred.getId())) {
                return cred;
            }
        }
        return null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * We need to keep this for compatibility - old config deserialization!
     * @deprecated since 2.3.0-SNAPSHOT - use {@link Auth2} instead.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<Auth> {
        @Override
        public String getDisplayName() {
            return "";
        }

        public static ListBoxModel doFillCredsItems() {
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

    public static Auth auth2ToAuth(Auth2 auth) {
        if (auth == null)
            return null;
        if (auth instanceof NoneAuth) {
            return new Auth(Auth.NONE, null, null, null);
        } else if (auth instanceof TokenAuth) {
            TokenAuth tokenAuth = (TokenAuth) auth;
            return new Auth(Auth.API_TOKEN, tokenAuth.getUserName(), tokenAuth.getApiToken().getPlainText(), null);
        } else if (auth instanceof CredentialsAuth) {
            CredentialsAuth credAuth = (CredentialsAuth) auth;
            try {
                String credUser = credAuth.getUserName(null);
                String credPass = credAuth.getPassword(null);
                return new Auth(Auth.CREDENTIALS_PLUGIN, credUser, credPass, credAuth.getCredentials());
            }
            catch (CredentialsNotFoundException e) {
                return new Auth(Auth.CREDENTIALS_PLUGIN, "", "", credAuth.getCredentials());
            }
        } else {
            return null;
        }
    }

    public static Auth2 authToAuth2(List<Auth> oldAuth) {
        if(oldAuth == null || oldAuth.size() <= 0) return NullAuth.INSTANCE;
        return authToAuth2(oldAuth.get(0));
    }

    public static Auth2 authToAuth2(Auth oldAuth) {
        String authType = oldAuth.getAuthType();
        if (Auth.NONE.equals(authType)) {
            return NoneAuth.INSTANCE;
        } else if (Auth.API_TOKEN.equals(authType)) {
            TokenAuth newAuth = new TokenAuth();
            newAuth.setUserName(oldAuth.getUsername());
            newAuth.setApiToken(Secret.fromString(oldAuth.getApiToken()));
            return newAuth;
        } else if (Auth.CREDENTIALS_PLUGIN.equals(authType)) {
            CredentialsAuth newAuth = new CredentialsAuth();
            newAuth.setCredentials(oldAuth.getCreds());
            return newAuth;
        } else {
            return NullAuth.INSTANCE;
        }
    }
}
