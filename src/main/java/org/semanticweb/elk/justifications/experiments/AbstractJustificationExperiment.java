package org.semanticweb.elk.justifications.experiments;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractJustificationExperiment
		implements JustificationExperiment {

	private final List<Listener> listeners_ = new ArrayList<>();

	@Override
	public void addJustificationListener(final Listener listener) {
		listeners_.add(listener);
	}

	@Override
	public void removeJustificationListener(final Listener listener) {
		listeners_.remove(listener);
	}

	protected void fireNewJustification() {
		for (final Listener listener : listeners_) {
			listener.newJustification();
		}
	}

}
