package org.liveontologies.proofs.adapters;

import java.util.List;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;

/**
 * Static utilities for proofs
 * 
 * @author Yevgeny Kazakov
 *
 */
public class Proofs {

	public static <C, I extends Inference<? extends C>> Proof<Inference<List<C>>> binarize(
			final Proof<? extends I> proof) {
		return new BinarizedProofAdapter<C, I>(proof);
	}

	public static <C, I extends Inference<? extends C>, A> InferenceJustifier<Inference<List<C>>, Set<? extends A>> binarize(
			final InferenceJustifier<? super I, ? extends Set<? extends A>> justifier) {
		return new BinarizedProofAdapter.Justifier<C, I, A>(justifier);
	}

	public static <I extends Inference<?>> Proof<I> eliminateCycles(
			final Proof<I> inferences) {
		return new CycleRemovingProofAdapter<I>(inferences);
	}

	public static <I extends Inference<?>, A> Proof<I> eliminateTautologyInferences(
			final Proof<I> proof,
			final InferenceJustifier<? super I, ? extends Set<? extends A>> justifier) {
		return new TautologyRemovingProofAdapter<I, A>(proof, justifier);
	}

	public static <I extends Inference<?>> boolean hasCycle(
			final Proof<I> inferences, final Object conclusion) {
		return (new ProofCycleDetector<I>(inferences))
				.hasCyclicProofFor(conclusion);
	}

	public static <I extends Inference<?>, A> ProofInfoForConclusion<I, A> getInfo(
			final Proof<I> proof,
			final InferenceJustifier<? super I, ? extends Set<? extends A>> justifier,
			Object conclusion) {
		return new ProofInfoForConclusion<I, A>(proof, justifier, conclusion);
	}

}
