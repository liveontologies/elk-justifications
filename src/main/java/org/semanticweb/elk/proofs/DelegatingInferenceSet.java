package org.semanticweb.elk.proofs;

/**
 * An {@link InferenceSet} that delegates all calls to another
 * {@link InferenceSet}. Useful as a prototype for other classes.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class DelegatingInferenceSet<C, A> implements InferenceSet<C, A> {

	private final InferenceSet<C, A> inferences_;

	public DelegatingInferenceSet(InferenceSet<C, A> inferences) {
		this.inferences_ = inferences;
	}

	@Override
	public Iterable<Inference<C, A>> getInferences(C conclusion) {
		return inferences_.getInferences(conclusion);
	}

	public InferenceSet<C, A> getDelegate() {
		return inferences_;
	}

}
