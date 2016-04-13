package org.semanticweb.elk.justifications.experiments;

public abstract class Experiment {

	public Experiment(final String[] args) throws ExperimentException {
		// Empty.
	}

	public abstract int getInputSize() throws ExperimentException;

	public abstract String getInputName(int inputIndex)
			throws ExperimentException;

	public abstract int run(int inputIndex)
			throws ExperimentException, InterruptedException;
	
	public abstract void processResult(final int inputIndex)
			throws ExperimentException;
	
}
