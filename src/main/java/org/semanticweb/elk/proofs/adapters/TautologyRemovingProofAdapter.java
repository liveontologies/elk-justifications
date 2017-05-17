package org.semanticweb.elk.proofs.adapters;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.DelegatingProof;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * A proof obtained from the given proof by removing all inferences that derive
 * "tautologies" from non-tautologies. A conclusion counts as a tautology if it
 * is derivable by inferences with the empty justification, i.e., the (only)
 * justification for this conclusion is the empty one. In the resulting proof,
 * tautologies are derived only from tautologies (by a single inference).
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 * 
 */
class TautologyRemovingProofAdapter<C, A> extends DelegatingProof<C, Proof<C>> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(TautologyRemovingProofAdapter.class);

	private final InferenceJustifier<C, ? extends Set<? extends A>> justifier_;

	/**
	 * the set of tautologies detected so far
	 */
	private final Set<C> tautologies_ = new HashSet<C>();

	/**
	 * tautologies to propagate
	 */
	private final Queue<C> toDoTautologies_ = new LinkedList<C>();

	/**
	 * index to retrieve inferences with empty justifications by their premises;
	 * only such inferences can derive new tautologies
	 */
	private final Multimap<C, Inference<C>> inferencesByPremises_ = ArrayListMultimap
			.create();

	/**
	 * a temporary queue for initialization of {@link #toDoTautologies_} and
	 * {@link #inferencesByPremises_}
	 */
	private final Queue<C> toDoInit_ = new LinkedList<C>();

	/**
	 * collects once they are inserted to {@link #toDoInit_} to avoid duplicates
	 */
	private final Set<C> doneInit_ = new HashSet<C>();

	TautologyRemovingProofAdapter(final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		super(proof);
		this.justifier_ = justifier;
	}

	@Override
	public Collection<? extends Inference<C>> getInferences(C conclusion) {
		toDoInit(conclusion);
		initialize();
		process();
		Collection<? extends Inference<C>> inferences = getDelegate()
				.getInferences(conclusion);
		if (isATautology(conclusion)) {
			// find one tautological inference
			for (final Inference<C> inf : inferences) {
				if (!justifier_.getJustification(inf).isEmpty()) {
					continue;
				}
				boolean inferenceIsATautology = true;
				for (C premise : inf.getPremises()) {
					if (!isATautology(premise)) {
						inferenceIsATautology = false;
						break;
					}
				}
				if (!inferenceIsATautology) {
					continue;
				}
				// else
				return Collections.singleton(inf);
			}

			return Collections.emptyList();
		}
		return inferences;
	}

	private void toDoInit(C conclusion) {
		if (doneInit_.add(conclusion)) {
			toDoInit_.add(conclusion);
		}
	}

	private void toDoTautology(C conclusion) {
		if (tautologies_.add(conclusion)) {
			toDoTautologies_.add(conclusion);
			LOGGER_.trace("new tautology {}", conclusion);
		}
	}

	private boolean isATautology(C conclusion) {
		return tautologies_.contains(conclusion);
	}

	/**
	 * initializes {@link #toDoTautologies_}
	 */
	private void initialize() {
		C conclusion;
		while ((conclusion = toDoInit_.poll()) != null) {
			for (final Inference<C> inf : getDelegate()
					.getInferences(conclusion)) {
				LOGGER_.trace("recursing by {}", inf);
				boolean noJustification = justifier_.getJustification(inf)
						.isEmpty();
				boolean conclusionIsATautology = noJustification;
				for (C premise : inf.getPremises()) {
					toDoInit(premise);
					if (noJustification) {
						inferencesByPremises_.put(premise, inf);
						conclusionIsATautology &= isATautology(premise);
					}
				}
				if (conclusionIsATautology) {
					toDoTautology(inf.getConclusion());
				}
			}
		}

	}

	private void process() {
		C tautology;
		while ((tautology = toDoTautologies_.poll()) != null) {
			for (final Inference<C> inf : inferencesByPremises_
					.get(tautology)) {
				boolean conclusionIsATautology = true;
				for (C premise : inf.getPremises()) {
					if (!isATautology(premise)) {
						conclusionIsATautology = false;
						break;
					}
				}
				if (conclusionIsATautology) {
					toDoTautology(inf.getConclusion());
				}
			}
		}
	}

}
