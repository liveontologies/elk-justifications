package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
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

	public static final String STAT_NAME_INFERENCES = "BottomUpJustificationComputation.nProcessedInferences";
	public static final String STAT_NAME_CONCLUSIONS = "BottomUpJustificationComputation.nProcessedConclusions";
	public static final String STAT_NAME_JUSTIFICATIONS = "BottomUpJustificationComputation.nProcessedJustificationCandidates";

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
	private final Queue<Job<C, A>> toDoJustifications_ = new PriorityQueue<Job<C, A>>();

	/**
	 * a map from conclusions to their justifications
	 */
	private final ListMultimap<C, Set<A>> justsByConcls_ = ArrayListMultimap
			.create();

	private StronglyConnectedComponentDecomposition<C> decomposition_;

	// Statistics

	private int countInferences_ = 0, countConclusions_ = 0,
			countJustifications_ = 0;

	BottomUpJustificationComputation(final InferenceSet<C, A> inferences,
			final Monitor monitor) {
		super(inferences, monitor);
	}

	@Override
	public Collection<Set<A>> computeJustifications(C conclusion) {

		if (!done_.contains(conclusion)) {
			// compute it first
			BloomHashSet.resetStatistics();
			decomposition_ = StronglyConnectedComponentsComputation
					.computeComponents(this, conclusion);
			process(conclusion);
		}

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
		process();
	}

	@Override
	public String[] getStatNames() {
		return getFactory().getStatNames();
	}

	@Override
	public Map<String, Object> getStatistics() {
		final Map<String, Object> stats = new HashMap<String, Object>(
				BloomHashSet.getStatistics());
		stats.put(STAT_NAME_INFERENCES, countInferences_);
		stats.put(STAT_NAME_CONCLUSIONS, countConclusions_);
		stats.put(STAT_NAME_JUSTIFICATIONS, countJustifications_);
		return stats;
	}

	@Override
	public void logStatistics() {
		if (LOGGER_.isDebugEnabled()) {
			LOGGER_.debug("{}: processed inferences", countInferences_);
			LOGGER_.debug("{}: processed conclusions", countConclusions_);
			LOGGER_.debug("{}: processed justification candidates",
					countJustifications_);
		}
		BloomHashSet.logStatistics();
	}

	@Override
	public void resetStatistics() {
		countInferences_ = 0;
		countConclusions_ = 0;
		countJustifications_ = 0;
		BloomHashSet.resetStatistics();
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
		// new inference, propagate existing justifications for premises
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
			toDo(conclusion, just);			
			if (monitor_.isCancelled()) {
				return;
			}
		}
	}

	private void toDo(C conclusion, Set<A> just) {
		if (merge(just, justsByConcls_.get(conclusion))) {
			LOGGER_.trace("{}: new justification: {}", conclusion, just);
			countJustifications_++;
			toDoJustifications_.add(new Job<C, A>(conclusion,
					decomposition_.getComponentId(conclusion), just));
		}
	}

	/**
	 * process new justifications until the fixpoint
	 */
	private void process() {
		Job<C, A> job;
		while ((job = toDoJustifications_.poll()) != null) {
			if (monitor_.isCancelled()) {
				return;
			}

			LOGGER_.trace("{}", job);

			if (job.just.isEmpty()) {
				// all justifications are computed,
				// the inferences are not needed anymore
				for (Inference<C, A> inf : getInferences(job.expr)) {
					for (C premise : inf.getPremises()) {
						inferencesByPremises_.remove(premise, inf);
					}
				}
			}

			/*
			 * propagating justification over inferences
			 */
			for (Inference<C, A> inf : inferencesByPremises_.get(job.expr)) {

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
					toDo(inf.getConclusion(), conclJust);
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
					LOGGER_.trace("removed: {}", oldJust);
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

	private static class Job<C, A> implements Comparable<Job<C, A>> {
		final C expr;
		final int order;
		final Set<A> just;

		public Job(final C expr, int order, final Set<A> just) {
			this.expr = expr;
			this.order = order;
			this.just = just;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "([" + order + "] " + expr
					+ ": " + just + ")";
		}

		@Override
		public int compareTo(Job<C, A> o) {
			// first priority for jobs with deeper conclusions
			// (this computes justifications component-wise)
			int orderDiff = order - o.order;
			if (orderDiff != 0) {
				return orderDiff;
			}
			// within each component, prioritize smaller justifications
			return just.size() - o.just.size();
		}

	}

	/**
	 * The factory for creating a {@link BottomUpJustificationComputation}
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
		
		public String[] getStatNames() {
			final String[] statNames = new String[] { STAT_NAME_INFERENCES,
					STAT_NAME_CONCLUSIONS, STAT_NAME_JUSTIFICATIONS, };
			final String[] bloomStatNames = BloomHashSet.getStatNames();
			final String[] ret = Arrays.copyOf(statNames,
					statNames.length + bloomStatNames.length);
			System.arraycopy(bloomStatNames, 0, ret, statNames.length,
					bloomStatNames.length);
			return ret;
		}

	}

}
