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
import org.semanticweb.elk.util.collections.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

/**
 * Resets the whole context, does not cache anything!
 * 
 * @author Peter Skocovsky
 *
 * @param <C>
 * @param <A>
 */
public class MinPremisesBottomUp<C, A>
		extends CancellableJustificationComputation<C, A> {

	public static final String STAT_NAME_JUSTIFICATIONS = "SimpleBottomUp.nJustificationsOfAllConclusions";
	public static final String STAT_NAME_MAX_JUST_OF_CONCL = "SimpleBottomUp.maxNJustificationsOfAConclusion";
	public static final String STAT_NAME_INFERENCES = "SimpleBottomUp.nProcessedInferences";
	public static final String STAT_NAME_CONCLUSIONS = "SimpleBottomUp.nProcessedConclusions";
	public static final String STAT_NAME_CANDIDATES = "SimpleBottomUp.nProcessedJustificationCandidates";
	public static final String STAT_NAME_BLOCKED = "SimpleBottomUp.nBlockedJustifications";

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(MinPremisesBottomUp.class);

	private static final MinPremisesBottomUp.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	/**
	 * a map from conclusions to their justifications
	 */
	private final ListMultimap<C, Justification<C, A>> justifications_ = ArrayListMultimap
			.create();

	/**
	 * a map from premises to inferences for relevant conclusions
	 */
	private final Multimap<C, Inference<C, A>> inferencesByPremises_ = ArrayListMultimap
			.create();

	/**
	 * a map from premises and inferences for which they are used to their
	 * justifications
	 */
	private final Multimap<Pair<Inference<C, A>, C>, Justification<C, A>> premiseJustifications_ = ArrayListMultimap
			.create();

	/**
	 * newly computed justifications to be propagated
	 */
	private final Queue<Justification<C, A>> toDoJustifications_ = new PriorityQueue<Justification<C, A>>();

	// Statistics

	private int countInferences_ = 0, countConclusions_ = 0,
			countJustificationCandidates_ = 0, countBlocked_ = 0;

	MinPremisesBottomUp(final InferenceSet<C, A> inferences,
			final Monitor monitor) {
		super(inferences, monitor);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(C conclusion,
			int sizeLimit) {
		BloomSet.resetStatistics();
		justifications_.clear();
		inferencesByPremises_.clear();
		premiseJustifications_.clear();
		toDoJustifications_.clear();
		return new JustificationEnumerator(conclusion, sizeLimit).getResult();
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(C conclusion) {
		return computeJustifications(conclusion, Integer.MAX_VALUE);
	}

	@Override
	public String[] getStatNames() {
		return getFactory().getStatNames();
	}

	@Override
	public Map<String, Object> getStatistics() {
		final Map<String, Object> stats = new HashMap<String, Object>(
				BloomSet.getStatistics());
		stats.put(STAT_NAME_JUSTIFICATIONS, justifications_.size());
		stats.put(STAT_NAME_BLOCKED, countBlocked_);
		int max = 0;
		for (final C conclusion : justifications_.keySet()) {
			final List<Justification<C, A>> justs = justifications_
					.get(conclusion);
			if (justs.size() > max) {
				max = justs.size();
			}
		}
		stats.put(STAT_NAME_MAX_JUST_OF_CONCL, max);
		stats.put(STAT_NAME_INFERENCES, countInferences_);
		stats.put(STAT_NAME_CONCLUSIONS, countConclusions_);
		stats.put(STAT_NAME_CANDIDATES, countJustificationCandidates_);
		return stats;
	}

	@Override
	public void logStatistics() {
		if (LOGGER_.isDebugEnabled()) {
			LOGGER_.debug("{}: number of justifications of all conclusions",
					justifications_.size());
			int max = 0;
			for (final C conclusion : justifications_.keySet()) {
				final List<Justification<C, A>> justs = justifications_
						.get(conclusion);
				if (justs.size() > max) {
					max = justs.size();
				}
			}
			LOGGER_.debug("{}: number of justifications of the conclusion "
					+ "with most justifications", max);
			LOGGER_.debug("{}: processed inferences", countInferences_);
			LOGGER_.debug("{}: processed conclusions", countConclusions_);
			LOGGER_.debug("{}: computed justifications",
					justifications_.size());
			LOGGER_.debug("{}: blocked justifications", countBlocked_);
			LOGGER_.debug("{}: produced justification candidates",
					countJustificationCandidates_);
			for (final C conclusion : justifications_.keySet()) {
				final List<Justification<C, A>> justs = justifications_
						.get(conclusion);
				if (justs.size() > 1000) {
					LOGGER_.debug("conclusion with {} justifications: {}",
							justs.size(), conclusion);
				}
			}
		}
		BloomSet.logStatistics();
	}

	@Override
	public void resetStatistics() {
		countInferences_ = 0;
		countConclusions_ = 0;
		countJustificationCandidates_ = 0;
		countBlocked_ = 0;
		BloomSet.resetStatistics();
	}

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	/**
	 * Checks if the given justification has a subset in the given collection of
	 * justifications
	 * 
	 * @param just
	 * @param justs
	 * @return {@code true} if the given justification is not a superset of any
	 *         justification in the given collection
	 */
	public static <J extends Set<?>> boolean isMinimal(J just,
			Collection<? extends J> justs) {
		for (J other : justs) {
			if (just.containsAll(other)) {
				return false;
			}
		}
		// otherwise
		return true;
	}

	/**
	 * Merges a given justification into a given collection of justifications.
	 * The justification is added to the collection unless its subset is already
	 * contained in the collection. Furthermore, all proper supersets of the
	 * justification are removed from the collection.
	 * 
	 * @param just
	 * @param justs
	 * @return {@code true} if the collection is modified as a result of this
	 *         operation and {@code false} otherwise
	 */
	private static <J extends Set<?>> boolean merge(J just,
			Collection<J> justs) {
		int justSize = just.size();
		final Iterator<J> oldJustIter = justs.iterator();
		boolean isASubsetOfOld = false;
		while (oldJustIter.hasNext()) {
			final J oldJust = oldJustIter.next();
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
				Justification<C, T> union = firstSet.addElements(secondSet);
				merge(union, result);
			}
		}
		return result;
	}

	@SafeVarargs
	private static <C, A> Justification<C, A> createJustification(C conclusion,
			Collection<? extends A>... collections) {
		return new BloomSet<C, A>(conclusion, collections);
	}

	/**
	 * Performs computation of justifications for the given conclusion. Can
	 * compute and reuse justifications for other conclusions.
	 * 
	 * @author Yevgeny Kazakov
	 */
	private class JustificationEnumerator {

		private final int sizeLimit_;

		/**
		 * the conclusions that are relevant for the computation of the
		 * justifications, i.e., those from which the conclusion for which the
		 * justifications are computed can be derived
		 */
		private final Set<C> relevant_ = new HashSet<C>();

		/**
		 * temporary queue to compute {@link #relevant_}
		 */
		private final Queue<C> toInitialize_ = new LinkedList<C>();

		/**
		 * the justifications will be returned here, they come in increasing
		 * size order
		 */
		private final List<? extends Set<A>> result_;

		JustificationEnumerator(C conclusion, int sizeLimit) {
			this.sizeLimit_ = sizeLimit;
			this.result_ = justifications_.get(conclusion);
			toInitialize(conclusion);
			initialize();
		}

		private Collection<? extends Set<A>> getResult() {
			process();
			if (result_.isEmpty()) {
				return result_;
			}
			// else filter out oversized justifications
			int index = result_.size() - 1;
			while (result_.get(index).size() > sizeLimit_) {
				index--;
			}
			return result_.subList(0, index + 1);
		}

		/**
		 * traverse inferences to find relevant conclusions and create the queue
		 * of justifications to be propagated reusing previously computed
		 * justifications
		 */
		private void initialize() {

			C conclusion;
			while ((conclusion = toInitialize_.poll()) != null) {
				countConclusions_++;
				LOGGER_.trace("{}: computation of justifiations initialized",
						conclusion);
				boolean derived = false;
				for (Inference<C, A> inf : getInferences(conclusion)) {
					LOGGER_.trace("{}: new inference", inf);
					derived = true;
					countInferences_++;
					for (C premise : inf.getPremises()) {
						inferencesByPremises_.put(premise, inf);
						toInitialize(premise);
					}
					if (inf.getPremises().isEmpty()) {
						toDoJustifications_.add(createJustification(
								inf.getConclusion(), inf.getJustification()));
						countJustificationCandidates_++;
					}
				}
				if (!derived) {
					LOGGER_.warn("{}: lemma not derived!", conclusion);
				}
			}

		}

		private void toInitialize(C conclusion) {
			if (!relevant_.contains(conclusion)) {
				countConclusions_++;
				relevant_.add(conclusion);
				toInitialize_.add(conclusion);
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
					if (currentSize_ > sizeLimit_) {
						// stop
						LOGGER_.trace(
								"there are justifications of size larger than {}",
								sizeLimit_);
						toDoJustifications_.add(just);
						return;
					}
					LOGGER_.debug("enumerating justifications of size {}...",
							currentSize_);
				}

				C conclusion = just.getConclusion();
				if (!relevant_.contains(conclusion)) {
					countBlocked_++;
					LOGGER_.trace("blocked {}", just);
					continue;
				}
				List<Justification<C, A>> justs = justifications_
						.get(conclusion);
				if (!isMinimal(just, justs)) {
					continue;
				}
				if (!isMinimal(just, result_)) {
					countBlocked_++;
					LOGGER_.trace("blocked {}", just);
					continue;
				}
				// else
				justs.add(just);
				LOGGER_.trace("new {}", just);

				if (just.isEmpty()) {

					// all justifications are computed,
					// the inferences are not needed anymore
					for (Inference<C, A> inf : getInferences(conclusion)) {
						for (C premise : inf.getPremises()) {
							inferencesByPremises_.remove(premise, inf);
							final Pair<Inference<C, A>, C> key = Pair
									.create(inf, premise);
							premiseJustifications_.removeAll(key);
							premiseJustifications_.put(key,
									just.copyTo(premise));
						}
					}

				} else {

					/*
					 * minimize premise justifications of inferences deriving
					 * this conclusion
					 * 
					 * if the justification is empty and the inferences are
					 * removed, there is no need to minimize their premise
					 * justifications
					 */
					for (final Inference<C, A> inf : getInferences(
							conclusion)) {
						final Justification<C, A> justLessInf = just
								.removeElements(inf.getJustification());
						for (final C premise : inf.getPremises()) {
							final Iterator<Justification<C, A>> premiseJustIt = premiseJustifications_
									.get(Pair.create(inf, premise)).iterator();
							while (premiseJustIt.hasNext()) {
								final Justification<C, A> premiseJust = premiseJustIt
										.next();
								if (premiseJust.containsAll(justLessInf)) {
									premiseJustIt.remove();
								}
							}
						}
					}

				}

				/*
				 * add the justification to premise justifications if inferences
				 * where this conclusion is the premise iff it is minimal w.r.t.
				 * justifications of the inference conclusion
				 */
				final Collection<Inference<C, A>> inferences = inferencesByPremises_
						.get(conclusion);
				if (inferences == null || inferences.isEmpty()) {
					continue;
				}
				final List<Inference<C, A>> infsToPropagate = new ArrayList<>(
						inferences.size());
				for (final Inference<C, A> inf : inferences) {
					final Collection<Justification<C, A>> premiseJusts = premiseJustifications_
							.get(Pair.create(inf, conclusion));

					final Justification<C, A> justWithInf = just
							.addElements(inf.getJustification());
					if (isMinimal(justWithInf,
							justifications_.get(inf.getConclusion()))) {
						premiseJusts.add(just);
						infsToPropagate.add(inf);
					}

				}

				/*
				 * propagating justification over inferences
				 */
				for (Inference<C, A> inf : infsToPropagate) {

					Collection<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
					Justification<C, A> conclusionJust = just
							.copyTo(inf.getConclusion())
							.addElements(inf.getJustification());
					conclusionJusts.add(conclusionJust);
					for (final C premise : inf.getPremises()) {
						if (!premise.equals(conclusion)) {
							conclusionJusts = join(conclusionJusts,
									premiseJustifications_
											.get(Pair.create(inf, premise)));
						}
					}

					for (Justification<C, A> conclJust : conclusionJusts) {
						toDoJustifications_.add(conclJust);
						countJustificationCandidates_++;
					}

				}

			}

		}

	}

	/**
	 * The factory for creating a {@link MinPremisesBottomUp}
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
			return new MinPremisesBottomUp<>(inferenceSet, monitor);
		}

		@Override
		public String[] getStatNames() {
			final String[] statNames = new String[] { STAT_NAME_JUSTIFICATIONS,
					STAT_NAME_MAX_JUST_OF_CONCL, STAT_NAME_BLOCKED,
					STAT_NAME_INFERENCES, STAT_NAME_CONCLUSIONS,
					STAT_NAME_CANDIDATES, };
			final String[] bloomStatNames = BloomSet.getStatNames();
			final String[] ret = Arrays.copyOf(statNames,
					statNames.length + bloomStatNames.length);
			System.arraycopy(bloomStatNames, 0, ret, statNames.length,
					bloomStatNames.length);
			return ret;
		}

	}

}
