package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.sf.json.JSONObject;

/**
 * Http response containing header, body (JSON format) and response code.
 *
 */
public class ConnectionResponse
{
    @Nonnull
    private final Map<String,List<String>> header;

    @Nullable
    private final JSONObject body;

    @Nonnull
    private final int responseCode;


    public ConnectionResponse(@Nonnull Map<String, List<String>> header, @Nullable JSONObject body, @Nonnull int responseCode)
    {
        this.header = header;
        this.body = body;
        this.responseCode = responseCode;
    }

    public ConnectionResponse(@Nonnull Map<String, List<String>> header, @Nonnull int responseCode)
    {
        this.header = header;
        this.body = null;
        this.responseCode = responseCode;
    }

    public Map<String,List<String>> getHeader()
    {
        return header;
    }

    public JSONObject getBody() {
        return body;
    }

    public int getResponseCode() {
        return responseCode;
    }

}
