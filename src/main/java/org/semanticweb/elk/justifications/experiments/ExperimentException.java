package org.semanticweb.elk.justifications.experiments;

public class ExperimentException extends Exception {
	private static final long serialVersionUID = 5206188610148701647L;

	public ExperimentException() {
		// Empty.
	}

	public ExperimentException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public ExperimentException(final String message) {
		super(message);
	}

	public ExperimentException(final Throwable cause) {
		super(cause);
	}

}
