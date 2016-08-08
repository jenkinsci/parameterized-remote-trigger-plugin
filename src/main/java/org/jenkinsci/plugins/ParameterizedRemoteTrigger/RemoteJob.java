package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

/**
 * Class to store remote job data
 *
 * @author Alejandra Ferreiro Vidal
 */
public class RemoteJob
{
  final private int jobNumber;
  final private String jobURL;

  public RemoteJob(int jobNumber, String jobURL)
  {
    if (jobNumber < 1)
      throw new RuntimeException("Remote job build number was not found.");

    if (jobURL == null || jobURL.equals(""))
      throw new RuntimeException("Remote job url was not found.");

    this.jobNumber = jobNumber;
    this.jobURL = jobURL;
  }

  public int getBuildNumber()
  {
    return jobNumber;
  }

  public String getURL()
  {
    return jobURL;
  }

}
