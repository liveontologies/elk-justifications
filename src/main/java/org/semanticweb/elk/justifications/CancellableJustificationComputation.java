package org.semanticweb.elk.justifications;

import org.semanticweb.elk.proofs.InferenceSet;

public abstract class CancellableJustificationComputation<C, A>
		extends AbstractJustificationComputation<C, A> {

	protected final Monitor monitor_;

	public CancellableJustificationComputation(
			final InferenceSet<C, A> inferences, final Monitor monitor) {
		super(inferences);
		this.monitor_ = monitor;
	}

	protected boolean checkCancelled() throws InterruptedException {
		return monitor_.isCancelled();
	}

}
