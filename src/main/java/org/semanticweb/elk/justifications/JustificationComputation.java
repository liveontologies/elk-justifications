package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

/**
 * A common interface for procedures that compute justifications of conclusions
 * from sets of inferences. Justification is a smallest set of axioms such that
 * there is a proof of the conclusion using only inferences with justifications
 * in this set.
 * 
 * @author Yevgeny Kazakov
 * @author Peter Skocovsky
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public interface JustificationComputation<C, A> extends HasStatistics {

	/**
	 * @return the inference set used by this computation
	 */
	InferenceSet<C, A> getInferenceSet();

	/**
	 * Computes all justifications for the given conclusion. This method can be
	 * called several times for different conclusions.
	 * 
	 * @see Inference#getJustification()
	 * 
	 * @param conclusion
	 *            the conclusion for which to compute the justification
	 * @return the set consisting of all justifications for the given conclusion
	 */
	Collection<? extends Set<A>> computeJustifications(C conclusion);

	/**
	 * Starts computation of justifications and visits every justification using
	 * the provided visitor as soon as it is computed. The visitor is called
	 * exactly once for every justification. When the method returns, all
	 * justifications must be visited.
	 * 
	 * @param conclusion
	 *            the conclusion for which to compute the justification
	 * @param visitor
	 *            the visitor using which to process justifications
	 */
//	void enumerateJustifications(C conclusion,
//			Justification.Visitor<C, A, ?> visitor);

	/**
	 * Factory for creating computations
	 * 
	 * @author Yevgeny Kazakov
	 * 
	 * @param <C>
	 *            the type of conclusion and premises used by the inferences
	 * @param <A>
	 *            the type of axioms used by the inferences
	 */
	static interface Factory<C, A> {

		/**
		 * @param inferenceSet
		 * @param monitor
		 * @return a new justification computation which uses the given
		 *         inference set
		 */
		JustificationComputation<C, A> create(InferenceSet<C, A> inferenceSet,
				Monitor monitor);

		/**
		 * @return the keys of the statistics map returned by the method
		 *         {@link HasStatistics#getStatistics()} of the
		 *         {@link JustificationComputation} created by this factory.
		 */
		String[] getStatNames();

	}

}
