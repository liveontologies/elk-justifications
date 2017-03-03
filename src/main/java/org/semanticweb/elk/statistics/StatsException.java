package org.semanticweb.elk.statistics;

public class StatsException extends RuntimeException {
	private static final long serialVersionUID = 3694803038220659642L;

	public StatsException() {
		super();
	}

	public StatsException(final String message, final Throwable cause) {
		super(message, cause);
	}

	public StatsException(final String message) {
		super(message);
	}

	public StatsException(final Throwable cause) {
		super(cause);
	}

}
