package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.AbstractJustificationComputation;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.ResetStats;
import org.liveontologies.puli.statistics.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

public class BottomUpJustificationComputation<C, A>
		extends AbstractJustificationComputation<C, A> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(BottomUpJustificationComputation.class);

	private static final int INITIAL_QUEUE_CAPACITY_ = 11;

	private static final BottomUpJustificationComputation.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	/**
	 * conclusions for which computation of justifications has been initialized
	 */
	private final Set<C> initialized_ = new HashSet<C>();

	/**
	 * a map from conclusions to their justifications
	 */
	private final ListMultimap<C, Justification<C, A>> justifications_ = ArrayListMultimap
			.create();

	/**
	 * justifications blocked from propagation because they are not needed for
	 * computing justifications for the goal conclusion
	 */
	private final ListMultimap<C, Justification<C, A>> blockedJustifications_ = ArrayListMultimap
			.create();

	/**
	 * a map from premises to inferences for relevant conclusions
	 */
	private final Multimap<C, Inference<C>> inferencesByPremises_ = ArrayListMultimap
			.create();

	/**
	 * newly computed justifications to be propagated
	 */
	private PriorityQueue<Justification<C, A>> toDoJustifications_;

	private Listener<A> listener_ = null;

	// Statistics

	private int countInferences_ = 0, countConclusions_ = 0,
			countJustificationCandidates_ = 0;

	private BottomUpJustificationComputation(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
		initQueue(null);
	}

	private void reset() {
		initialized_.clear();
		justifications_.clear();
		blockedJustifications_.clear();
		toDoJustifications_ = null;
	}

	private void initQueue(final Comparator<? super Set<A>> order) {
		this.toDoJustifications_ = new PriorityQueue<Justification<C, A>>(
				INITIAL_QUEUE_CAPACITY_, new Order(order));
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			final Listener<A> listener) {
		Preconditions.checkNotNull(listener);
		this.listener_ = listener;

		boolean doNotReset = true;
		if (toDoJustifications_ != null) {
			final Comparator<? super Justification<C, A>> comparator = toDoJustifications_
					.comparator();
			if (comparator != null
					&& (comparator instanceof BottomUpJustificationComputation.Order)) {
				@SuppressWarnings("unchecked")
				final Order oldOrder = (Order) comparator;
				doNotReset = order == null ? oldOrder.originalOrder == null
						: order.equals(oldOrder.originalOrder);
			}
		}

		if (doNotReset) {
			// Visit already computed justifications. They should be in the
			// correct order.
			for (final Justification<C, A> just : justifications_
					.get(conclusion)) {
				listener.newJustification(just);
			}
		} else {
			// Reset everything.
			reset();
		}

		initQueue(order);

		new JustificationEnumerator(conclusion).process();

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
		return blockedJustifications_.size();
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
	}

	@NestedStats
	public static Class<?> getNestedStats() {
		return BloomSet.class;
	}

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
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
	private class JustificationEnumerator {

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
		private final Queue<C> toDo_ = new LinkedList<C>();

		/**
		 * the justifications will be returned here, they come in increasing
		 * size order
		 */
		private final List<? extends Set<A>> result_;

		JustificationEnumerator(C conclusion) {
			this.conclusion_ = conclusion;
			this.result_ = justifications_.get(conclusion);
			toDo(conclusion);
			initialize();
		}

		/**
		 * traverse inferences to find relevant conclusions and create the queue
		 * of justifications to be propagated reusing previously computed
		 * justifications
		 */
		private void initialize() {

			C conclusion;
			while ((conclusion = toDo_.poll()) != null) {

				final Collection<? extends Inference<C>> infs = getInferences(
						conclusion);
				if (infs.isEmpty()) {
					LOGGER_.warn("{}: lemma not derived!", conclusion);
				}

				for (final Inference<C> inf : infs) {
					LOGGER_.trace("{}: new inference", inf);
					countInferences_++;
					for (final C premise : inf.getPremises()) {
						inferencesByPremises_.put(premise, inf);
						toDo(premise);
					}
				}

				if (initialized_.add(conclusion)) {
					LOGGER_.trace(
							"{}: computation of justifiations initialized",
							conclusion);
					// propagate existing justifications for premises
					for (final Inference<C> inf : infs) {
						List<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
						conclusionJusts.add(createJustification(
								inf.getConclusion(), getJustification(inf)));
						for (final C premise : inf.getPremises()) {
							conclusionJusts = Utils.join(conclusionJusts,
									justifications_.get(premise));
						}
						for (final Justification<C, A> just : conclusionJusts) {
							produce(just);
						}
					}
				} else {
					// conclusion has already been initialized.
					final List<Justification<C, A>> blocked = blockedJustifications_
							.get(conclusion);
					for (final Justification<C, A> just : blocked) {
						LOGGER_.trace("unblocked {}", just);
						// Don't produce, blocked justs were already produced.
						toDoJustifications_.add(just);
					}
					blocked.clear();
				}

			}

		}

		private void toDo(C conclusion) {
			if (relevant_.add(conclusion)) {
				countConclusions_++;
				toDo_.add(conclusion);
			}
		}

		/**
		 * process new justifications until the fixpoint
		 */
		private void process() {
			Justification<C, A> just;
			while ((just = toDoJustifications_.poll()) != null) {
				if (isInterrupted()) {
					return;
				}

				C conclusion = just.getConclusion();
				if (!relevant_.contains(conclusion)) {
					blockedJustifications_.put(conclusion, just);
					LOGGER_.trace("blocked {}", just);
					continue;
				}
				List<Justification<C, A>> justs = justifications_
						.get(conclusion);
				if (!Utils.isMinimal(just, justs)) {
					continue;
				}
				if (!Utils.isMinimal(just, result_)) {
					blockedJustifications_.put(conclusion, just);
					LOGGER_.trace("blocked {}", just);
					continue;
				}
				// else
				justs.add(just);
				LOGGER_.trace("new {}", just);
				if (conclusion_.equals(conclusion) && listener_ != null) {
					listener_.newJustification(just);
				}

				if (just.isEmpty()) {
					// all justifications are computed,
					// the inferences are not needed anymore
					for (final Inference<C> inf : getInferences(conclusion)) {
						for (C premise : inf.getPremises()) {
							inferencesByPremises_.remove(premise, inf);
						}
					}
				}

				/*
				 * propagating justification over inferences
				 */
				for (final Inference<C> inf : inferencesByPremises_
						.get(conclusion)) {

					Collection<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
					Justification<C, A> conclusionJust = just
							.copyTo(inf.getConclusion())
							.addElements(getJustification(inf));
					conclusionJusts.add(conclusionJust);
					for (final C premise : inf.getPremises()) {
						if (!premise.equals(conclusion)) {
							conclusionJusts = Utils.join(conclusionJusts,
									justifications_.get(premise));
						}
					}

					for (Justification<C, A> conclJust : conclusionJusts) {
						produce(conclJust);
					}

				}

			}

		}

		private void produce(final Justification<C, A> justification) {
			countJustificationCandidates_++;
			toDoJustifications_.add(justification);
		}

	}

	private class Order implements Comparator<Justification<C, A>> {

		public final Comparator<? super Set<A>> originalOrder;

		private final Comparator<? super Set<A>> setOrder_;

		public Order(final Comparator<? super Set<A>> innerOrder) {
			this.originalOrder = innerOrder;
			if (innerOrder == null) {
				setOrder_ = DEFAULT_ORDER;
			} else {
				setOrder_ = innerOrder;
			}
		}

		@Override
		public int compare(final Justification<C, A> just1,
				final Justification<C, A> just2) {
			final int result = setOrder_.compare(just1, just2);
			if (result != 0) {
				return result;
			}
			return Integer.compare(just1.getConclusion().hashCode(),
					just2.getConclusion().hashCode());
		}

	}

	/**
	 * The factory for creating a {@link BottomUpJustificationComputation}
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
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new BottomUpJustificationComputation<>(inferenceSet,
					justifier, monitor);
		}

	}

}
