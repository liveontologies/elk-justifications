package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

public class BottomUpJustificationComputation<C, A>
		extends CancellableJustificationComputation<C, A> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(BottomUpJustificationComputation.class);

	private static final BottomUpJustificationComputation.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	/**
	 * conclusions for which to compute justifications
	 */
	private final Queue<C> toDo_ = new LinkedList<C>();

	/**
	 * caches conclusions for which justification computation has started
	 */
	private final Set<C> done_ = new HashSet<C>();

	/**
	 * a map from conclusions to inferences in which they participate
	 */
	private final Multimap<C, Inference<C, A>> inferencesByPremises_ = ArrayListMultimap
			.create();
	/**
	 * newly computed justifications to be propagated
	 */
	private final Queue<Job<C, A>> toDoJustifications_ = new LinkedList<Job<C, A>>();

	/**
	 * a map from conclusions to their justifications
	 */
	private final ListMultimap<C, Set<A>> justsByConcls_ = ArrayListMultimap
			.create();

	// Statistics

	private int countInferences_ = 0, countConclusions_ = 0,
			countJustifications_ = 0;

	BottomUpJustificationComputation(final InferenceSet<C, A> inferences,
			final Monitor monitor) {
		super(inferences, monitor);
	}

	@Override
	public Collection<Set<A>> computeJustifications(C conclusion) {

		process(conclusion);

		BloomHashSet.logStatistics();
		BloomHashSet.resetStatistics();

		return justsByConcls_.get(conclusion);
	}

	private void process(C conclusion) {

		toDo(conclusion);

		while ((conclusion = toDo_.poll()) != null) {
			LOGGER_.trace("{}: new lemma", conclusion);

			for (Inference<C, A> inf : getInferences(conclusion)) {
				process(inf);
				if (monitor_.isCancelled()) {
					return;
				}
			}

		}

	}

	@Override
	public void logStatistics() {
		if (LOGGER_.isDebugEnabled()) {
			LOGGER_.debug("{}: processed inferences", countInferences_);
			LOGGER_.debug("{}: processed conclusions", countConclusions_);
			LOGGER_.debug("{}: processed justification candidates",
					countJustifications_);
		}
	}

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	private void toDo(C exp) {
		if (done_.add(exp)) {
			countConclusions_++;
			toDo_.add(exp);
		}
	}

	private void process(Inference<C, A> inf) {
		LOGGER_.trace("{}: new inference", inf);
		countInferences_++;
		// new inference, propagate existing the justification for premises
		List<Set<A>> conclusionJusts = new ArrayList<Set<A>>();
		conclusionJusts.add(createSet(inf.getJustification()));
		for (C premise : inf.getPremises()) {
			inferencesByPremises_.put(premise, inf);
			toDo(premise);
			conclusionJusts = join(conclusionJusts,
					justsByConcls_.get(premise));
		}
		C conclusion = inf.getConclusion();
		for (Set<A> just : conclusionJusts) {
			process(new Job<C, A>(conclusion, just));
			if (monitor_.isCancelled()) {
				return;
			}
		}
	}

	/**
	 * propagates the newly computed justification until the fixpoint
	 */
	private void process(Job<C, A> job) {
		toDoJustifications_.add(job);
		while ((job = toDoJustifications_.poll()) != null) {
			if (monitor_.isCancelled()) {
				return;
			}
			LOGGER_.trace("{}: new justification: {}", job.expr, job.just);
			countJustifications_++;

			if (job.just.isEmpty()) {
				// all justifications are computed,
				// the inferences are not needed anymore
				for (Inference<C, A> inf : getInferences(job.expr)) {
					for (C premise : inf.getPremises()) {
						inferencesByPremises_.remove(premise, inf);
					}
				}
			}

			if (merge(job.just, justsByConcls_.get(job.expr))) {

				/*
				 * propagating justification over inferences
				 */
				for (Inference<C, A> inf : inferencesByPremises_
						.get(job.expr)) {

					Collection<Set<A>> conclusionJusts = new ArrayList<Set<A>>();
					Set<A> just = createSet(job.just);
					just.addAll(inf.getJustification());
					conclusionJusts.add(just);
					for (final C premise : inf.getPremises()) {
						if (!premise.equals(job.expr)) {
							conclusionJusts = join(conclusionJusts,
									justsByConcls_.get(premise));
						}
					}

					for (Set<A> conclJust : conclusionJusts) {
						toDoJustifications_.add(
								new Job<C, A>(inf.getConclusion(), conclJust));
					}

				}

			}

		}

	}

	/**
	 * Merges a given justification into a given collection of justifications.
	 * The justification is added to the collection unless its subset is already
	 * contained in the collection. Furthermore, all proper subsets of the
	 * justification are removed from the collection.
	 * 
	 * @param just
	 * @param justs
	 * @return {@code true} if the collection is modified as a result of this
	 *         operation and {@code false} otherwise
	 */
	private static <T> boolean merge(Set<T> just, Collection<Set<T>> justs) {
		int justSize = just.size();
		final Iterator<Set<T>> oldJustIter = justs.iterator();
		boolean isASubsetOfOld = false;
		while (oldJustIter.hasNext()) {
			final Set<T> oldJust = oldJustIter.next();
			if (justSize < oldJust.size()) {
				if (oldJust.containsAll(just)) {
					// new justification is smaller
					oldJustIter.remove();
					isASubsetOfOld = true;
				}
			} else if (!isASubsetOfOld & just.containsAll(oldJust)) {
				// is a superset of some old justification
				return false;
			}
		}
		// justification survived all tests, it is minimal
		justs.add(just);
		return true;
	}

	/**
	 * @param first
	 * @param second
	 * @return the list of all pairwise unions of the sets in the first and the
	 *         second collections, minimized under set inclusion
	 */
	private static <T> List<Set<T>> join(Collection<? extends Set<T>> first,
			Collection<? extends Set<T>> second) {
		if (first.isEmpty() || second.isEmpty()) {
			return Collections.emptyList();
		}
		List<Set<T>> result = new ArrayList<Set<T>>(
				first.size() * second.size());
		for (Set<T> firstSet : first) {
			for (Set<T> secondSet : second) {
				Set<T> union = createSet(firstSet);
				union.addAll(secondSet);
				merge(union, result);
			}
		}
		return result;
	}

	private static <E> Set<E> createSet(Collection<? extends E> elements) {
		return new BloomHashSet<E>(elements);
	}

	private static class Job<C, A> {
		final C expr;
		final Set<A> just;

		public Job(final C expr, final Set<A> just) {
			this.expr = expr;
			this.just = just;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "(" + expr + ", " + just + ")";
		}

	}

	/**
	 * The factory for creating a {@link BinarizedJustificationComputation}
	 * 
	 * @author Yevgeny Kazakov
	 *
	 * @param <C>
	 *            the type of conclusion and premises used by the inferences
	 * @param <A>
	 *            the type of axioms used by the inferences
	 */
	private static class Factory<C, A>
			implements JustificationComputation.Factory<C, A> {

		@Override
		public JustificationComputation<C, A> create(
				final InferenceSet<C, A> inferenceSet, final Monitor monitor) {
			return new BottomUpJustificationComputation<>(inferenceSet,
					monitor);
		}

	}

}
