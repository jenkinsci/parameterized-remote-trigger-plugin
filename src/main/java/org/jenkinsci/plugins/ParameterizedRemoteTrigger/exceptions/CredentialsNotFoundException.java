package org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions;

import java.io.IOException;

public class CredentialsNotFoundException extends IOException
{

    private static final long serialVersionUID = -2489306184948013529L;
    private String credentialsId;

    public CredentialsNotFoundException(String credentialsId)
    {
        this.credentialsId = credentialsId;
    }

    @Override
    public String getMessage()
    {
        return "No Jenkins Credentials found with ID '" + credentialsId + "'";
    }

}
