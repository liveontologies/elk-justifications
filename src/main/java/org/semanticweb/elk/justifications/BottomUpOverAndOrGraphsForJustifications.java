package org.semanticweb.elk.justifications;

import java.util.Comparator;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.liveontologies.puli.justifications.AbstractJustificationComputation;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.semanticweb.elk.justifications.andorgraph.AndOrGraphs;
import org.semanticweb.elk.justifications.andorgraph.Node;

public class BottomUpOverAndOrGraphsForJustifications<C, A>
		extends AbstractJustificationComputation<C, A> {

	private static final BottomUpOverAndOrGraphsForJustifications.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	private final BottomUpOverAndOrGraphs<A> computation_;

	public BottomUpOverAndOrGraphsForJustifications(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
			final InterruptMonitor monitor) {
		super(inferenceSet, monitor);
		this.computation_ = new BottomUpOverAndOrGraphs<>(monitor);
	}

	@Override
	public void enumerateJustifications(final C conclusion,
			final Comparator<? super Set<A>> order,
			final Listener<A> listener) {
		final Node<A> goal = AndOrGraphs
				.getAndOrGraphForJustifications(conclusion, getInferenceSet());
		computation_.enumerateJustifications(goal, order, listener);
	}

	private static class Factory<C, A>
			implements JustificationComputation.Factory<C, A> {

		@Override
		public JustificationComputation<C, A> create(
				final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
				final InterruptMonitor monitor) {
			return new BottomUpOverAndOrGraphsForJustifications<>(inferenceSet,
					monitor);
		}

	}

}
