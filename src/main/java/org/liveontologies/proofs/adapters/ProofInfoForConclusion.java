package org.liveontologies.proofs.adapters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A convenience class for collecting basic information about the inferences
 * that are used for deriving a given conclusion within a given proof.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 * @param <A>
 */
public class ProofInfoForConclusion<C, A> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(ProofInfoForConclusion.class);

	private final Set<C> usedConclusions_ = new HashSet<>();
	private final Set<A> usedAxioms_ = new HashSet<>();
	private final List<Inference<C>> usedInferences_ = new ArrayList<>();

	private final Queue<C> toDo_ = new LinkedList<C>();

	private final Proof<C> inferences_;
	private final InferenceJustifier<C, ? extends Set<? extends A>> justifier_;

	ProofInfoForConclusion(final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion) {
		this.inferences_ = proof;
		this.justifier_ = justifier;
		toDo(conclusion);
		process();
	}

	/**
	 * @return the inferences used in the proofs for the given conclusion
	 */
	public List<Inference<C>> getUsedInferences() {
		return usedInferences_;
	}

	/**
	 * @return the conclusions used in the proofs for the given conclusion
	 */
	public Set<C> getUsedConclusions() {
		return usedConclusions_;
	}

	/**
	 * @return the axioms used in justifications of inferences used in the
	 *         proofs for the given conclusion
	 */
	public Set<A> getUsedAxioms() {
		return usedAxioms_;
	}

	public void log() {
		LOGGER_.debug("{} used inferences", usedInferences_.size());
		LOGGER_.debug("{} used conclusions", usedConclusions_.size());
		LOGGER_.debug("{} used axioms", usedAxioms_.size());
	}

	private void toDo(C conclusion) {
		if (usedConclusions_.add(conclusion)) {
			toDo_.add(conclusion);
		}
	}

	private void process() {
		C next;
		while ((next = toDo_.poll()) != null) {
			for (final Inference<C> inf : inferences_.getInferences(next)) {
				usedInferences_.add(inf);
				usedAxioms_.addAll(justifier_.getJustification(inf));
				for (C premise : inf.getPremises()) {
					toDo(premise);
				}
			}
		}
	}

}