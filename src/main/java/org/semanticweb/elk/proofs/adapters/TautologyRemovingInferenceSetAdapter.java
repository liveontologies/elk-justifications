package org.semanticweb.elk.proofs.adapters;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * An inference set obtained from the given inference set by removing all
 * inferences that derive "tautologies" from non-tautologies. A conclusion
 * counts as a tautology if it is derivable by inferences with the empty
 * justification, i.e., the (only) justification for this conclusion is the
 * empty one. In the resulting inference set, tautologies are derived only from
 * tautologies (by a single inference).
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 * 
 */
class TautologyRemovingInferenceSetAdapter<C, A> implements InferenceSet<C, A> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(TautologyRemovingInferenceSetAdapter.class);

	/**
	 * inferences that are filtered
	 */
	private final InferenceSet<C, A> originalInferences_;

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
	private final Multimap<C, Inference<C, A>> inferencesByPremises_ = ArrayListMultimap
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

	TautologyRemovingInferenceSetAdapter(
			InferenceSet<C, A> originalInferences) {
		this.originalInferences_ = originalInferences;
	}

	@Override
	public Iterable<Inference<C, A>> getInferences(C conclusion) {
		toDoInit(conclusion);
		initialize();
		process();
		Iterable<Inference<C, A>> inferences = originalInferences_
				.getInferences(conclusion);
		if (isATautology(conclusion)) {
			// find one tautological inference
			for (Inference<C, A> inf : inferences) {
				if (!inf.getJustification().isEmpty()) {
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
			for (Inference<C, A> inf : originalInferences_
					.getInferences(conclusion)) {
				LOGGER_.trace("recursing by {}", inf);
				boolean noJustification = inf.getJustification().isEmpty();
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
			for (Inference<C, A> inf : inferencesByPremises_.get(tautology)) {
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