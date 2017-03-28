package org.semanticweb.elk.justifications;

import java.util.Comparator;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.AbstractJustificationComputation;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.andorgraph.AndOrGraphs;
import org.semanticweb.elk.justifications.andorgraph.Node;

public class BottomUpOverAndOrGraphsForRepairs<C, A>
		extends AbstractJustificationComputation<C, A> {

	private static final BottomUpOverAndOrGraphsForRepairs.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	private final BottomUpOverAndOrGraphs<A> computation_;

	public BottomUpOverAndOrGraphsForRepairs(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final InterruptMonitor monitor) {
		super(inferenceSet, justifier, monitor);
		this.computation_ = new BottomUpOverAndOrGraphs<>(monitor);
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			final Listener<A> listener) {
		final Node<A> goal = AndOrGraphs
				.getDual(AndOrGraphs.getAndOrGraphForJustifications(conclusion,
						getInferenceSet(), getInferenceJustifier()));
		computation_.enumerateJustifications(goal, order, listener);
	}

	private static class Factory<C, A>
			implements JustificationComputation.Factory<C, A> {

		@Override
		public JustificationComputation<C, A> create(
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
				final InterruptMonitor monitor) {
			return new BottomUpOverAndOrGraphsForRepairs<>(inferenceSet,
					justifier, monitor);
		}

	}

}
