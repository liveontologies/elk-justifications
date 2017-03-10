package org.semanticweb.elk.justifications;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.Delegator;
import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.liveontologies.puli.Util;
import org.liveontologies.puli.collections.BloomTrieCollection2;
import org.liveontologies.puli.collections.Collection2;
import org.semanticweb.elk.statistics.NestedStats;
import org.semanticweb.elk.statistics.ResetStats;
import org.semanticweb.elk.statistics.Stat;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;

/**
 * Computing justifications by resolving inferences. An inference X can be
 * resolved with an inference Y if the conclusion of X is one of the premises of
 * Y; the resulting inference Z will have the conclusion of Y, all premises of X
 * and Y except for the resolved one and all justificaitons of X and Y.
 * 
 * @author Peter Skocovsky
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class ResolutionJustificationComputation<C, A>
		extends CancellableJustificationComputation<C, A> {

	private static final ResolutionJustificationComputation.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	private static final int INITIAL_QUEUE_CAPACITY_ = 11;

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	/**
	 * newly computed jobs to be propagated
	 */
	private Queue<Job<C, A>> toDoJobs_ = new PriorityQueue<Job<C, A>>();

	/**
	 * Used to minimize the jobs
	 */
	private final Collection2<Job<C, A>> minimalJobs_ = new BloomTrieCollection2<>();

	/**
	 * Used to collect the result
	 */
	private final Collection2<Set<A>> minimalJustifications_ = new BloomTrieCollection2<>();

	private final ListMultimap<C, Job<C, A>>
	// jobs whose conclusions are selected, indexed by these conclusion
	jobsBySelectedConclusions_ = ArrayListMultimap.create(),
			// jobs whose premise is selected, indexed by this premise
			jobsBySelectedPremises_ = ArrayListMultimap.create();
	private final SelectionFunction<C, A> selection_;

	private C goal_;

	private Listener<A> listener_ = null;

	// Statistics
	private int producedJobsCount_ = 0, nonMinimalJobsCount_ = 0,
			expansionCount_ = 0, expandedInferencesCount_ = 0;

	public ResolutionJustificationComputation(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final Monitor monitor, final SelectionFunction<C, A> selection) {
		super(inferences, monitor);
		this.selection_ = selection;
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			final Listener<A> listener) {
		Util.checkNotNull(listener);

		this.toDoJobs_ = new PriorityQueue<>(INITIAL_QUEUE_CAPACITY_,
				extendToJobOrder(order));
		this.minimalJobs_.clear();
		this.minimalJustifications_.clear();
		this.jobsBySelectedConclusions_.clear();
		this.jobsBySelectedPremises_.clear();
		this.listener_ = listener;

		initialize(conclusion);
		process();

		this.listener_ = null;
	}

	private void initialize(final C goal) {
		this.goal_ = goal;
		// produce jobs for all inferences that can be used to derive the goal
		Set<C> done = new HashSet<>();
		Queue<C> todo = new ArrayDeque<>();
		done.add(goal);
		todo.add(goal);
		for (;;) {
			C next = todo.poll();
			if (next == null) {
				return;
			}
			for (JustifiedInference<C, A> inf : getInferences(next)) {
				produce(new Job<C, A>(inf));
				for (C premise : inf.getPremises()) {
					if (done.add(premise)) {
						todo.add(premise);
					}
				}
			}
		}
	}

	private void process() {
		Job<C, A> job;
		while ((job = toDoJobs_.poll()) != null) {
			if (minimalJustifications_.isMinimal(job.justification_)
					&& minimalJobs_.isMinimal(job)) {
				minimalJobs_.add(job);
				if (job.premises_.isEmpty() && goal_.equals(job.conclusion_)) {
					minimalJustifications_.add(job.justification_);
					if (listener_ != null) {
						listener_.newJustification(job.justification_);
					}
				} else {
					C selected = selection_.selectResolvent(job,
							getInferenceSet(), goal_);
					if (selected == null) {
						// resolve on the conclusions
						selected = job.conclusion_;
						jobsBySelectedConclusions_.put(selected, job);
						for (Job<C, A> other : jobsBySelectedPremises_
								.get(selected)) {
							produce(job.resolve(other));
						}
					} else {
						// resolve on the selected premise
						jobsBySelectedPremises_.put(selected, job);
						for (Job<C, A> other : jobsBySelectedConclusions_
								.get(selected)) {
							produce(other.resolve(job));
						}
					}
				}
			} else {
				nonMinimalJobsCount_++;
			}

			if (monitor_.isCancelled()) {
				break;
			}

		}
	}

	private void produce(final Job<C, A> job) {
		if (job.premises_.contains(job.conclusion_)) {
			// skip tautologies
			return;
		}
		producedJobsCount_++;
		toDoJobs_.add(job);
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
	 * A derived inference obtained from either original inferences or
	 * resolution between inferences on conclusions and premises.
	 * 
	 * @author Peter Skocovsky
	 * @author Yevgeny Kazakov
	 */
	public static class Job<C, A> extends AbstractSet<JobMember<C, A>> {

		private final C conclusion_;
		private final Set<C> premises_;
		private final Set<A> justification_;

		private Job(C conclusion, Set<C> premises, Set<A> justification) {
			this.conclusion_ = conclusion;
			this.premises_ = premises;
			this.justification_ = justification;
		}

		public Job(JustifiedInference<C, A> inference) {
			this(inference.getConclusion(),
					ImmutableSet.copyOf(inference.getPremises()),
					ImmutableSet.copyOf(inference.getJustification()));
		}

		public C getConclusion() {
			return conclusion_;
		}

		public Set<C> getPremises() {
			return premises_;
		}

		public Set<A> getJustification() {
			return justification_;
		}

		public Job<C, A> resolve(Job<C, A> other) {
			Set<C> newPremises;
			if (other.premises_.size() == 1) {
				newPremises = premises_;
			} else {
				newPremises = ImmutableSet.copyOf(Iterables.concat(premises_,
						Sets.difference(other.premises_,
								Collections.singleton(conclusion_))));
			}
			return new Job<C, A>(other.conclusion_, newPremises,
					union(justification_, other.justification_));
		}

		<O> Set<O> union(Set<O> first, Set<O> second) {
			if (first.isEmpty()) {
				return second;
			}
			// else
			if (second.isEmpty()) {
				return first;
			}
			// else create
			Set<O> result = ImmutableSet
					.copyOf(Iterables.concat(first, second));
			return result;
		}

		@Override
		public Iterator<JobMember<C, A>> iterator() {
			return Iterators.<JobMember<C, A>> concat(
					Iterators.singletonIterator(
							new Conclusion<C, A>(conclusion_)),
					Iterators.transform(premises_.iterator(),
							new Function<C, Premise<C, A>>() {

								@Override
								public Premise<C, A> apply(C premise) {
									return new Premise<C, A>(premise);
								}

							}),
					Iterators.transform(justification_.iterator(),
							new Function<A, Axiom<C, A>>() {

								@Override
								public Axiom<C, A> apply(final A axiom) {
									return new Axiom<C, A>(axiom);
								}

							}));
		}

		@Override
		public boolean containsAll(final Collection<?> c) {
			if (c instanceof Job<?, ?>) {
				final Job<?, ?> other = (Job<?, ?>) c;
				return conclusion_.equals(other.conclusion_)
						&& premises_.containsAll(other.premises_)
						&& justification_.containsAll(other.justification_);
			}
			// else
			return super.containsAll(c);
		}

		<CC, AA> boolean contains(JobMember<CC, AA> other) {
			return other.accept(new JobMember.Visitor<CC, AA, Boolean>() {

				@Override
				public Boolean visit(Axiom<CC, AA> axiom) {
					return justification_.contains(axiom.getDelegate());
				}

				@Override
				public Boolean visit(Conclusion<CC, AA> conclusion) {
					return conclusion_.equals(conclusion.getDelegate());
				}

				@Override
				public Boolean visit(Premise<CC, AA> premise) {
					return premises_.contains(premise.getDelegate());
				}

			});

		}

		@Override
		public boolean contains(final Object o) {
			if (o instanceof JobMember<?, ?>) {
				return contains((JobMember<?, ?>) o);
			}
			// else
			return false;
		}

		@Override
		public int size() {
			return premises_.size() + justification_.size() + 1;
		}

		@Override
		public String toString() {
			return conclusion_.toString() + " -| " + premises_.toString() + ": "
					+ justification_.toString();
		}

	}

	private interface JobMember<C, A> {

		<O> O accept(Visitor<C, A, O> visitor);

		interface Visitor<C, A, O> {

			O visit(Axiom<C, A> axiom);

			O visit(Conclusion<C, A> conclusion);

			O visit(Premise<C, A> premise);
		}

	}

	private static final class Axiom<C, A> extends Delegator<A>
			implements JobMember<C, A> {

		public Axiom(A delegate) {
			super(delegate);
		}

		@Override
		public <O> O accept(JobMember.Visitor<C, A, O> visitor) {
			return visitor.visit(this);
		}

	}

	private static final class Conclusion<C, A> extends Delegator<C>
			implements JobMember<C, A> {

		public Conclusion(final C delegate) {
			super(delegate);
		}

		@Override
		public <O> O accept(JobMember.Visitor<C, A, O> visitor) {
			return visitor.visit(this);
		}

	}

	private static final class Premise<C, A> extends Delegator<C>
			implements JobMember<C, A> {

		public Premise(final C delegate) {
			super(delegate);
		}

		@Override
		public <O> O accept(JobMember.Visitor<C, A, O> visitor) {
			return visitor.visit(this);
		}

	}

	public static interface SelectionFunction<C, A> {

		/**
		 * Selects the conclusion or one of the premises of the job
		 * 
		 * @param job
		 * @return {@code null} if the conclusion is selected or the selected
		 *         premise
		 */
		C selectResolvent(Job<C, A> job,
				GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
				C goal);

	}

	private Comparator<Job<C, A>> extendToJobOrder(
			final Comparator<? super Set<A>> order) {

		final Comparator<? super Set<A>> justOrder;
		if (order == null) {
			justOrder = DEFAULT_ORDER;
		} else {
			justOrder = order;
		}

		return new Comparator<Job<C, A>>() {

			@Override
			public int compare(final Job<C, A> job1, final Job<C, A> job2) {
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

	/**
	 * The factory for creating computations
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
				final Monitor monitor) {
//			return new ResolutionJustificationComputation<>(inferenceSet,
//					monitor, new BottomUpSelection<C, A>());
//			return new ResolutionJustificationComputation<>(inferenceSet,
//					monitor, new TopDownSelection<C, A>());
			return new ResolutionJustificationComputation<>(inferenceSet,
					monitor, new ThresholdSelection<C, A>());
		}

	}

}
