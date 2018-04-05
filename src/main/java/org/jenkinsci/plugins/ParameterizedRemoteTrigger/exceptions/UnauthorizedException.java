package org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions;

import java.io.IOException;
import java.net.URL;

public class UnauthorizedException extends IOException
{
    private static final long serialVersionUID = -7505703592596401545L;

    private URL url;

    public UnauthorizedException(URL url)
    {
        this.url = url;
    }

    @Override
    public String getMessage()
    {
        return "Server returned 401 - Unauthorized. Most likely there is something wrong with the provided credentials for this request: " + url;
    }

}
