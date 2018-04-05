package org.jenkinsci.plugins.ParameterizedRemoteTrigger.utils;

import static org.apache.commons.lang.StringUtils.isEmpty;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.annotation.Nonnull;

import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.BuildContext;

public class Base64Utils
{

    public static final String AUTHTYPE_BASIC = "Basic";

    public static String encode(String input) throws UnsupportedEncodingException
    {
        byte[] encoded = Base64.encodeBase64(input.getBytes("UTF-8"));
        return new String(encoded, "UTF-8");
    }

    /**
     * Creates the value for an <code>Authorization</code> header consisting of: <br>
     * "authType base64Encoded(user:password)"<br>
     * e.g. "Basic zhwef9tz33ergwerg4394zh370345zh=="
     *
     * @param authType
     *           the authorization type.
     * @param user
     *           the user name.
     * @param password
     *           the user password.
     * @param context
     *            the context of this Builder/BuildStep.
     * @return the base64 encoded authorization.
     * @throws IOException
     *            if there is a failure while replacing token macros, or
     *            if there is a failure while encoding user:password.
     */
    @Nonnull
    public static String generateAuthorizationHeaderValue(String authType, String user, String password,
                BuildContext context) throws IOException
    {
        if (isEmpty(user)) throw new IllegalArgumentException("user null or empty");
        if (password == null) throw new IllegalArgumentException("password null"); // is empty password allowed for Basic Auth?
        String authTypeKey = getAuthType(authType);
        String tuple = user + ":" + password;
        tuple = TokenMacroUtils.applyTokenMacroReplacements(tuple, context);
        String encodedTuple = Base64Utils.encode(tuple);
        return authTypeKey + " " + encodedTuple;
    }

    @Nonnull
    private static String getAuthType(String authType)
    {
        if ("Basic".equalsIgnoreCase(authType)) return "Basic";
        throw new IllegalArgumentException("AuthType wrong or not supported yet: " + authType);
    }

}
