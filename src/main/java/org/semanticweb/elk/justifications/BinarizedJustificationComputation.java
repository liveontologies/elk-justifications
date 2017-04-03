package org.semanticweb.elk.justifications;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.MinimalSubsetsFromInferences;
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
		extends MinimalSubsetsFromInferences<C, A> {

	private final MinimalSubsetEnumerator.Factory<List<C>, A> enumeratorFactory_;

	BinarizedJustificationComputation(
			final MinimalSubsetsFromInferences.Factory<List<C>, A> mainFactory,
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
		enumeratorFactory_ = mainFactory.create(
				InferenceSets.binarize(inferenceSet),
				InferenceSets.binarize(justifier), monitor);
	}

	@Override
	public MinimalSubsetEnumerator<A> newEnumerator(final C query) {
		return enumeratorFactory_
				.newEnumerator(Collections.singletonList(query));
	}

	@NestedStats
	public MinimalSubsetEnumerator.Factory<List<C>, A> getDelegate() {
		return enumeratorFactory_;
	}

	public static <C, A> MinimalSubsetsFromInferences.Factory<C, A> getFactory(
			final MinimalSubsetsFromInferences.Factory<List<C>, A> mainFactory) {
		return new Factory<C, A>(mainFactory);
	}

	private static class Factory<C, A>
			implements MinimalSubsetsFromInferences.Factory<C, A> {

		private final MinimalSubsetsFromInferences.Factory<List<C>, A> mainFactory_;

		Factory(MinimalSubsetsFromInferences.Factory<List<C>, A> mainFactory) {
			this.mainFactory_ = mainFactory;
		}

		@Override
		public MinimalSubsetEnumerator.Factory<C, A> create(
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new BinarizedJustificationComputation<C, A>(mainFactory_,
					inferenceSet, justifier, monitor);
		}

	}

}
