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

	public static final String STAT_NAME_JUSTIFICATIONS = "BottomUpJustificationComputation.nJustificationsOfAllConclusions";
	public static final String STAT_NAME_MAX_JUST_OF_CONCL = "BottomUpJustificationComputation.maxNJustificationsOfAConclusion";
	public static final String STAT_NAME_INFERENCES = "BottomUpJustificationComputation.nProcessedInferences";
	public static final String STAT_NAME_CONCLUSIONS = "BottomUpJustificationComputation.nProcessedConclusions";
	public static final String STAT_NAME_CANDIDATES = "BottomUpJustificationComputation.nProcessedJustificationCandidates";

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
	private final Queue<Justification<C, A>> toDoJustifications_ = new PriorityQueue<Justification<C, A>>();

	/**
	 * a map from conclusions to their justifications
	 */
	private final ListMultimap<C, Justification<C, A>> justsByConcls_ = ArrayListMultimap
			.create();

	// Statistics

	private int countInferences_ = 0, countConclusions_ = 0,
			countJustifications_ = 0;

	BottomUpJustificationComputation(final InferenceSet<C, A> inferences,
			final Monitor monitor) {
		super(inferences, monitor);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(C conclusion) {

		if (!done_.contains(conclusion)) {
			// compute it first
			BloomHashSet.resetStatistics();
			process(conclusion);
		}

		return justsByConcls_.get(conclusion);
	}

	private void process(C conclusion) {

		toDo(conclusion);

		while ((conclusion = toDo_.poll()) != null) {
			LOGGER_.trace("{}: new lemma", conclusion);

			boolean derived = false;
			for (Inference<C, A> inf : getInferences(conclusion)) {
				derived = true;
				process(inf);
				if (monitor_.isCancelled()) {
					return;
				}
			}
			if (!derived) {
				LOGGER_.warn("{}: lemma not derived!", conclusion);
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
		stats.put(STAT_NAME_JUSTIFICATIONS, justsByConcls_.size());
		int max = 0;
		for (final C conclusion : justsByConcls_.keySet()) {
			final List<Justification<C, A>> justs = justsByConcls_
					.get(conclusion);
			if (justs.size() > max) {
				max = justs.size();
			}
		}
		stats.put(STAT_NAME_MAX_JUST_OF_CONCL, max);
		stats.put(STAT_NAME_INFERENCES, countInferences_);
		stats.put(STAT_NAME_CONCLUSIONS, countConclusions_);
		stats.put(STAT_NAME_CANDIDATES, countJustifications_);
		return stats;
	}

	@Override
	public void logStatistics() {
		if (LOGGER_.isDebugEnabled()) {
			LOGGER_.debug("{}: number of justifications of all conclusions",
					justsByConcls_.size());
			int max = 0;
			for (final C conclusion : justsByConcls_.keySet()) {
				final List<Justification<C, A>> justs = justsByConcls_
						.get(conclusion);
				if (justs.size() > max) {
					max = justs.size();
				}
			}
			LOGGER_.debug("{}: number of justifications of the conclusion "
					+ "with most justifications", max);
			LOGGER_.debug("{}: processed inferences", countInferences_);
			LOGGER_.debug("{}: processed conclusions", countConclusions_);
			LOGGER_.debug("{}: processed justification candidates",
					countJustifications_);
			for (final C conclusion : justsByConcls_.keySet()) {
				final List<Justification<C, A>> justs = justsByConcls_
						.get(conclusion);
				if (justs.size() > 1000) {
					LOGGER_.debug("conclusion with {} justifications: {}",
							justs.size(), conclusion);
				}
			}
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
		List<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
		conclusionJusts.add(createJustification(inf.getConclusion(), 0,
				inf.getJustification()));
		for (C premise : inf.getPremises()) {
			inferencesByPremises_.put(premise, inf);
			toDo(premise);
			conclusionJusts = join(conclusionJusts,
					justsByConcls_.get(premise));
		}
		for (Justification<C, A> just : conclusionJusts) {
			toDoJustifications_.add(just);
			if (monitor_.isCancelled()) {
				return;
			}
		}
	}

	/**
	 * process new justifications until the fixpoint
	 */
	private void process() {
		Justification<C, A> just;
		int currentSize_ = 0; // 
		while ((just = toDoJustifications_.poll()) != null) {
			if (monitor_.isCancelled()) {
				return;
			}

			int size = just.size();
			if (size != currentSize_) {
				currentSize_ = size;
				LOGGER_.debug("enumerating justifications of size {}...",
						currentSize_);
			}
			
			C conclusion = just.getConclusion();
			List<Justification<C, A>> justs = justsByConcls_.get(conclusion);
			if (!merge(just, justs)) {
				continue;
			}

			LOGGER_.trace("{}", just);
			countJustifications_++;

			if (just.isEmpty()) {
				// all justifications are computed,
				// the inferences are not needed anymore
				for (Inference<C, A> inf : getInferences(conclusion)) {
					for (C premise : inf.getPremises()) {
						inferencesByPremises_.remove(premise, inf);
					}
				}
			}

			/*
			 * propagating justification over inferences
			 */
			for (Inference<C, A> inf : inferencesByPremises_.get(conclusion)) {

				Collection<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
				Justification<C, A> conclusionJust = createJustification(
						inf.getConclusion(), just.getAge() + 1, just,
						inf.getJustification());
				conclusionJusts.add(conclusionJust);
				for (final C premise : inf.getPremises()) {
					if (!premise.equals(conclusion)) {
						conclusionJusts = join(conclusionJusts,
								justsByConcls_.get(premise));
					}
				}

				for (Justification<C, A> conclJust : conclusionJusts) {
					toDoJustifications_.add(conclJust);
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
	private static <C, A> boolean merge(Justification<C, A> just,
			Collection<Justification<C, A>> justs) {
		int justSize = just.size();
		final Iterator<Justification<C, A>> oldJustIter = justs.iterator();
		boolean isASubsetOfOld = false;
		while (oldJustIter.hasNext()) {
			final Justification<C, A> oldJust = oldJustIter.next();
			if (justSize < oldJust.size()) {
				if (oldJust.containsAll(just)) {
					// new justification is smaller
					LOGGER_.trace("removed {}", oldJust);
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
		LOGGER_.trace("new {}", just);
		return true;
	}

	/**
	 * @param first
	 * @param second
	 * @return the list of all pairwise unions of the justifications in the
	 *         first and the second collections, minimized under set inclusion
	 */
	private static <C, T> List<Justification<C, T>> join(
			Collection<? extends Justification<C, T>> first,
			Collection<? extends Justification<C, T>> second) {
		if (first.isEmpty() || second.isEmpty()) {
			return Collections.emptyList();
		}
		List<Justification<C, T>> result = new ArrayList<Justification<C, T>>(
				first.size() * second.size());
		for (Justification<C, T> firstSet : first) {
			for (Justification<C, T> secondSet : second) {
				Justification<C, T> union = createJustification(
						firstSet.getConclusion(),
						Math.max(firstSet.getAge(), secondSet.getAge() + 1),
						firstSet, secondSet);
				merge(union, result);
			}
		}
		return result;
	}

	@SafeVarargs
	private static <C, A> Justification<C, A> createJustification(C conclusion,
			int age, Collection<? extends A>... collections) {
		return new BloomHashSet<C, A>(conclusion, age, collections);
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
			final String[] statNames = new String[] { STAT_NAME_JUSTIFICATIONS,
					STAT_NAME_MAX_JUST_OF_CONCL, STAT_NAME_INFERENCES,
					STAT_NAME_CONCLUSIONS, STAT_NAME_CANDIDATES, };
			final String[] bloomStatNames = BloomHashSet.getStatNames();
			final String[] ret = Arrays.copyOf(statNames,
					statNames.length + bloomStatNames.length);
			System.arraycopy(bloomStatNames, 0, ret, statNames.length,
					bloomStatNames.length);
			return ret;
		}

	}

}
