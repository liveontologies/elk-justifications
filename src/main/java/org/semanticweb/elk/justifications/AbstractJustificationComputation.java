package org.semanticweb.elk.justifications;

import java.util.Collection;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;

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
abstract class AbstractJustificationComputation<C, A> implements JustificationComputation<C, A> {

	private final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet_;
	
	public AbstractJustificationComputation(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet) {
		this.inferenceSet_ = inferenceSet;
	}

	public GenericInferenceSet<C, ? extends JustifiedInference<C, A>> getInferenceSet() {
		return inferenceSet_;
	}

	public Collection<? extends JustifiedInference<C, A>> getInferences(
			final C conclusion) {
		return inferenceSet_.getInferences(conclusion);
	}

}
