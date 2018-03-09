package org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.jvnet.hudson.test.WithoutJenkins;

public class FormValidationUtilsTest
{

    @Test @WithoutJenkins
    public void testIsUrl() {
        assertEquals(true, FormValidationUtils.isURL("http://xyz"));
        assertEquals(true, FormValidationUtils.isURL("https://xyz"));
        assertEquals(true, FormValidationUtils.isURL("https://xyz:1234/test"));
        assertEquals(false, FormValidationUtils.isURL("xyz"));
        assertEquals(false, FormValidationUtils.isURL(""));
        assertEquals(false, FormValidationUtils.isURL(null));
        assertEquals(false, FormValidationUtils.isURL("http://"));
        assertEquals(false, FormValidationUtils.isURL("https://"));
        assertEquals(false, FormValidationUtils.isURL("  http://xyz  "));
        assertEquals(false, FormValidationUtils.isURL("http://xyz/$jobPath"));
        assertEquals(false, FormValidationUtils.isURL("http://xyz/${jobPath}"));
    }

}
