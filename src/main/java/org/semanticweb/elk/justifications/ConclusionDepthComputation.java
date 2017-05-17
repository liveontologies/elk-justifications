package org.semanticweb.elk.justifications;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.Proof;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

public class ConclusionDepthComputation<C> {

	private final static Logger LOGGER_ = LoggerFactory
			.getLogger(ConclusionDepthComputation.class);

	private final Proof<C> inferences_;

	private final Map<C, Integer> depth_ = new HashMap<>();

	private final ListMultimap<C, Inference<C>> inferencesByDeepestPremise_ = ArrayListMultimap
			.create();

	private final Queue<C> toDo_ = new ArrayDeque<>();

	private final Queue<ConclusionDepth<C>> toPropagate_ = new ArrayDeque<>();

	private final Set<C> done_ = new HashSet<>();

	public ConclusionDepthComputation(Proof<C> inferences) {
		this.inferences_ = inferences;
	}

	public Integer getDepth(C conclusion) {
		todo(conclusion);
		process();
		Integer result = depth_.get(conclusion);
		return result;
	}

	private void todo(C c) {
		if (done_.add(c)) {
			toDo_.add(c);
		}
	}

	private void process() {
		for (;;) {
			C next = toDo_.poll();
			if (next == null) {
				break;
			}
			for (Inference<C> inf : inferences_.getInferences(next)) {
				processInference(inf);
				for (C premise : inf.getPremises()) {
					todo(premise);
				}
			}
		}
		for (;;) {
			ConclusionDepth<C> next = toPropagate_.poll();
			if (next == null) {
				break;
			}
			Integer depth = depth_.get(next.conclusion);
			if (depth != null && depth <= next.depth) {
				continue;
			}
			// else
			depth_.put(next.conclusion, next.depth);
			for (Inference<C> inf : inferencesByDeepestPremise_
					.removeAll(next.conclusion)) {
				processInference(inf);
			}
		}
	}

	void processInference(Inference<C> inf) {
		C deepestPremise = null;
		int maxPremiseDepth = 0;
		for (C premise : inf.getPremises()) {
			Integer depth = depth_.get(premise);
			if (depth == null) {
				inferencesByDeepestPremise_.put(premise, inf);
				return;
			}
			if (depth > maxPremiseDepth) {
				deepestPremise = premise;
				maxPremiseDepth = depth;
			}
		}
		if (deepestPremise != null) {
			inferencesByDeepestPremise_.put(deepestPremise, inf);
		}
		todoDepth(inf.getConclusion(), maxPremiseDepth + 1);

	}

	public void todoDepth(C conclusion, int depth) {
		LOGGER_.debug("{}: new depth: {}", conclusion, depth);
		toPropagate_.add(new ConclusionDepth<C>(conclusion, depth));
	}

	private static class ConclusionDepth<C> {

		final C conclusion;

		int depth;

		ConclusionDepth(C conclusion, int depth) {
			this.conclusion = conclusion;
			this.depth = depth;
		}

	}

}
