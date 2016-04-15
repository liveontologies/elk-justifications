package org.semanticweb.elk.proofs.adapters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An inference set obtained from the given inference set by eliminating cyclic
 * inferences of length 1 and 2. An inference is cyclic of length 1 if one of
 * the premises of the inferences is the same as its conclusion. An inference is
 * cyclic of length 2 if there is a premise such that all inferences in the
 * original inference set that produce this premise use the conclusion of the
 * inference as one of the premises.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 * 
 */
class CycleRemovingInferenceSetAdapter<C, A> extends SimpleInferenceSet<C, A> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(CycleRemovingInferenceSetAdapter.class);

	/**
	 * inferences that are filtered
	 */
	private final InferenceSet<C, A> originalInferences_;

	/**
	 * conclusions for which to process inferences recursively
	 */
	private final Queue<C> toDoConclusions_ = new LinkedList<C>();

	/**
	 * caches the processed conclusions and {@link #toDoConclusions_}
	 */
	private final Set<C> doneConclusions_ = new HashSet<C>();

	/**
	 * inferences indexed by a premise that cannot be (currently) obtained as a
	 * conclusion of an inference in this inference that does not use the
	 * conclusion of the inference as one of the premises
	 */
	private final Map<C, List<Inference<C, A>>> blocked_ = new HashMap<C, List<Inference<C, A>>>();

	/**
	 * the inferences that are (no longer) blocked as a result of adding other
	 * (unblocked) inferences to this inference set
	 */
	private final Queue<Inference<C, A>> unblocked_ = new LinkedList<Inference<C, A>>();

	CycleRemovingInferenceSetAdapter(InferenceSet<C, A> originalInferences) {
		this.originalInferences_ = originalInferences;
	}

	@Override
	public Iterable<Inference<C, A>> getInferences(C conclusion) {
		toDo(conclusion);
		process();
		return super.getInferences(conclusion);
	}

	private void toDo(C conclusion) {
		if (doneConclusions_.add(conclusion)) {
			toDoConclusions_.add(conclusion);
		}
	}

	private void process() {
		C conclusion;
		while ((conclusion = toDoConclusions_.poll()) != null) {
			for (Inference<C, A> inf : originalInferences_
					.getInferences(conclusion)) {
				process(inf);
			}
		}
	}

	private void process(Inference<C, A> next) {
		for (C premise : next.getPremises()) {
			toDo(premise);
		}
		checkBlocked(next);
		while ((next = unblocked_.poll()) != null) {
			addInference(next);
			List<Inference<C, A>> blockedByNext = blocked_
					.remove(next.getConclusion());
			if (blockedByNext == null) {
				continue;
			}
			// else
			for (Inference<C, A> inf : blockedByNext) {
				checkBlocked(inf);
			}
		}
	}

	private void checkBlocked(Inference<C, A> inference) {
		if (inference.getPremises().contains(inference.getConclusion())) {
			LOGGER_.trace("{}: permanently blocked", inference);
			return;
		}
		C blockedPremise = getBlockedPremise(inference);
		if (blockedPremise == null) {
			LOGGER_.trace("{}: unblocked", inference);
			unblocked_.add(inference);
		} else {
			LOGGER_.trace("{}: blocked by {}", inference, blockedPremise);
			block(inference, blockedPremise);
		}
	}

	private void block(Inference<C, A> inference, C conclusion) {
		List<Inference<C, A>> blockedForConclusion = blocked_.get(conclusion);
		if (blockedForConclusion == null) {
			blockedForConclusion = new ArrayList<Inference<C, A>>();
			blocked_.put(conclusion, blockedForConclusion);
		}
		blockedForConclusion.add(inference);
	}

	/**
	 * @param inference
	 * @return an inference premise that cannot be derived by other inferences
	 *         that do not use the conclusion of this inference as one of the
	 *         premises; returns {@code null} if such a premise does not exist
	 */
	private C getBlockedPremise(Inference<C, A> inference) {
		C conclusion = inference.getConclusion();
		for (C premise : inference.getPremises()) {
			if (!derivableWithoutPremise(premise, conclusion)) {
				return premise;
			}
		}
		// else
		return null;
	}

	/**
	 * @param conclusion
	 * @param nonpremise
	 * @return {@code true} if there exists an inference in {@link #output_}
	 *         with the given conclusion which does not use the given premise
	 */
	private boolean derivableWithoutPremise(C conclusion, C nonpremise) {
		boolean derivable = false;
		for (Inference<C, A> inf : getInferences(conclusion)) {
			if (derivable |= !inf.getPremises().contains(nonpremise)) {
				return true;
			}
		}
		// else
		return false;
	}

}
