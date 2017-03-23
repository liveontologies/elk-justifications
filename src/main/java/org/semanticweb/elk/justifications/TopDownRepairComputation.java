package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.liveontologies.puli.Util;
import org.liveontologies.puli.collections.BloomTrieCollection2;
import org.liveontologies.puli.collections.Collection2;
import org.liveontologies.puli.justifications.AbstractJustificationComputation;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.ResetStats;
import org.liveontologies.puli.statistics.Stat;

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

	private static final int INITIAL_QUEUE_CAPACITY_ = 11;

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

	private JustificationComputation.Listener<A> listener_ = null;

	// Statistics
	private int producedJobsCount_ = 0;

	private TopDownRepairComputation(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final InterruptMonitor monitor) {
		super(inferences, monitor);
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			JustificationComputation.Listener<A> listener) {
		Util.checkNotNull(listener);
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
		Job job;
		jobLoop: while ((job = toDoJobs_.poll()) != null) {
			if (!minimalRepairs_.isMaximal(job.repair_)) {
				continue;
			}
			for (;;) {
				JustifiedInference<C, A> nextToBreak = job.toBreak_.poll();
				if (nextToBreak == null) {
					minimalRepairs_.add(job.repair_);
					if (listener_ != null) {
						listener_.newJustification(job.repair_);
					}
					continue jobLoop;
				}
				if (job.isBroken(nextToBreak)) {
					continue;
				}
				for (C premise : nextToBreak.getPremises()) {
					Job nextJob = job.copy();
					nextJob.broken_.add(premise);
					nextJob.toBreak_.addAll(getInferences(premise));
					produce(nextJob);
				}
				for (A axiom : nextToBreak.getJustification()) {
					Job nextJob = job.copy();
					nextJob.repair_.add(axiom);
					produce(nextJob);
				}
				break;
			}

			if (isInterrupted()) {
				break;
			}

		}
	}

	private void produce(final Job job) {
		producedJobsCount_++;
		toDoJobs_.add(job);
		// System.err.println("produced: " + job);
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

	private final Comparator<JustifiedInference<C, A>> inferenceComparator = new Comparator<JustifiedInference<C, A>>() {

		@Override
		public int compare(JustifiedInference<C, A> inf1,
				JustifiedInference<C, A> inf2) {
			return inf1.getPremises().size() + inf1.getJustification().size()
					- inf2.getPremises().size()
					- inf2.getJustification().size();
		}

	};

	/**
	 * A simple state for computing a repair
	 * 
	 * @author Peter Skocovsky
	 * @author Yevgeny Kazakov
	 */
	private class Job {

		private final Set<C> broken_;
		private final Set<A> repair_;
		private final Queue<JustifiedInference<C, A>> toBreak_;

		Job(Set<C> broken, Set<A> repair,
				Queue<JustifiedInference<C, A>> toBreak) {
			this.broken_ = broken;
			this.repair_ = repair;
			this.toBreak_ = toBreak;
		}

		Job(C conclusion) {
			this.broken_ = new HashSet<C>();
			broken_.add(conclusion);
			this.repair_ = new HashSet<>();
			Collection<? extends JustifiedInference<C, A>> inferences = getInferences(
					conclusion);
			this.toBreak_ = new PriorityQueue<>(inferences.size(),
					inferenceComparator);
			toBreak_.addAll(inferences);
		}

		Job copy() {
			Set<C> newBroken = new HashSet<>(broken_);
			Set<A> newRepair = new HashSet<>(repair_);
			Queue<JustifiedInference<C, A>> newToBreak = new PriorityQueue<>(
					toBreak_.size() + 1, inferenceComparator);
			newToBreak.addAll(toBreak_);
			return new Job(newBroken, newRepair, newToBreak);
		}

		boolean isBroken(JustifiedInference<C, A> inference) {
			for (C premise : inference.getPremises()) {
				if (broken_.contains(premise)) {
					return true;
				}
			}
			for (A axiom : inference.getJustification()) {
				if (repair_.contains(axiom)) {
					return true;
				}
			}
			// else not broken
			return false;
		}

		@Override
		public String toString() {
			return repair_.toString() + "; " + broken_.toString() + "; "
					+ toBreak_;
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
				return justOrder.compare(job1.repair_, job2.repair_);
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
				final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
				final InterruptMonitor monitor) {
			return new TopDownRepairComputation<>(inferenceSet, monitor);
		}

	}

}
