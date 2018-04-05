package org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2;

import java.io.IOException;
import java.net.URLConnection;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Item;

public class NullAuth extends Auth2 {

    private static final long serialVersionUID = -1209658644855942360L;

    @Extension
    public static final Auth2Descriptor DESCRIPTOR = new NullAuthDescriptor();

    public static final NullAuth INSTANCE = new NullAuth();

    @DataBoundConstructor
    public NullAuth() {
    }

    @Override
    public void setAuthorizationHeader(URLConnection connection, BuildContext context) throws IOException {
        //Ignore
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

    @Symbol("NullAuth")
    public static class NullAuthDescriptor extends Auth2Descriptor {
        @Override
        public String getDisplayName() {
            return "Don't Set/Override";
        }
    }

    @Override
    public int hashCode() {
        return "NullAuth".hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this.getClass().isInstance(obj);
    }

}