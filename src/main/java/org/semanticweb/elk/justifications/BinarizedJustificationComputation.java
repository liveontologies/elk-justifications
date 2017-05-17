package org.semanticweb.elk.justifications;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.MinimalSubsetsFromProofs;
import org.liveontologies.puli.statistics.NestedStats;
import org.semanticweb.elk.proofs.adapters.Proofs;

/**
 * Provided justification computation applied to the binarization of the input
 * proof.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class BinarizedJustificationComputation<C, A>
		extends MinimalSubsetsFromProofs<C, A> {

	private final MinimalSubsetEnumerator.Factory<List<C>, A> enumeratorFactory_;

	BinarizedJustificationComputation(
			final MinimalSubsetsFromProofs.Factory<List<C>, A> mainFactory,
			final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(proof, justifier, monitor);
		enumeratorFactory_ = mainFactory.create(Proofs.binarize(proof),
				Proofs.binarize(justifier), monitor);
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

	public static <C, A> MinimalSubsetsFromProofs.Factory<C, A> getFactory(
			final MinimalSubsetsFromProofs.Factory<List<C>, A> mainFactory) {
		return new Factory<C, A>(mainFactory);
	}

	private static class Factory<C, A>
			implements MinimalSubsetsFromProofs.Factory<C, A> {

		private final MinimalSubsetsFromProofs.Factory<List<C>, A> mainFactory_;

		Factory(MinimalSubsetsFromProofs.Factory<List<C>, A> mainFactory) {
			this.mainFactory_ = mainFactory;
		}

		@Override
		public MinimalSubsetEnumerator.Factory<C, A> create(
				final Proof<C> proof,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new BinarizedJustificationComputation<C, A>(mainFactory_,
					proof, justifier, monitor);
		}

	}

}
