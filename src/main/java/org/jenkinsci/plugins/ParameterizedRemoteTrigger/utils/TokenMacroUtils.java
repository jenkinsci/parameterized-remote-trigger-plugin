package org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;

public class TokenMacroUtils
{

    public static String applyTokenMacroReplacements(String input, BuildContext context) throws IOException
    {
        try {
            if (isUseTokenMacro(context)) {
                return TokenMacro.expandAll(context.run, context.workspace, context.listener, input);
            }
        }
        catch (MacroEvaluationException e) {
            throw new IOException(e);
        }
        catch (InterruptedException e) {
            throw new IOException(e);
        }
        return input;
    }

    public static List<String> applyTokenMacroReplacements(List<String> inputs, BuildContext context) throws IOException
    {
        List<String> outputs = new ArrayList<String>();
        for (String input : inputs) {
            outputs.add(applyTokenMacroReplacements(input, context));
        }
        return outputs;
    }

    public static boolean isUseTokenMacro(BuildContext context)
    {
        return context != null && context.run != null && context.workspace != null && context.listener != null;
    }

}
