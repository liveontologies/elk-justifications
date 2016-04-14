package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.Set;

import org.semanticweb.elk.proofs.DelegatingInferenceSet;
import org.semanticweb.elk.proofs.InferenceSet;

public abstract class JustificationComputation<C, A>
		extends DelegatingInferenceSet<C, A> {

	public JustificationComputation(InferenceSet<C, A> inferences) {
		super(inferences);
	}

	public abstract Collection<Set<A>> computeJustifications(C conclusion)
			throws InterruptedException;

	public void logStatistics() {
		// does nothing by default
	}

}
