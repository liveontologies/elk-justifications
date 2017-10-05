package org.liveontologies.pinpointing.experiments;

import org.liveontologies.puli.pinpointing.InterruptMonitor;

public interface JustificationExperiment {

	void init(String[] args) throws ExperimentException;

	void before(String query) throws ExperimentException;

	void run(InterruptMonitor monitor) throws ExperimentException;

	void after() throws ExperimentException;

	void dispose();

	void addJustificationListener(Listener listener);

	void removeJustificationListener(Listener listener);

	public static interface Listener {
		void newJustification();
	}

}
