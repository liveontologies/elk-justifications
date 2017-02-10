package org.semanticweb.elk.justifications;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;

public abstract class CancellableJustificationComputation<C, A>
		extends AbstractJustificationComputation<C, A> {

	protected final Monitor monitor_;

	public CancellableJustificationComputation(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final Monitor monitor) {
		super(inferences);
		this.monitor_ = monitor;
	}

	protected boolean checkCancelled() throws InterruptedException {
		return monitor_.isCancelled();
	}

}
