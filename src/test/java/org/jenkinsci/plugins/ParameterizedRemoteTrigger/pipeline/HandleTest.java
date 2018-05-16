package org.jenkinsci.plugins.ParameterizedRemoteTrigger.pipeline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HandleTest
{

  @Test
  public void testHelp() {
    String help = Handle.help();
    //Check only a few to see if it works in general
    assertContains(help, true, "- String toString()");
    assertContains(help, true, "- RemoteBuildInfo getBuildInfo()");
    assertContains(help, true, "- RemoteBuildStatus getBuildStatus()");
    assertContains(help, true, "- Result getBuildResult()");
    assertContains(help, true, "- URL getBuildUrl()");
    assertContains(help, true, "- int getBuildNumber()");
    assertContains(help, true, "- boolean isFinished()");
    assertContains(help, false, " set");
  }

  private void assertContains(String help, boolean assertIsContained, String checkString)
  {
    if(assertIsContained)
      assertTrue("Help does not contain '" + checkString + "': \"" + help + "\"", help.contains(checkString));
    else
      assertFalse("Help contains '" + checkString + "': \"" + help + "\"", help.contains(checkString));
  }


}
