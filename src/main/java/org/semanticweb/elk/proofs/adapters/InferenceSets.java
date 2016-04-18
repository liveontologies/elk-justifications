package org.semanticweb.elk.proofs.adapters;

import java.util.List;

import org.semanticweb.elk.proofs.InferenceSet;

/**
 * Static utilities for inference sets
 * 
 * @author Yevgeny Kazakov
 *
 */
public class InferenceSets {

	public static <C, A> InferenceSet<List<C>, A> binarize(
			InferenceSet<C, A> inferences) {
		return new BinarizedInferenceSetAdapter<C, A>(inferences);
	}

	public static <C, A> InferenceSet<C, A> eliminateCycles(
			InferenceSet<C, A> inferences) {
		return new CycleRemovingInferenceSetAdapter<C, A>(inferences);
	}

	public static <C, A> boolean hasCycle(InferenceSet<C, A> inferences,
			C conclusion) {
		return (new InferenceSetCycleDetector<C, A>(inferences))
				.hasCyclicProofFor(conclusion);
	}

	public static <C, A> InferenceSetInfoForConclusion<C, A> getInfo(
			InferenceSet<C, A> inferences, C conclusion) {
		return new InferenceSetInfoForConclusion<C, A>(inferences, conclusion);
	}

}
