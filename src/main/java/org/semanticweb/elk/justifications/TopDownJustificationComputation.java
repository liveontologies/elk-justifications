package org.semanticweb.elk.justifications;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;

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
	private final Queue<Job> toDoJobs_ = new LinkedList<Job>();

	/**
	 * Jobs produced so far.
	 */
	private final Collection<Job> doneJobs_ = new ArrayList<>();

	/**
	 * Only for the constructor of {@link BloomSet}. TODO: generalize BloomSet!
	 */
	private C goal_ = null;

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
		goal_ = conclusion;
		initialize(conclusion);
		process();
		final Collection<Set<A>> result = new ArrayList<>();
		for (final Job doneJob : doneJobs_) {
			if (doneJob.getConclusions().isEmpty()) {
				result.add(doneJob.getAxioms());
			}
		}
		return result;
	}

	private void initialize(final C goal) {
		final Job initialJob = new Job(goal);
		produce(initialJob);
	}

	private void process() {
		Job job;
		while ((job = toDoJobs_.poll()) != null) {

			final C conclusion = chooseConclusion(job.getConclusions());
			if (conclusion == null) {
				// No more conclusions to expand.
				continue;
			}
			// else

			for (final JustifiedInference<C, A> inf : getInferences(
					conclusion)) {
				final Job newJob = job.expand(inf);
				produce(newJob);
			}

		}
	}

	private C chooseConclusion(final Set<C> conclusions) {
		if (conclusions.isEmpty()) {
			return null;
		}
		// else
		return conclusions.iterator().next();
	}

	private void produce(final Job job) {
		if (Utils.merge(job, doneJobs_)) {
			// The set is so far minimal.
			toDoJobs_.offer(job);
		}
	}

	/**
	 * A set of conclusions and axioms. If the conclusions are derived, the goal
	 * conclusion can be derived from the axioms using the inferences from the
	 * inference set.
	 * <p>
	 * TODO: This is not really a set, because it may contain the same object in
	 * conclusions and in axioms !!! Needed for OWL API inference sets!
	 * 
	 * @author Peter Skocovsky
	 */
	private class Job extends AbstractSet<Object> {

		private final Set<C> conclusions_;
		private final Set<A> axioms_;

		private Job(final Set<C> conclusions, final Set<A> axioms) {
			this.conclusions_ = conclusions;
			this.axioms_ = axioms;
		}

		public Job(final C goal) {
			this(Collections.singleton(goal), Collections.<A> emptySet());
		}

		public Job expand(final JustifiedInference<C, A> inference) {
			final Set<C> newConclusions = new HashSet<>(conclusions_);
			newConclusions.remove(inference.getConclusion());
			newConclusions.addAll(inference.getPremises());
			final Set<A> newAxioms = new HashSet<>(axioms_);
			newAxioms.addAll(inference.getJustification());
			return new Job(new BloomSet<C, C>(goal_, newConclusions),
					new BloomSet<C, A>(goal_, newAxioms));
		}

		public Set<C> getConclusions() {
			return conclusions_;
		}

		public Set<A> getAxioms() {
			return axioms_;
		}

		@Override
		public Iterator<Object> iterator() {
			return Iterators.concat(conclusions_.iterator(),
					axioms_.iterator());
		}

		@Override
		public boolean contains(final Object o) {
			return conclusions_.contains(o) || axioms_.contains(o);
		}

		@Override
		public boolean containsAll(final Collection<?> c) {
			if (c instanceof TopDownJustificationComputation.Job) {
				@SuppressWarnings("unchecked")
				final Job other = (Job) c;
				return conclusions_.containsAll(other.getConclusions())
						&& axioms_.containsAll(other.getAxioms());
			}
			// else
			return super.containsAll(c);
		}

		@Override
		public int size() {
			return conclusions_.size() + axioms_.size();
		}

	}

	@Override
	public String[] getStatNames() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getStatistics() {
		// TODO Auto-generated method stub
		return null;
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

		@Override
		public String[] getStatNames() {
			final String[] statNames = new String[] {
					// TODO :-P
			};
			final String[] bloomStatNames = BloomSet.getStatNames();
			final String[] ret = Arrays.copyOf(statNames,
					statNames.length + bloomStatNames.length);
			System.arraycopy(bloomStatNames, 0, ret, statNames.length,
					bloomStatNames.length);
			return ret;
		}

	}

}
