package org.semanticweb.elk.justifications.experiments;

import org.liveontologies.puli.justifications.InterruptMonitor;

public interface JustificationExperiment {

	void init(String[] args) throws ExperimentException;

	void before() throws ExperimentException;

	void run(String query, InterruptMonitor monitor) throws ExperimentException;

	void after() throws ExperimentException;

	void dispose();

	int getJustificationCount();

}
