package org.semanticweb.elk.justifications.experiments;

import org.semanticweb.elk.justifications.Monitor;

public abstract class JustificationExperiment {

	protected volatile int justCount;

	public JustificationExperiment(final String[] args)
			throws ExperimentException {
		// Empty.
	}

	public void init() {
		justCount = 0;
	}

	public abstract void run(String query, Monitor monitor)
			throws ExperimentException;

	public int getJustificationCount() {
		return justCount;
	}

}
