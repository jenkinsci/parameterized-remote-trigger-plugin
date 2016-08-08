package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import net.sf.json.JSONObject;
import hudson.AbortException;

/**
 * Class to store the queue response
 *
 * @author Alejandra Ferreiro Vidal
 */
public class RemoteJobInfo
{
  private JSONObject queueResponse;

  public RemoteJobInfo(JSONObject queueResponse)
  {
    if (queueResponse == null)
      throw new RuntimeException("No queue-response.");

    this.queueResponse = queueResponse;
  }

  public boolean isBlocked()
  {
    return queueResponse.getBoolean("blocked");
  }

  public boolean isBuildable()
  {
    return queueResponse.getBoolean("buildable");
  }

  public boolean isPending()
  {
    return getOptionalBoolean("pending");
  }

  public boolean isCancelled()
  {
    return getOptionalBoolean("cancelled");
  }

  public String getWhy()
  {
    return queueResponse.getString("why");
  }

  public boolean isExecutable()
  {
    return (!isBlocked() && !isBuildable() && !isPending() && !isCancelled());
  }

  public RemoteJob getRemoteJob() throws AbortException
  {
    if (isExecutable()) {
      JSONObject remoteJobInfo = queueResponse.getJSONObject("executable");
      if (remoteJobInfo.isNullObject())
        throw new AbortException("The attribute \"executable\" was not found. Unexpected response: " + queueResponse.toString());
      return new RemoteJob(remoteJobInfo.getInt("number"), remoteJobInfo.getString("url"));
    }
    else {
      return null;
    }
  }

  private boolean getOptionalBoolean(String attribute)
  {
    if (queueResponse.containsKey(attribute))
      return queueResponse.getBoolean(attribute);
    else return false;
  }
}