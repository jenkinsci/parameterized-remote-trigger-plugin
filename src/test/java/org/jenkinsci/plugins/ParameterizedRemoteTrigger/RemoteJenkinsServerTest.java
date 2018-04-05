package org.jenkinsci.plugins.ParameterizedRemoteTrigger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.CredentialsAuth;
import org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2.TokenAuth;
import org.junit.Test;


public class RemoteJenkinsServerTest {

    private final static String TOKEN = "myToken";
    private final static String USER = "myUser";
    private final static String ADDRESS = "http://www.example.org:8443";
    private final static String DISPLAY_NAME = "My example server.";
    private final static boolean HAS_BUILD_TOKEN_ROOT_SUPPORT = true;

    @Test
    public void testCloneBehaviour() throws Exception {
        TokenAuth auth = new TokenAuth();
        auth.setApiToken(TOKEN);
        auth.setUserName(USER);

        RemoteJenkinsServer server = new RemoteJenkinsServer();
        server.setAddress(ADDRESS);
        server.setDisplayName(DISPLAY_NAME);
        server.setAuth2(auth);
        server.setHasBuildTokenRootSupport(HAS_BUILD_TOKEN_ROOT_SUPPORT);

        RemoteJenkinsServer clone = server.clone();

        //Test if still equal after cloning
        verifyEqualsHashCode(server, clone);
        assertEquals("address", ADDRESS, clone.getAddress());
        assertEquals("address", server.getAddress(), clone.getAddress());
        assertEquals("remoteAddress", ADDRESS, clone.getRemoteAddress());
        assertEquals("remoteAddress", server.getRemoteAddress(), clone.getRemoteAddress());
        assertEquals("auth2", server.getAuth2(), clone.getAuth2());
        assertEquals("displayName", DISPLAY_NAME, clone.getDisplayName());
        assertEquals("displayName", server.getDisplayName(), clone.getDisplayName());
        assertEquals("hasBuildTokenRootSupport", HAS_BUILD_TOKEN_ROOT_SUPPORT, clone.getHasBuildTokenRootSupport());
        assertEquals("hasBuildTokenRootSupport", server.getHasBuildTokenRootSupport(), clone.getHasBuildTokenRootSupport());

        //Test if original object affected by clone modifications
        clone.setAddress("http://www.changed.org:8443");
        clone.setDisplayName("Changed");
        clone.setHasBuildTokenRootSupport(false);
        verifyEqualsHashCode(server, clone, false);
        assertEquals("address", ADDRESS, server.getAddress());
        assertEquals("displayName", DISPLAY_NAME, server.getDisplayName());
        assertEquals("hasBuildTokenRootSupport", HAS_BUILD_TOKEN_ROOT_SUPPORT, server.getHasBuildTokenRootSupport());

        //Test if clone is deep-copy or if server fields can be modified
        TokenAuth cloneAuth = (TokenAuth)clone.getAuth2();
        assertNotNull(cloneAuth);
        cloneAuth.setApiToken("changed");
        cloneAuth.setUserName("changed");
        TokenAuth serverAuth = (TokenAuth)server.getAuth2();
        assertNotNull(serverAuth);
        assertEquals("auth.apiToken", TOKEN, serverAuth.getApiToken());
        assertEquals("auth.userName", USER, serverAuth.getUserName());

        //Test if clone.setAuth() affects original object
        CredentialsAuth credAuth = new CredentialsAuth();
        clone.setAuth2(credAuth);
        assertEquals("auth", auth, server.getAuth2());
    }

    private void verifyEqualsHashCode(RemoteJenkinsServer server, RemoteJenkinsServer clone) throws CloneNotSupportedException {
        verifyEqualsHashCode(server, clone, true);
    }

    private void verifyEqualsHashCode(RemoteJenkinsServer server, RemoteJenkinsServer clone, boolean expectToBeSame) throws CloneNotSupportedException {
        assertNotEquals("Still same object after clone", System.identityHashCode(server), System.identityHashCode(clone));
        if(expectToBeSame) {
            assertTrue("clone not equals() server", clone.equals(server));
            assertEquals("clone has different hashCode() than server", server.hashCode(), clone.hashCode());
        } else {
            assertFalse("clone still equals() server", clone.equals(server));
            assertNotEquals("clone still has same hashCode() than server", server.hashCode(), clone.hashCode());
        }
    }

}
