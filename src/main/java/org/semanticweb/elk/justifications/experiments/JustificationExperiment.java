package org.semanticweb.elk.justifications.experiments;

import org.liveontologies.puli.justifications.InterruptMonitor;

public interface JustificationExperiment {

	void init(String[] args) throws ExperimentException;

	String before(String query) throws ExperimentException;

	void run(InterruptMonitor monitor) throws ExperimentException;

	void after() throws ExperimentException;

	void dispose();

	int getJustificationCount();

	void addJustificationListener(Listener listener);

	void removeJustificationListener(Listener listener);

	public static interface Listener {
		void newJustification();
	}

}
