package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.AbstractMinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.ComparableWrapper;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.MinimalSubsetsFromInferences;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.ResetStats;
import org.liveontologies.puli.statistics.Stat;
import org.semanticweb.elk.util.collections.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
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
		extends MinimalSubsetsFromInferences<C, A> {

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
	private final Multimap<C, Inference<C>> inferencesByPremises_ = ArrayListMultimap
			.create();

	/**
	 * a map from premises and inferences for which they are used to their
	 * justifications
	 */
	private final Multimap<Pair<Inference<C>, C>, Justification<C, A>> premiseJustifications_ = ArrayListMultimap
			.create();

	// Statistics

	private int countInferences_ = 0, countConclusions_ = 0,
			countJustificationCandidates_ = 0, countBlocked_ = 0;

	private MinPremisesBottomUp(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
	}

	private void reset() {
		justifications_.clear();
		inferencesByPremises_.clear();
		premiseJustifications_.clear();
	}

	@Override
	public MinimalSubsetEnumerator<A> newEnumerator(final C query) {
		return new JustificationEnumerator(query);
	}

	@Stat
	public int nProcessedInferences() {
		return countInferences_;
	}

	@Stat
	public int nProcessedConclusions() {
		return countConclusions_;
	}

	@Stat
	public int nProcessedJustificationCandidates() {
		return countJustificationCandidates_;
	}

	@Stat
	public int nJustificationsOfAllConclusions() {
		return justifications_.size();
	}

	@Stat
	public int nBlockedJustifications() {
		return countBlocked_;
	}

	@Stat
	public int maxNJustificationsOfAConclusion() {
		int max = 0;
		for (final C conclusion : justifications_.keySet()) {
			final List<Justification<C, A>> justs = justifications_
					.get(conclusion);
			if (justs.size() > max) {
				max = justs.size();
			}
		}
		return max;
	}

	@ResetStats
	public void resetStats() {
		countInferences_ = 0;
		countConclusions_ = 0;
		countJustificationCandidates_ = 0;
		countBlocked_ = 0;
	}

	@NestedStats
	public static Class<?> getNestedStats() {
		return BloomSet.class;
	}

	@SuppressWarnings("unchecked")
	public static <C, A> MinimalSubsetsFromInferences.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
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
	private class JustificationEnumerator
			extends AbstractMinimalSubsetEnumerator<A> {

		private final C conclusion_;

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
		 * newly computed justifications to be propagated
		 */
		private PriorityQueue<Job<C, A, ?>> toDoJustifications_ = null;

		private Listener<A> listener_ = null;

		private ComparableWrapper.Factory<Set<A>, ?> wrapper_;

		/**
		 * the justifications will be returned here, they come in increasing
		 * size order
		 */
		private final List<? extends Set<A>> result_;

		JustificationEnumerator(C conclusion) {
			this.conclusion_ = conclusion;
			this.result_ = justifications_.get(conclusion);
		}

		@Override
		public void enumerate(
				final ComparableWrapper.Factory<Set<A>, ?> wrapper,
				final Listener<A> listener) {
			Preconditions.checkNotNull(listener);
			this.listener_ = listener;
			if (wrapper == null) {
				enumerate(listener);
				return;
			}
			// else

			if (wrapper.equals(this.wrapper_)) {
				// Visit already computed justifications. They should be in the
				// correct order.
				for (final Justification<C, A> just : justifications_
						.get(conclusion_)) {
					listener.newMinimalSubset(just);
				}
			} else {
				// Reset everything.
				this.wrapper_ = wrapper;
				reset();
			}

			this.toDoJustifications_ = new PriorityQueue<Job<C, A, ?>>();

			toInitialize(conclusion_);
			initialize();
			process();

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
				for (final Inference<C> inf : getInferences(conclusion)) {
					LOGGER_.trace("{}: new inference", inf);
					derived = true;
					countInferences_++;
					for (C premise : inf.getPremises()) {
						inferencesByPremises_.put(premise, inf);
						toInitialize(premise);
					}
					if (inf.getPremises().isEmpty()) {
						toDoJustifications_.add(Job.create(wrapper_,
								createJustification(inf.getConclusion(),
										getJustification(inf))));
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
			Job<C, A, ?> job;
			while ((job = toDoJustifications_.poll()) != null) {
				Justification<C, A> just = job.justification;
				if (isInterrupted()) {
					return;
				}

				C conclusion = just.getConclusion();
				if (!relevant_.contains(conclusion)) {
					countBlocked_++;
					LOGGER_.trace("blocked {}", just);
					continue;
				}
				List<Justification<C, A>> justs = justifications_
						.get(conclusion);
				if (!Utils.isMinimal(just, justs)) {
					continue;
				}
				if (!Utils.isMinimal(just, result_)) {
					countBlocked_++;
					LOGGER_.trace("blocked {}", just);
					continue;
				}
				// else
				justs.add(just);
				LOGGER_.trace("new {}", just);
				if (conclusion_.equals(conclusion) && listener_ != null) {
					listener_.newMinimalSubset(just);
				}

				if (just.isEmpty()) {

					// all justifications are computed,
					// the inferences are not needed anymore
					for (final Inference<C> inf : getInferences(conclusion)) {
						for (C premise : inf.getPremises()) {
							inferencesByPremises_.remove(premise, inf);
							final Pair<Inference<C>, C> key = Pair.create(inf,
									premise);
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
					for (final Inference<C> inf : getInferences(conclusion)) {
						final Justification<C, A> justLessInf = just
								.removeElements(getJustification(inf));
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
				final Collection<Inference<C>> inferences = inferencesByPremises_
						.get(conclusion);
				if (inferences == null || inferences.isEmpty()) {
					continue;
				}
				final List<Inference<C>> infsToPropagate = new ArrayList<>(
						inferences.size());
				for (final Inference<C> inf : inferences) {
					final Collection<Justification<C, A>> premiseJusts = premiseJustifications_
							.get(Pair.create(inf, conclusion));

					final Justification<C, A> justWithInf = just
							.addElements(getJustification(inf));
					if (Utils.isMinimal(justWithInf,
							justifications_.get(inf.getConclusion()))) {
						premiseJusts.add(just);
						infsToPropagate.add(inf);
					}

				}

				/*
				 * propagating justification over inferences
				 */
				for (final Inference<C> inf : infsToPropagate) {

					Collection<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
					Justification<C, A> conclusionJust = just
							.copyTo(inf.getConclusion())
							.addElements(getJustification(inf));
					conclusionJusts.add(conclusionJust);
					for (final C premise : inf.getPremises()) {
						if (!premise.equals(conclusion)) {
							conclusionJusts = Utils.join(conclusionJusts,
									premiseJustifications_
											.get(Pair.create(inf, premise)));
						}
					}

					for (Justification<C, A> conclJust : conclusionJusts) {
						toDoJustifications_
								.add(Job.create(wrapper_, conclJust));
						countJustificationCandidates_++;
					}

				}

			}

		}

	}

	private static class Job<C, A, W extends ComparableWrapper<Set<A>, W>>
			implements Comparable<Job<C, A, W>> {

		private final W wrapped_;

		public final Justification<C, A> justification;

		public Job(final W wrapped, final Justification<C, A> justification) {
			this.wrapped_ = wrapped;
			this.justification = justification;
		}

		public static <C, A, W extends ComparableWrapper<Set<A>, W>> Job<C, A, W> create(
				final ComparableWrapper.Factory<Set<A>, W> wrapper,
				final Justification<C, A> justification) {
			final W wrapped = wrapper.wrap(justification);
			return new Job<C, A, W>(wrapped, justification);
		}

		@Override
		public int compareTo(final Job<C, A, W> other) {
			final int result = wrapped_.compareTo(other.wrapped_);
			if (result != 0) {
				return result;
			}
			return Integer.compare(justification.getConclusion().hashCode(),
					other.justification.getConclusion().hashCode());
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
			implements MinimalSubsetsFromInferences.Factory<C, A> {

		@Override
		public MinimalSubsetEnumerator.Factory<C, A> create(
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new MinPremisesBottomUp<>(inferenceSet, justifier, monitor);
		}

	}

}
