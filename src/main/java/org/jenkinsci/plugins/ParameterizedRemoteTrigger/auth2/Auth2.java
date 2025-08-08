package org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLConnection;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;

import hudson.DescriptorExtensionList;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import jenkins.model.Jenkins;

public abstract class Auth2 extends AbstractDescribableImpl<Auth2> implements Serializable, Cloneable {

    private static final long serialVersionUID = -3217381962636283564L;

    private static final DescriptorExtensionList<Auth2, Auth2Descriptor> ALL = DescriptorExtensionList
        .createDescriptorList(Jenkins.getInstance(), Auth2.class);

    public static DescriptorExtensionList<Auth2, Auth2Descriptor> all()
    {
        return ALL;
    }

    public static abstract class Auth2Descriptor extends Descriptor<Auth2>
    {
    }

    /**
     * Depending on the purpose the Auth2 implementation has to override the
     * <code>Authorization</code> header of the connection appropriately. It might also ignore this
     * step or remove an existing <code>Authorization</code> header.
     *
     * @param connection
     *            connection between the application and the remote server.
     * @param context
     *            the context of this Builder/BuildStep.
     * @throws IOException
     *            if there is an error generating the authorization header.
     */
    public abstract void setAuthorizationHeader(URLConnection connection, BuildContext context) throws IOException;

    /**
     * Whether a Jenkins crumb is required on {@code POST} requests.
     */
    public boolean requiresCrumb() {
        return true;
    }

    public abstract String toString();

    /**
     * Returns a string representing the authorization.
     *
     * @param item
     *            the Item (Job, Pipeline,...) we are currently running in.
     *            The item is required to also get Credentials which are defined in the items scope and not Jenkins globally.
     *            Value can be null, but Credentials e.g. configured on a Folder will not be found in this case,
     *            only globally configured Credentials.
     * @return a string representing the authorization.
     */
    public abstract String toString(Item item);


    @Override
    public Auth2 clone() throws CloneNotSupportedException {
        return (Auth2)super.clone();
    };

}
