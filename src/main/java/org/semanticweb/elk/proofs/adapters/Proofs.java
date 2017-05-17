package org.semanticweb.elk.proofs.adapters;

import java.util.List;
import java.util.Set;

import org.liveontologies.puli.GenericProof;
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

	public static <C> Proof<List<C>> binarize(final Proof<C> inferences) {
		return new BinarizedProofAdapter<C>(inferences);
	}

	public static <C, A> InferenceJustifier<List<C>, Set<? extends A>> binarize(
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		return new BinarizedProofAdapter.Justifier<C, A>(justifier);
	}

	public static <C, I extends Inference<C>> GenericProof<C, I> eliminateCycles(
			final GenericProof<C, I> inferences) {
		return new CycleRemovingProofAdapter<C, I>(inferences);
	}

	public static <C, A> Proof<C> eliminateTautologyInferences(
			final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		return new TautologyRemovingProofAdapter<C, A>(proof, justifier);
	}

	public static <C> boolean hasCycle(final Proof<C> inferences,
			final C conclusion) {
		return (new ProofCycleDetector<C>(inferences))
				.hasCyclicProofFor(conclusion);
	}

	public static <C, A> ProofInfoForConclusion<C, A> getInfo(
			final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion) {
		return new ProofInfoForConclusion<C, A>(proof, justifier, conclusion);
	}

}
