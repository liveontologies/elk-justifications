package org.semanticweb.elk.justifications;

import org.semanticweb.elk.proofs.InferenceSet;

public abstract class CancellableJustificationComputation<C, A>
		extends JustificationComputation<C, A> {

	public CancellableJustificationComputation(InferenceSet<C, A> inferences) {
		super(inferences);
	}

	private final int checkFrequence = 100;
	private int iterationCounter = 0;

	protected void checkCancelled() throws InterruptedException {

		if (iterationCounter++ > checkFrequence) {
			iterationCounter = 0;
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
		}

	}

}
