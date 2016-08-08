package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import java.util.List;
import java.util.Map;

import net.sf.json.JSONObject;

/**
 * Class to store a connection response
 *
 * @author Alejandra Ferreiro Vidal
 */
public class ConnectionResponse
{
  private final Map<String,List<String>> response_header;
  private final JSONObject response_object;

  public ConnectionResponse(Map<String,List<String>> header, JSONObject response)
  {
    if (header == null || header.isEmpty())
      throw new RuntimeException("There was no response-header in connection-response.");

    this.response_header = header;
    this.response_object = response;
  }

  public Map<String,List<String>> getResponseHeader()
  {
    return response_header;
  }

  public JSONObject getResponseObject()
  {
    return response_object;
  }

  public String getLocation()
  {
    if (!response_header.containsKey("Location")) return null;
    return response_header.get("Location").get(0);
  }
}
