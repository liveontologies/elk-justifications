package org.semanticweb.elk.justifications.experiments;

import org.semanticweb.elk.justifications.HasStatistics;
import org.semanticweb.elk.justifications.Monitor;

public abstract class Experiment implements HasStatistics {

	public Experiment(final String[] args) throws ExperimentException {
		// Empty.
	}

	public abstract void init() throws ExperimentException;
	
	public abstract boolean hasNext();
	
	public abstract Record run(final Monitor monitor)
					throws ExperimentException;

	public abstract String getInputName() throws ExperimentException;
	
	public abstract void processResult() throws ExperimentException;
	
}
