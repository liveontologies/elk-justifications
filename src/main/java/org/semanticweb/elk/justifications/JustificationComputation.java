package org.semanticweb.elk.justifications;

import java.util.Comparator;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;

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
public interface JustificationComputation<C, A> {

	/**
	 * Starts computation of justifications and visits every justification using
	 * the provided visitor as soon as it is computed. The justifications are
	 * visited in the order defined by the provided {@link Comparator} The
	 * visitor is called exactly once for every justification. When the method
	 * returns, all justifications must be visited.
	 * 
	 * @param conclusion
	 *            the conclusion for which to compute the justification
	 * @param order
	 *            the comparator that defines the order in which the
	 *            justifications are visited
	 * @param visitor
	 *            the visitor using which to process justifications
	 */
	void enumerateJustifications(C conclusion, Comparator<? super Set<A>> order,
			JustificationVisitor<A> visitor);

	public static interface JustificationVisitor<A> {

		void visit(Set<A> justification);

		public static JustificationVisitor<?> DUMMY = new JustificationVisitor<Object>() {

			@Override
			public void visit(final Set<Object> justification) {
				// Empty.
			}

		};

	}

	public static Comparator<? super Set<?>> DEFAULT_ORDER = new Comparator<Set<?>>() {
		@Override
		public int compare(final Set<?> just1, final Set<?> just2) {
			return Integer.compare(just1.size(), just2.size());
		}
	};

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
		JustificationComputation<C, A> create(
				GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
				Monitor monitor);

	}

}
