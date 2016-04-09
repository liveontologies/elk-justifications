package org.semanticweb.elk.justifications;

import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;

public abstract class CancellableJustificationComputation
		extends JustificationComputation {

	public CancellableJustificationComputation(
			final ExplainingOWLReasoner reasoner) {
		super(reasoner);
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
