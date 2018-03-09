package org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions;

import java.io.IOException;
import java.net.URL;

public class ForbiddenException extends IOException
{

    private static final long serialVersionUID = -4049611671761455585L;
    private URL url;

    public ForbiddenException(URL url)
    {
        this.url = url;
    }

    @Override
    public String getMessage()
    {
        return "Server returned 403 - Forbidden. User does not have enough permissions for this request: " + url;
    }
        
}
