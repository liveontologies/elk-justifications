package org.semanticweb.elk.justifications.experiments;

import org.semanticweb.elk.justifications.Monitor;

public abstract class JustificationExperiment {

	public JustificationExperiment(final String[] args)
			throws ExperimentException {
		// Empty.
	}

	public abstract void init();

	public abstract void run(String query, Monitor monitor)
			throws ExperimentException;

	public abstract int getJustificationCount();

}
