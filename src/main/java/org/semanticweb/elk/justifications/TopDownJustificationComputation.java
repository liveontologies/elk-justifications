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
 * @author Peter Skocovsky
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class TopDownJustificationComputation<C, A>
		extends MinimalSubsetsFromInferences<C, A> {

	private static final TopDownJustificationComputation.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	private static final int INITIAL_QUEUE_CAPACITY_ = 11;

	@SuppressWarnings("unchecked")
	public static <C, A> MinimalSubsetsFromInferences.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	/**
	 * used to select the conclusion to expand
	 */
	private final Comparator<C> rank_;

	// Statistics
	private int producedJobsCount_ = 0, nonMinimalJobsCount_ = 0,
			expansionCount_ = 0, expandedInferencesCount_ = 0;

	private TopDownJustificationComputation(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
		this.rank_ = new Comparator<C>() {
			@Override
			public int compare(final C first, final C second) {
				int result = Integer.compare(
						inferenceSet.getInferences(first).size(),
						inferenceSet.getInferences(second).size());
				if (result != 0) {
					return result;
				}
				// else
				return Integer.compare(first.hashCode(), second.hashCode());
			}
		};
	}

	@Override
	public MinimalSubsetEnumerator<A> newEnumerator(final C query) {
		return new JustificationEnumerator(query);
	}

	private class JustificationEnumerator
			extends AbstractMinimalSubsetEnumerator<A> {

		private final C conclusion_;

		/**
		 * newly computed jobs to be propagated
		 */
		private Queue<Job> toDoJobs_;

		/**
		 * Used to minimize the jobs
		 */
		private final Collection2<Set<Object>> minimalJobs_ = new BloomTrieCollection2<>();

		private final Collection2<Set<A>> minimalJustifications_ = new BloomTrieCollection2<>();

		private Listener<A> listener_ = null;

		JustificationEnumerator(final C query) {
			this.conclusion_ = query;
		}

		@Override
		public void enumerate(final Comparator<? super Set<A>> order,
				final Listener<A> listener) {
			Preconditions.checkNotNull(listener);

			this.toDoJobs_ = new PriorityQueue<>(INITIAL_QUEUE_CAPACITY_,
					extendToJobOrder(order));
			this.minimalJobs_.clear();
			this.minimalJustifications_.clear();
			this.listener_ = listener;

			initialize(conclusion_);
			process();

			this.listener_ = null;
		}

		private void initialize(final C goal) {
			final Job initialJob = new Job(goal);
			produce(initialJob);
		}

		private void process() {
			Job job;
			while ((job = toDoJobs_.poll()) != null) {

				if (minimalJustifications_.isMinimal(job.justification_)
						&& minimalJobs_.isMinimal(job)) {
					minimalJobs_.add(job);
					if (job.premises_.isEmpty()) {
						minimalJustifications_.add(job.justification_);
						if (listener_ != null) {
							listener_.newMinimalSubset(job.justification_);
						}
					} else {
						expansionCount_++;
						for (final Inference<C> inf : getInferences(
								chooseConclusion(job.premises_))) {
							expandedInferencesCount_++;
							final Job newJob = job.expand(inf);
							produce(newJob);
						}
					}
				} else {
					nonMinimalJobsCount_++;
				}

				if (isInterrupted()) {
					break;
				}

			}
		}

		private C chooseConclusion(final Collection<C> conclusions) {
			// select the conclusion with the smallest rank
			C result = null;
			for (C c : conclusions) {
				if (result == null || rank_.compare(c, result) < 0) {
					result = c;
				}
			}
			return result;
		}

		private void produce(final Job job) {
			producedJobsCount_++;
			toDoJobs_.add(job);
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
					final int result = justOrder.compare(job1.justification_,
							job2.justification_);
					if (result != 0) {
						return result;
					}
					return Integer.compare(job1.premises_.size(),
							job2.premises_.size());
				}

			};
		}

	}

	@Stat
	public int nProducedJobs() {
		return producedJobsCount_;
	}

	@Stat
	public int nNonMinimalJobs() {
		return nonMinimalJobsCount_;
	}

	@Stat
	public double ratioInferencesPerExpansion() {
		return ((double) expandedInferencesCount_) / expansionCount_;
	}

	@ResetStats
	public void resetStats() {
		producedJobsCount_ = 0;
		nonMinimalJobsCount_ = 0;
		expansionCount_ = 0;
		expandedInferencesCount_ = 0;
	}

	@NestedStats
	public static Class<?> getNestedStats() {
		return BloomTrieCollection2.class;
	}

	/**
	 * A set of premises and justification that can be used for deriving the
	 * goal conclusion.
	 * 
	 * @author Peter Skocovsky
	 * @author Yevgeny Kazakov
	 */
	private class Job extends AbstractSet<Object> implements Comparable<Job> {

		private final Set<C> premises_;
		private final Set<A> justification_;

		private Job(final Set<C> premises, final Set<A> justification) {
			this.premises_ = premises;
			this.justification_ = justification;
		}

		public Job(final C goal) {
			this(Collections.singleton(goal), Collections.<A> emptySet());
		}

		public Job expand(final Inference<C> inference) {
			final Set<C> newPremises = new HashSet<>(premises_);
			newPremises.remove(inference.getConclusion());
			newPremises.addAll(inference.getPremises());
			Set<A> newJustification = justification_;
			Set<? extends A> toExpand = getJustification(inference);
			if (newJustification.containsAll(toExpand)) {
				newJustification = justification_;
			} else {
				newJustification = new HashSet<A>(justification_.size());
				newJustification.addAll(justification_);
				newJustification.addAll(toExpand);
			}
			return new Job(newPremises, newJustification);
		}

		@Override
		public Iterator<Object> iterator() {
			return Iterators.concat(premises_.iterator(),
					Iterators.transform(justification_.iterator(),
							new Function<A, Distinguisher>() {

								@Override
								public Distinguisher apply(final A axiom) {
									return new Distinguisher(axiom);
								}

							}));
		}

		@Override
		public boolean containsAll(final Collection<?> c) {
			if (c instanceof TopDownJustificationComputation.Job) {
				@SuppressWarnings("unchecked")
				final Job other = (Job) c;
				return premises_.containsAll(other.premises_)
						&& justification_.containsAll(other.justification_);
			}
			// else
			return super.containsAll(c);
		}

		@Override
		public boolean contains(final Object o) {
			if (o instanceof TopDownJustificationComputation.Distinguisher) {
				@SuppressWarnings("unchecked")
				final Distinguisher distinguisher = (Distinguisher) o;
				return justification_.contains(distinguisher.getDelegate());
			} else {
				return premises_.contains(o);
			}
		}

		@Override
		public boolean remove(final Object o) {
			if (o instanceof TopDownJustificationComputation.Distinguisher) {
				@SuppressWarnings("unchecked")
				final Distinguisher distinguisher = (Distinguisher) o;
				return justification_.remove(distinguisher.getDelegate());
			} else {
				return premises_.remove(o);
			}
		}

		@Override
		public int size() {
			return premises_.size() + justification_.size();
		}

		@Override
		public int compareTo(Job o) {
			int result = justification_.size() - o.justification_.size();
			if (result != 0) {
				return result;
			}
			result = premises_.size() - o.premises_.size();
			return result;
		}

	}

	private class Distinguisher extends Delegator<A> {

		public Distinguisher(final A delegate) {
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
			return new TopDownJustificationComputation<>(inferenceSet,
					justifier, monitor);
		}

	}

}
