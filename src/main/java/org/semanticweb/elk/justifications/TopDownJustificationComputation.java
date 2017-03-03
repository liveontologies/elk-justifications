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

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.liveontologies.puli.collections.ArrayListCollection2;
import org.liveontologies.puli.collections.Collection2;

import com.google.common.collect.Iterators;

/**
 * TODO: For OWL API inference sets it happens that some axioms are equal to
 * some conclusions (because they have the same type). That's why
 * {@link TopDownJustificationComputation#Job} is currently implemented not most
 * efficiently.
 * 
 * @author Peter Skocovsky
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class TopDownJustificationComputation<C, A>
		extends CancellableJustificationComputation<C, A> {

	private static final TopDownJustificationComputation.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	/**
	 * newly computed jobs to be propagated
	 */
	private final Queue<Job> toDoJobs_ = new PriorityQueue<Job>();

	/**
	 * Used to minimize the jobs
	 */
	private final Collection2<Set<Object>> minimalJobs_ = new ArrayListCollection2<>();

	private final Collection2<Set<A>> minimalJustifications_ = new ArrayListCollection2<Set<A>>();

	/**
	 * used to select the conclusion to expand
	 */
	private Comparator<C> rank_;

	private TopDownJustificationComputation(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final Monitor monitor) {
		super(inferences, monitor);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C conclusion) {
		return computeJustifications(conclusion, Integer.MAX_VALUE);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C conclusion, final int sizeLimit) {
		initialize(conclusion);
		final InferenceSet<C> inferences = getInferenceSet();
		rank_ = new Comparator<C>() {
			@Override
			public int compare(C first, C second) {
				int result = Integer.compare(
						inferences.getInferences(first).size(),
						inferences.getInferences(second).size());
				if (result != 0) {
					return result;
				}
				// else
				return Integer.compare(first.hashCode(), second.hashCode());
			}
		};
		process();
//		ArrayListCollection2.printStatistics();
		return minimalJustifications_;
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
				} else {
					for (final JustifiedInference<C, A> inf : getInferences(
							chooseConclusion(job.premises_))) {
						final Job newJob = job.expand(inf);
						produce(newJob);
					}
				}
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
		toDoJobs_.offer(job);
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

		public Job expand(final JustifiedInference<C, A> inference) {
			final Set<C> newConclusions = new HashSet<>(premises_);
			newConclusions.remove(inference.getConclusion());
			newConclusions.addAll(inference.getPremises());
			Set<A> newJustification = justification_;
			Set<? extends A> toExpand = inference.getJustification();
			if (newJustification.containsAll(toExpand)) {
				newJustification = justification_;
			} else {
				newJustification = new HashSet<A>(justification_.size());
				newJustification.addAll(justification_);
				newJustification.addAll(toExpand);
			}
			return new Job(newConclusions, newJustification);
		}

		@Override
		public Iterator<Object> iterator() {
			return Iterators.concat(premises_.iterator(),
					justification_.iterator());
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
				final Monitor monitor) {
			return new TopDownJustificationComputation<>(inferenceSet, monitor);
		}

	}

}
