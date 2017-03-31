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
import org.liveontologies.puli.justifications.AbstractJustificationComputation;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.JustificationComputation;
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
		extends AbstractJustificationComputation<C, A> {

	private static final TopDownRepairComputation.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	private static final int INITIAL_QUEUE_CAPACITY_ = 256;

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	/**
	 * jobs to be processed
	 */
	private Queue<Job> toDoJobs_ = new PriorityQueue<Job>();

	/**
	 * Used to collect the result and prune jobs
	 */
	private final Collection2<Set<A>> minimalRepairs_ = new BloomTrieCollection2<>();

	/**
	 * Used to filter out redundant jobs
	 */
	private final Collection2<Job> minimalJobs_ = new BloomTrieCollection2<>();

	private JustificationComputation.Listener<A> listener_ = null;

	// Statistics
	private int producedJobsCount_ = 0;

	private TopDownRepairComputation(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			JustificationComputation.Listener<A> listener) {
		Preconditions.checkNotNull(listener);
		this.toDoJobs_ = new PriorityQueue<>(INITIAL_QUEUE_CAPACITY_,
				extendToJobOrder(order));
		this.minimalRepairs_.clear();
		this.listener_ = listener;

		initialize(conclusion);
		process();

		this.listener_ = null;
	}

	private void initialize(final C goal) {
		produce(new Job(goal));
	}

	private void process() {
		for (;;) {
			if (isInterrupted()) {
				break;
			}
			Job job = toDoJobs_.poll();
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
					listener_.newJustification(job.repair_);
				}
				continue;
			}
			for (C premise : nextToBreak.getPremises()) {
				produce(job.copy().brake(premise, job.toBreak_));
			}
			for (A axiom : getJustification(nextToBreak)) {
				produce(job.copy().repair(axiom, job.toBreak_));
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

	private void produce(final Job job) {
		producedJobsCount_++;
		toDoJobs_.add(job);
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

	/**
	 * A simple state for computing a repair;
	 * 
	 * @author Peter Skocovsky
	 * @author Yevgeny Kazakov
	 */
	private class Job extends AbstractSet<JobMember<C, A>> {

		private final Set<A> repair_;
		private final Set<Inference<C>> toBreak_;
		/**
		 * the cached set of conclusions not derivable without using
		 * {@link #repair_} and {@link #toBreak_}
		 */
		private final Set<C> broken_;

		Job(Set<A> repair, Set<Inference<C>> toBreak, Set<C> broken) {
			this.repair_ = repair;
			this.toBreak_ = toBreak;
			this.broken_ = broken;
		}

		Job() {
			this(new HashSet<A>(1), new HashSet<Inference<C>>(3),
					new HashSet<C>(1));
		}

		Job(C conclusion) {
			this();
			brake(conclusion, Collections.<Inference<C>> emptySet());
		}

		Job copy() {
			return new Job(new HashSet<A>(repair_),
					new HashSet<Inference<C>>(toBreak_.size()),
					new HashSet<C>(broken_));
		}

		Job brake(C broken, Collection<Inference<C>> toBreak) {
			broken_.add(broken);
			for (Inference<C> inf : toBreak) {
				if (!inf.getPremises().contains(broken)) {
					toBreak_.add(inf);
				}
			}
			infLoop: for (Inference<C> inf : getInferences(broken)) {
				for (C premise : inf.getPremises()) {
					if (broken_.contains(premise)) {
						continue infLoop;
					}
				}
				for (A axiom : getJustification(inf)) {
					if (repair_.contains(axiom)) {
						continue infLoop;
					}
				}
				toBreak_.add(inf);
			}
			return this;
		}

		Job repair(A axiom, Collection<Inference<C>> toBreak) {
			repair_.add(axiom);
			for (Inference<C> inf : toBreak) {
				if (!getJustification(inf).contains(axiom)) {
					toBreak_.add(inf);
				}
			}
			return this;
		}

		@Override
		public boolean containsAll(final Collection<?> c) {
			if (c instanceof TopDownRepairComputation<?, ?>.Job) {
				final TopDownRepairComputation<?, ?>.Job other = (TopDownRepairComputation<?, ?>.Job) c;
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

	private Comparator<Job> extendToJobOrder(
			final Comparator<? super Set<A>> order) {

		final Comparator<? super Set<A>> justOrder;
		if (order == null) {
			justOrder = DEFAULT_ORDER;
		} else {
			justOrder = order;
		}

		return new Comparator<Job>() {

			@Override
			public int compare(final Job job1, final Job job2) {
				int result = justOrder.compare(job1.repair_, job2.repair_);
				if (result != 0) {
					return result;
				}
				// else
				return job1.toBreak_.size() - job2.toBreak_.size();
			}

		};
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
			implements JustificationComputation.Factory<C, A> {

		@Override
		public JustificationComputation<C, A> create(
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new TopDownRepairComputation<>(inferenceSet, justifier,
					monitor);
		}

	}

}
