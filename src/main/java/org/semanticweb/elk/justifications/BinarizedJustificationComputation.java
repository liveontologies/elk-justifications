package org.semanticweb.elk.justifications;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.AbstractJustificationComputation;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.liveontologies.puli.statistics.NestedStats;
import org.semanticweb.elk.proofs.adapters.InferenceSets;

/**
 * The {@link BottomUpJustificationComputation} applied to the binarization of
 * the input inference set.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class BinarizedJustificationComputation<C, A>
		extends AbstractJustificationComputation<C, A> {

	private final JustificationComputation<List<C>, A> computaiton_;

	BinarizedJustificationComputation(
			JustificationComputation.Factory<List<C>, A> mainFactory,
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
		computaiton_ = mainFactory.create(InferenceSets.binarize(inferenceSet),
				InferenceSets.binarize(justifier), monitor);
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			final Listener<A> listener) {
		computaiton_.enumerateJustifications(
				Collections.singletonList(conclusion), order, listener);
	}

	@NestedStats
	public JustificationComputation<List<C>, A> getDelegate() {
		return computaiton_;
	}

	public static <C, A> JustificationComputation.Factory<C, A> getFactory(
			JustificationComputation.Factory<List<C>, A> mainFactory) {
		return new Factory<C, A>(mainFactory);
	}

	private static class Factory<C, A>
			implements JustificationComputation.Factory<C, A> {

		JustificationComputation.Factory<List<C>, A> mainFactory_;

		Factory(JustificationComputation.Factory<List<C>, A> mainFactory) {
			this.mainFactory_ = mainFactory;
		}

		@Override
		public JustificationComputation<C, A> create(
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new BinarizedJustificationComputation<C, A>(mainFactory_,
					inferenceSet, justifier, monitor);
		}

	}

}
