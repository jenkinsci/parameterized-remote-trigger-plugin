package org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils;

public class StringTools
{
    
    /**
     * System specific new line character/string
     */
    public static final String NL = getSystemLineSeparator();

    /**
     * Unix/Linux specific new line character '\n'
     */
    public static final String NL_UNIX = "\n";

    private static String getSystemLineSeparator() {
        String newLine = System.getProperty("line.separator");
        if(newLine == null || newLine.length() <= 0) newLine = "\n";
        return newLine;
    }

}
