package org.semanticweb.elk.proofs.adapters;

import java.util.List;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;

/**
 * Static utilities for inference sets
 * 
 * @author Yevgeny Kazakov
 *
 */
public class InferenceSets {

	public static <C> InferenceSet<List<C>> binarize(
			final InferenceSet<C> inferences) {
		return new BinarizedInferenceSetAdapter<C>(inferences);
	}

	public static <C, A> InferenceJustifier<List<C>, Set<? extends A>> binarize(
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		return new BinarizedInferenceSetAdapter.Justifier<C, A>(justifier);
	}

	public static <C, I extends Inference<C>> GenericInferenceSet<C, I> eliminateCycles(
			final GenericInferenceSet<C, I> inferences) {
		return new CycleRemovingInferenceSetAdapter<C, I>(inferences);
	}

	public static <C, A> InferenceSet<C> eliminateTautologyInferences(
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		return new TautologyRemovingInferenceSetAdapter<C, A>(inferenceSet,
				justifier);
	}

	public static <C> boolean hasCycle(final InferenceSet<C> inferences,
			final C conclusion) {
		return (new InferenceSetCycleDetector<C>(inferences))
				.hasCyclicProofFor(conclusion);
	}

	public static <C, A> InferenceSetInfoForConclusion<C, A> getInfo(
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion) {
		return new InferenceSetInfoForConclusion<C, A>(inferenceSet, justifier,
				conclusion);
	}

}
