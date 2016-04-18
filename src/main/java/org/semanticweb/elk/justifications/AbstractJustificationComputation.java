package org.semanticweb.elk.justifications;

import org.semanticweb.elk.proofs.DelegatingInferenceSet;
import org.semanticweb.elk.proofs.InferenceSet;

/**
 * A skeleton implementation of {@link JustificationComputation}
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
abstract class AbstractJustificationComputation<C, A> extends
		DelegatingInferenceSet<C, A> implements JustificationComputation<C, A> {

	public AbstractJustificationComputation(InferenceSet<C, A> inferences) {
		super(inferences);
	}

	@Override
	public InferenceSet<C, A> getInferenceSet() {
		return getDelegate();
	}

	@Override
	public void logStatistics() {
		// does nothing by default
	}

	@Override
	public void resetStatistics() {
		// does nothing by default
	}

}
