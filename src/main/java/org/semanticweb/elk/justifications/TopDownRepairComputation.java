package org.semanticweb.elk.justifications;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.Delegator;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.collections.BloomTrieCollection2;
import org.liveontologies.puli.collections.Collection2;
import org.liveontologies.puli.justifications.AbstractMinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.ComparableWrapper;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.MinimalSubsetsFromInferences;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.ResetStats;
import org.liveontologies.puli.statistics.Stat;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

/**
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class TopDownRepairComputation<C, A>
		extends MinimalSubsetsFromInferences<C, A> {

	private static final TopDownRepairComputation.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	@SuppressWarnings("unchecked")
	public static <C, A> MinimalSubsetsFromInferences.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	// Statistics
	private int producedJobsCount_ = 0;

	private TopDownRepairComputation(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
	}

	@Override
	public MinimalSubsetEnumerator<A> newEnumerator(final C query) {
		return new Enumerator(query);
	}

	private class Enumerator extends AbstractMinimalSubsetEnumerator<A> {

		private final C query_;

		/**
		 * jobs to be processed
		 */
		private Queue<Job<?>> toDoJobs_;

		/**
		 * Used to collect the result and prune jobs
		 */
		private final Collection2<Set<A>> minimalRepairs_ = new BloomTrieCollection2<>();

		/**
		 * Used to filter out redundant jobs
		 */
		private final Collection2<Job<?>> minimalJobs_ = new BloomTrieCollection2<>();

		private Listener<A> listener_ = null;

		private ComparableWrapper.Factory<Set<A>, ?> wrapper_ = null;

		Enumerator(final C query) {
			this.query_ = query;
		}

		@Override
		public void enumerate(
				final ComparableWrapper.Factory<Set<A>, ?> wrapper,
				final Listener<A> listener) {
			Preconditions.checkNotNull(listener);
			if (wrapper == null) {
				enumerate(listener);
				return;
			}
			// else
			this.toDoJobs_ = new PriorityQueue<>();
			this.minimalRepairs_.clear();
			this.wrapper_ = wrapper;
			this.listener_ = listener;

			initialize(query_);
			process();

			this.listener_ = null;
		}

		private void initialize(final C goal) {
			produce(newJob(wrapper_, goal));
		}

		private void process() {
			for (;;) {
				if (isInterrupted()) {
					break;
				}
				final Job<?> job = toDoJobs_.poll();
				if (job == null) {
					break;
				}
				// else
				if (!minimalRepairs_.isMinimal(job.repair_)) {
					continue;
				}
				// else
				if (!minimalJobs_.isMinimal(job)) {
					continue;
				}
				// else
				minimalJobs_.add(job);
				final Inference<C> nextToBreak = chooseToBreak(job.toBreak_);
				if (nextToBreak == null) {
					minimalRepairs_.add(job.repair_);
					if (listener_ != null) {
						listener_.newMinimalSubset(job.repair_);
					}
					continue;
				}
				for (C premise : nextToBreak.getPremises()) {
					produce(doBreak(job.repair_, job.toBreak_, job.broken_,
							wrapper_, premise));
				}
				for (A axiom : getJustification(nextToBreak)) {
					produce(repair(job.repair_, job.toBreak_, job.broken_,
							wrapper_, axiom));
				}
			}
		}

		private Inference<C> chooseToBreak(
				final Collection<Inference<C>> inferences) {
			// select the smallest conclusion according to the comparator
			Inference<C> result = null;
			for (Inference<C> inf : inferences) {
				if (result == null
						|| inferenceComparator.compare(inf, result) < 0) {
					result = inf;
				}
			}
			return result;
		}

		private void produce(final Job<?> job) {
			producedJobsCount_++;
			toDoJobs_.add(job);
		}

	}

	@Stat
	public int nProducedJobs() {
		return producedJobsCount_;
	}

	@ResetStats
	public void resetStats() {
		producedJobsCount_ = 0;
	}

	@NestedStats
	public static Class<?> getNestedStats() {
		return BloomTrieCollection2.class;
	}

	private final Comparator<Inference<C>> inferenceComparator = new Comparator<Inference<C>>() {

		@Override
		public int compare(final Inference<C> inf1, final Inference<C> inf2) {
			return inf1.getPremises().size() + getJustification(inf1).size()
					- inf2.getPremises().size() - getJustification(inf2).size();
		}

	};

	private <W extends ComparableWrapper<Set<A>, W>> Job<W> newJob(
			final ComparableWrapper.Factory<Set<A>, W> wrapper,
			final C conclusion) {
		return doBreak(Collections.<A> emptySet(),
				Collections.<Inference<C>> emptySet(),
				Collections.<C> emptySet(), wrapper, conclusion);
	}

	private <W extends ComparableWrapper<Set<A>, W>> Job<W> doBreak(
			final Set<A> repair, final Collection<Inference<C>> toBreak,
			final Set<C> broken,
			final ComparableWrapper.Factory<Set<A>, W> wrapper,
			final C conclusion) {

		final Set<A> newRepair = repair.isEmpty() ? new HashSet<A>(1)
				: new HashSet<>(repair);
		final Set<Inference<C>> newToBreak = toBreak.isEmpty()
				? new HashSet<Inference<C>>(3)
				: new HashSet<Inference<C>>(toBreak.size());
		final Set<C> newBroken = broken.isEmpty() ? new HashSet<C>(1)
				: new HashSet<>(broken);

		newBroken.add(conclusion);
		for (final Inference<C> inf : toBreak) {
			if (!inf.getPremises().contains(conclusion)) {
				newToBreak.add(inf);
			}
		}
		infLoop: for (final Inference<C> inf : getInferences(conclusion)) {
			for (final C premise : inf.getPremises()) {
				if (broken.contains(premise)) {
					continue infLoop;
				}
			}
			for (final A axiom : getJustification(inf)) {
				if (repair.contains(axiom)) {
					continue infLoop;
				}
			}
			newToBreak.add(inf);
		}
		return new Job<W>(newRepair, newToBreak, newBroken,
				wrapper.wrap(newRepair));
	}

	private <W extends ComparableWrapper<Set<A>, W>> Job<W> repair(
			final Set<A> repair, final Collection<Inference<C>> toBreak,
			final Set<C> broken,
			final ComparableWrapper.Factory<Set<A>, W> wrapper, final A axiom) {

		final Set<A> newRepair = new HashSet<>(repair);
		final Set<Inference<C>> newToBreak = new HashSet<>(toBreak.size());
		final Set<C> newBroken = new HashSet<>(broken);

		newRepair.add(axiom);
		for (final Inference<C> inf : toBreak) {
			if (!getJustification(inf).contains(axiom)) {
				newToBreak.add(inf);
			}
		}
		return new Job<W>(newRepair, newToBreak, newBroken,
				wrapper.wrap(newRepair));
	}

	/**
	 * A simple state for computing a repair;
	 * 
	 * @author Peter Skocovsky
	 * @author Yevgeny Kazakov
	 */
	private class Job<W extends ComparableWrapper<Set<A>, W>>
			extends AbstractSet<JobMember<C, A>> implements Comparable<Job<W>> {

		private final Set<A> repair_;
		private final Set<Inference<C>> toBreak_;
		/**
		 * the cached set of conclusions not derivable without using
		 * {@link #repair_} and {@link #toBreak_}
		 */
		private final Set<C> broken_;
		private final W wrapped_;

		Job(final Set<A> repair, final Set<Inference<C>> toBreak,
				final Set<C> broken, final W wrapped) {
			this.repair_ = repair;
			this.toBreak_ = toBreak;
			this.broken_ = broken;
			this.wrapped_ = wrapped;
		}

		@Override
		public boolean containsAll(final Collection<?> c) {
			if (c instanceof TopDownRepairComputation<?, ?>.Job<?>) {
				final TopDownRepairComputation<?, ?>.Job<?> other = (TopDownRepairComputation<?, ?>.Job<?>) c;
				return repair_.containsAll(other.repair_)
						&& toBreak_.containsAll(other.toBreak_);
			}
			// else
			return super.containsAll(c);
		}

		@Override
		public String toString() {
			return repair_.toString() + "; " + broken_.toString() + "; "
					+ toBreak_.toString();
		}

		@Override
		public Iterator<JobMember<C, A>> iterator() {
			return Iterators.<JobMember<C, A>> concat(Iterators.transform(
					repair_.iterator(), new Function<A, Axiom<C, A>>() {

						@Override
						public Axiom<C, A> apply(final A axiom) {
							return new Axiom<C, A>(axiom);
						}

					}), Iterators.transform(toBreak_.iterator(),
							new Function<Inference<C>, Inf<C, A>>() {

								@Override
								public Inf<C, A> apply(Inference<C> inf) {
									return new Inf<C, A>(inf);
								}

							}));
		}

		@Override
		public int size() {
			return repair_.size() + toBreak_.size();
		}

		@Override
		public int compareTo(final Job<W> other) {
			int result = wrapped_.compareTo(other.wrapped_);
			if (result != 0) {
				return result;
			}
			// else
			return toBreak_.size() - other.toBreak_.size();
		}

	}

	private interface JobMember<C, A> {

	}

	private final static class Inf<C, A> extends Delegator<Inference<C>>
			implements JobMember<C, A> {

		public Inf(Inference<C> delegate) {
			super(delegate);
		}

	}

	private final static class Axiom<C, A> extends Delegator<A>
			implements JobMember<C, A> {

		public Axiom(A delegate) {
			super(delegate);
		}

	}

	/**
	 * The factory for creating a {@link BottomUpJustificationComputation}
	 * 
	 * @author Peter Skocovsky
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
			return new TopDownRepairComputation<>(inferenceSet, justifier,
					monitor);
		}

	}

}
