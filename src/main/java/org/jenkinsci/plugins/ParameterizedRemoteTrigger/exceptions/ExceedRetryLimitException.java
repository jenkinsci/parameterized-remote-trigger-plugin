package org.jenkinsci.plugins.ParameterizedRemoteTrigger.exceptions;

import java.io.IOException;

public class ExceedRetryLimitException extends IOException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7817258508279153509L;

	@Override
	public String getMessage() {
		return "Max number of connection retries have been exceeded.";
	}

}
