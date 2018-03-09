package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

/**
 * If the remote Jenkins server uses the "Prevent Cross Site Request Forgery exploits" security option,
 * a CSRF protection token must be sent in the header of the request to trigger the remote job.
 * This token is called crumb.
 *
 */
public class JenkinsCrumb
{
    String headerId;
    String crumbValue;
    boolean isEnabledOnRemote;

    /**
     * New JenkinsCrumb object indicating that CSRF is disabled in the remote Jenkins (no crumb needed).
     */
    public JenkinsCrumb()
    {
        this.headerId = null;
        this.crumbValue = null;
        this.isEnabledOnRemote = false;
    }

    /**
     * New JenkinsCrumb object with the header ID and crumb value to use in subsequent requests.
     *
     * @param headerId
     *            the header ID to be used in the subsequent requests.
     * @param crumbValue
     *            the crumb value to be used in the header of subsequent requests.
     */
    public JenkinsCrumb(String headerId, String crumbValue)
    {
        this.headerId = headerId;
        this.crumbValue = crumbValue;
        this.isEnabledOnRemote = true;
    }

    /**
     * @return the header ID to be used in the subsequent requests. Null if CSRF is disabled in the remote Jenkins.
     */
    public String getHeaderId()
    {
        return headerId;
    }

    /**
     * @return the crumb value to be used in the header of subsequent requests. Null if CSRF is disabled in the remote Jenkins.
     */
    public String getCrumbValue()
    {
        return crumbValue;
    }

    /**
     * @return true if CSRF is enabled on the remote Jenkins, false otherwise. 
     */
    public boolean isEnabledOnRemote()
    {
        return isEnabledOnRemote;
    }
}
