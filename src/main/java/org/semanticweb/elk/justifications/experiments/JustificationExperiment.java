package org.semanticweb.elk.justifications.experiments;

import org.liveontologies.puli.justifications.InterruptMonitor;

public abstract class JustificationExperiment {

	public JustificationExperiment(final String[] args)
			throws ExperimentException {
		// Empty.
	}

	public abstract void init();

	public abstract void run(String query, InterruptMonitor monitor)
			throws ExperimentException;

	public abstract int getJustificationCount();

}
