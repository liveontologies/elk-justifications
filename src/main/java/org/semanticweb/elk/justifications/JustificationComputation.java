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
	 * Starts computation of justifications and notifies the provided listener
	 * about each new justification as soon as it is computed. The listener is
	 * notified about justifications in the order defined by the provided
	 * {@link Comparator}. The listener is notified exactly once for every
	 * justification. When the method returns, the listener must be notified
	 * about all the justifications.
	 * 
	 * @param conclusion
	 *            the conclusion for which to compute the justification
	 * @param order
	 *            The comparator that defines the order of justifications. The
	 *            listener is notified about new justifications in this order.
	 * @param listener
	 *            The listener that is notified about new justifications.
	 */
	void enumerateJustifications(C conclusion, Comparator<? super Set<A>> order,
			Listener<A> listener);

	public static interface Listener<A> {

		void newJustification(Set<A> justification);

		public static Listener<?> DUMMY = new Listener<Object>() {

			@Override
			public void newJustification(final Set<Object> justification) {
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
