package org.semanticweb.elk.justifications;

import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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
import com.google.common.collect.ForwardingSet;
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

	private static final int INITIAL_QUEUE_CAPACITY_ = 256;

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	/**
	 * Conclusions for which computation of justifications has been initialized
	 */
	private final Set<C> initialized_ = new HashSet<>();

	/**
	 * a structure used to check inferences for minimality; an inference is
	 * minimal if there was no other inference with the same conclusion, subset
	 * of premises and subset of justirication produced
	 */
	private final Map<C, Collection2<DerivedInference<C, A>>> minimalInferencesByConclusions_ = new HashMap<>();

	private final ListMultimap<C, DerivedInference<C, A>>
	// inferences whose conclusions are selected, indexed by this conclusion
	inferencesBySelectedConclusions_ = ArrayListMultimap.create(),
			// inferences whose premise is selected, indexed by this premise
			inferencesBySelectedPremises_ = ArrayListMultimap.create();

	/**
	 * inferences that are not necessary for computing the justifications for
	 * the current goal; these are (possibly minimal) inferences whose
	 * justification is a superset of a justification for the goal
	 */
	private Queue<DerivedInference<C, A>> blockedInferences_ = new ArrayDeque<>();

	/**
	 * a function used for selecting conclusions in inferences on which to
	 * resolve
	 */
	private final SelectionFunction<C, A> selection_;

	// Statistics
	private int producedInferenceCount_ = 0, minimalInferenceCount_ = 0;

	public ResolutionJustificationComputation(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final Monitor monitor, final SelectionFunction<C, A> selection) {
		super(inferences, monitor);
		this.selection_ = selection;
	}

	Collection2<DerivedInference<C, A>> getMinimalInferences(C conclusion) {
		Collection2<DerivedInference<C, A>> result = minimalInferencesByConclusions_
				.get(conclusion);
		if (result == null) {
			result = new BloomTrieCollection2<>();
			minimalInferencesByConclusions_.put(conclusion, result);
		}
		return result;
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			final Listener<A> listener) {
		new JustificationEnumerator(conclusion, order, listener).process();
	}

	@Stat
	public int nProducedInferences() {
		return producedInferenceCount_;
	}

	@Stat
	public int nMinimalInferences() {
		return minimalInferenceCount_;
	}

	@ResetStats
	public void resetStats() {
		producedInferenceCount_ = 0;
		minimalInferenceCount_ = 0;
	}

	@NestedStats
	public static Class<?> getNestedStats() {
		return BloomTrieCollection2.class;
	}

	interface InferenceHolder<C, A> {

		/**
		 * @return the inference represented by this object
		 */
		DerivedInference<C, A> getInference();

		/**
		 * @return the justification for the result of {@link #getInference()}
		 *         (without need to compute the letter)
		 * 
		 */
		Set<A> getJustification();

		/**
		 * @return the number of premises of for the result of
		 *         {@link #getInference()} (without computing the letter)
		 */
		int getPremiseCount();

		/**
		 * @return {@code true} if {@link #getInference()} returns a
		 *         tautological inference, i.e. its conclusion is one of the
		 *         premises.
		 */
		boolean isATautology();

	}

	/**
	 * A derived inference obtained from either original inferences or
	 * resolution between two inferences on the conclusion and a premise.
	 * 
	 * @author Peter Skocovsky
	 * @author Yevgeny Kazakov
	 */
	public static class DerivedInference<C, A>
			extends AbstractSet<DerivedInferenceMember<C, A>>
			implements InferenceHolder<C, A> {

		private final C conclusion_;
		private final Set<C> premises_;
		private final Set<A> justification_;
		/**
		 * {@code true} if the inference was checked for minimality
		 */
		private boolean isMinimal_ = false;

		private DerivedInference(C conclusion, Set<C> premises,
				Set<A> justification) {
			this.conclusion_ = conclusion;
			this.premises_ = premises;
			this.justification_ = justification;
		}

		public DerivedInference(JustifiedInference<C, A> inference) {
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

		@Override
		public Set<A> getJustification() {
			return justification_;
		}

		@Override
		public int getPremiseCount() {
			return premises_.size();
		}

		@Override
		public boolean isATautology() {
			return premises_.contains(conclusion_);
		}

		@Override
		public DerivedInference<C, A> getInference() {
			return this;
		}

		public DerivedInference<C, A> resolveWith(
				DerivedInference<C, A> other) {
			Set<C> newPremises;
			if (other.premises_.size() == 1) {
				newPremises = premises_;
			} else {
				newPremises = ImmutableSet.copyOf(Iterables.concat(premises_,
						Sets.difference(other.premises_,
								Collections.singleton(conclusion_))));
			}
			return new DerivedInference<C, A>(other.conclusion_, newPremises,
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
		public Iterator<DerivedInferenceMember<C, A>> iterator() {
			return Iterators.<DerivedInferenceMember<C, A>> concat(
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
			if (c instanceof DerivedInference<?, ?>) {
				final DerivedInference<?, ?> other = (DerivedInference<?, ?>) c;
				return conclusion_.equals(other.conclusion_)
						&& premises_.containsAll(other.premises_)
						&& justification_.containsAll(other.justification_);
			}
			// else
			return super.containsAll(c);
		}

		<CC, AA> boolean contains(DerivedInferenceMember<CC, AA> other) {
			return other.accept(
					new DerivedInferenceMember.Visitor<CC, AA, Boolean>() {

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
			if (o instanceof DerivedInferenceMember<?, ?>) {
				return contains((DerivedInferenceMember<?, ?>) o);
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

	private interface DerivedInferenceMember<C, A> {

		<O> O accept(Visitor<C, A, O> visitor);

		interface Visitor<C, A, O> {

			O visit(Axiom<C, A> axiom);

			O visit(Conclusion<C, A> conclusion);

			O visit(Premise<C, A> premise);
		}

	}

	private static final class Axiom<C, A> extends Delegator<A>
			implements DerivedInferenceMember<C, A> {

		public Axiom(A delegate) {
			super(delegate);
		}

		@Override
		public <O> O accept(DerivedInferenceMember.Visitor<C, A, O> visitor) {
			return visitor.visit(this);
		}

	}

	private static final class Conclusion<C, A> extends Delegator<C>
			implements DerivedInferenceMember<C, A> {

		public Conclusion(final C delegate) {
			super(delegate);
		}

		@Override
		public <O> O accept(DerivedInferenceMember.Visitor<C, A, O> visitor) {
			return visitor.visit(this);
		}

	}

	private static final class Premise<C, A> extends Delegator<C>
			implements DerivedInferenceMember<C, A> {

		public Premise(final C delegate) {
			super(delegate);
		}

		@Override
		public <O> O accept(DerivedInferenceMember.Visitor<C, A, O> visitor) {
			return visitor.visit(this);
		}

	}

	/**
	 * The result of resolution applied to two {@link DerivedInference}s. The
	 * resulting inference returned by {@link #getInference()} is computed on
	 * demand to prevent unnecessary memory consumption when this object is
	 * stored in the produced inference queue.
	 * 
	 * @author Yevgeny Kazakov
	 *
	 * @param <C>
	 * @param <A>
	 */
	static class Resolvent<C, A> extends ForwardingSet<A>
			implements InferenceHolder<C, A> {

		private final DerivedInference<C, A> firstInference_, secondInference_;

		private final Set<A> justification_; // lazy representation

		/**
		 * cached size of {@link #justification_}
		 */
		private final int justificationSize_;

		private final int premiseCount_;

		Resolvent(DerivedInference<C, A> firstInference,
				DerivedInference<C, A> secondInference) {
			if (firstInference.isATautology()
					|| secondInference.isATautology()) {
				throw new IllegalArgumentException(
						"Cannot resolve on tautologies!");
			}
			this.firstInference_ = firstInference;
			this.secondInference_ = secondInference;
			this.justification_ = Sets.union(firstInference.justification_,
					secondInference.justification_);
			this.justificationSize_ = justification_.size();
			// correct when the first resolving inference is not a tautology
			this.premiseCount_ = Sets.union(firstInference_.premises_,
					secondInference_.premises_).size() - 1;
		}

		@Override
		public DerivedInference<C, A> getInference() {
			return firstInference_.resolveWith(secondInference_);
		}

		@Override
		public Set<A> getJustification() {
			return this;
		}

		@Override
		public int getPremiseCount() {
			return premiseCount_;
		}

		@Override
		public boolean isATautology() {
			// correct when the second inference is not a tautology
			return firstInference_.premises_
					.contains(secondInference_.conclusion_);
		}

		@Override
		public int size() {
			return justificationSize_;
		}

		@Override
		protected Set<A> delegate() {
			return justification_;
		}

	}

	private class JustificationEnumerator {

		/**
		 * the conclusion for which to enumerate justifications
		 */
		private final C goal_;

		/**
		 * the listener through which to report the justifications
		 */
		private final Listener<A> listener_;

		/**
		 * newly computed inferences to be resolved upon
		 */
		private final Queue<InferenceHolder<C, A>> producedInferences_;

		/**
		 * to check minimality of justifications
		 */
		private final Collection2<Set<A>> minimalJustifications_ = new BloomTrieCollection2<>();

		/**
		 * a temporary queue used to initialize computation of justifications
		 * for conclusions that are not yet {@link #initialized_}
		 */
		private final Queue<C> toInitialize_ = new ArrayDeque<>();

		JustificationEnumerator(C goal, Comparator<? super Set<A>> order,
				Listener<A> listener) {
			Util.checkNotNull(listener);
			this.goal_ = goal;
			this.listener_ = listener;
			this.producedInferences_ = new PriorityQueue<>(
					INITIAL_QUEUE_CAPACITY_, getOrderExtension(order));
			initialize();
			unblockJobs();
			changeSelection();
		}

		void initialize() {
			toInitialize(goal_);
			for (;;) {
				C next = toInitialize_.poll();
				if (next == null) {
					return;
				}
				for (JustifiedInference<C, A> inf : getInferences(next)) {
					produce(new DerivedInference<C, A>(inf));
					for (C premise : inf.getPremises()) {
						toInitialize(premise);
					}
				}
			}
		}

		void toInitialize(C conclusion) {
			if (initialized_.add(conclusion)) {
				toInitialize_.add(conclusion);
			}
		}

		void unblockJobs() {
			for (;;) {
				DerivedInference<C, A> inf = blockedInferences_.poll();
				if (inf == null) {
					return;
				}
				// else
				produce(inf);
			}
		}

		void changeSelection() {
			// selection for inferences with selected goal must change
			for (DerivedInference<C, A> inf : inferencesBySelectedConclusions_
					.removeAll(goal_)) {
				produce(inf);
			}
		}

		void block(DerivedInference<C, A> inf) {
			blockedInferences_.add(inf);
		}

		private void process() {
			for (;;) {
				if (monitor_.isCancelled()) {
					break;
				}
				InferenceHolder<C, A> next = producedInferences_.poll();
				if (next == null) {
					break;
				}
				DerivedInference<C, A> inf = next.getInference();
				if (!minimalJustifications_.isMinimal(inf.justification_)) {
					block(inf);
					continue;
				}
				// else
				if (inf.premises_.isEmpty() && goal_.equals(inf.conclusion_)) {
					minimalJustifications_.add(inf.justification_);
					listener_.newJustification(inf.justification_);
					block(inf);
					continue;
				}
				// else
				if (!inf.isMinimal_) {
					Collection2<DerivedInference<C, A>> minimalInferences = getMinimalInferences(
							inf.conclusion_);
					if (!minimalInferences.isMinimal(inf)) {
						continue;
					}
					// else
					inf.isMinimal_ = true;
					minimalInferences.add(inf);
					minimalInferenceCount_++;
				}
				C selected = selection_.getResolvingAtom(inf, getInferenceSet(),
						goal_);
				if (selected == null) {
					// resolve on the conclusions
					selected = inf.conclusion_;
					if (goal_.equals(selected)) {
						throw new RuntimeException(
								"Goal conclusion cannot be selected if the inference has premises!");
					}
					inferencesBySelectedConclusions_.put(selected, inf);
					for (DerivedInference<C, A> other : inferencesBySelectedPremises_
							.get(selected)) {
						produce(new Resolvent<C, A>(inf, other));
					}
				} else {
					// resolve on the selected premise
					inferencesBySelectedPremises_.put(selected, inf);
					for (DerivedInference<C, A> other : inferencesBySelectedConclusions_
							.get(selected)) {
						produce(new Resolvent<C, A>(other, inf));
					}
				}
			}

		}

		private void produce(InferenceHolder<C, A> resolvent) {
			if (resolvent.isATautology()) {
				// skip tautologies
				return;
			}
			producedInferenceCount_++;
			producedInferences_.add(resolvent);
		}

	}

	public static interface SelectionFunction<C, A> {

		/**
		 * Selects the conclusion or one of the premises of the inference; only
		 * this atom is used in the resolution rule
		 * 
		 * @param inference
		 * @return {@code null} if the conclusion is selected or the selected
		 *         premise
		 */
		C getResolvingAtom(DerivedInference<C, A> inference,
				GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
				C goal);

	}

	private Comparator<InferenceHolder<C, A>> getOrderExtension(
			final Comparator<? super Set<A>> order) {

		final Comparator<? super Set<A>> justOrder;
		if (order == null) {
			justOrder = DEFAULT_ORDER;
		} else {
			justOrder = order;
		}

		return new Comparator<InferenceHolder<C, A>>() {

			@Override
			public int compare(final InferenceHolder<C, A> first,
					final InferenceHolder<C, A> second) {
				int result = justOrder.compare(first.getJustification(),
						second.getJustification());
				if (result != 0) {
					return result;
				}
				// else
				result = Integer.compare(first.getPremiseCount(),
						second.getPremiseCount());
				return result;
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
			// return new ResolutionJustificationComputation<>(inferenceSet,
			// monitor, new BottomUpSelection<C, A>());
			// return new ResolutionJustificationComputation<>(inferenceSet,
			// monitor, new TopDownSelection<C, A>());
			return new ResolutionJustificationComputation<>(inferenceSet,
					monitor, new ThresholdSelection<C, A>());
		}

	}

}
