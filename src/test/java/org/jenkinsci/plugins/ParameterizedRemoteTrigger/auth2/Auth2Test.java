package org.jenkinsci.plugins.ParameterizedRemoteTrigger.auth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Auth2Test {

	@Test
	public void testCredentialsAuthCloneBehaviour() throws CloneNotSupportedException {
		CredentialsAuth original = new CredentialsAuth();
		original.setCredentials("original");
		CredentialsAuth clone = original.clone();
		verifyEqualsHashCode(original, clone);
		
		//Test changing clone
		clone.setCredentials("changed");
		verifyEqualsHashCode(original, clone, false);
		assertEquals("original", original.getCredentials());
		assertEquals("changed", clone.getCredentials());
	}

	@Test
	public void testTokenAuthCloneBehaviour() throws CloneNotSupportedException {
		TokenAuth original = new TokenAuth();
		original.setApiToken("original");
		original.setUserName("original");
		TokenAuth clone = original.clone();
		verifyEqualsHashCode(original, clone);
		
		//Test changing clone
		clone.setApiToken("changed");
		clone.setUserName("changed");
		verifyEqualsHashCode(original, clone, false);
		assertEquals("original", original.getApiToken());
		assertEquals("original", original.getUserName());
		assertEquals("changed", clone.getApiToken());
		assertEquals("changed", clone.getUserName());
	}

	@Test
	public void testNullAuthCloneBehaviour() throws CloneNotSupportedException {
		NullAuth original = new NullAuth();
		NullAuth clone = original.clone();
		verifyEqualsHashCode(original, clone);
	}

	@Test
	public void testNoneAuthCloneBehaviour() throws CloneNotSupportedException {
		NoneAuth original = new NoneAuth();
		NoneAuth clone = original.clone();
		verifyEqualsHashCode(original, clone);
	}

	private void verifyEqualsHashCode(Auth2 original, Auth2 clone) throws CloneNotSupportedException {
		verifyEqualsHashCode(original, clone, true);
	}

	private void verifyEqualsHashCode(Auth2 original, Auth2 clone, boolean expectToBeSame) throws CloneNotSupportedException {
        assertNotEquals("Still same object after clone", System.identityHashCode(original), System.identityHashCode(clone));
        if(expectToBeSame) {
    		assertTrue("clone not equals() original", clone.equals(original));
            assertEquals("clone has different hashCode() than original", original.hashCode(), clone.hashCode());
        } else {
    		assertFalse("clone still equals() original", clone.equals(original));
            assertNotEquals("clone still has same hashCode() than original", original.hashCode(), clone.hashCode());
        }
	}
}
