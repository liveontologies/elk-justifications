package org.semanticweb.elk.justifications.experiments;

public abstract class Experiment {

	public Experiment(final String[] args) throws ExperimentException {
		// Empty.
	}

	public abstract void init() throws ExperimentException;
	public abstract boolean hasNext();

	public abstract Record run() throws ExperimentException, InterruptedException;

	public abstract String getInputName() throws ExperimentException;
	
	public abstract void processResult() throws ExperimentException;
	
}
