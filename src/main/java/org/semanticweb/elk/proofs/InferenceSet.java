package org.semanticweb.elk.proofs;

/**
 * An object from which one can retrieve inferences deriving conclusions.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public interface InferenceSet<C, A> {

	/**
	 * @param conclusion
	 * @return the inferences from this inference set that derive the given
	 *         conclusion
	 */
	Iterable<Inference<C, A>> getInferences(C conclusion);

}
