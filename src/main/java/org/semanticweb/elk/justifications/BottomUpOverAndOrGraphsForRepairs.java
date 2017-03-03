package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.andorgraph.AndOrGraphs;
import org.semanticweb.elk.justifications.andorgraph.Node;

public class BottomUpOverAndOrGraphsForRepairs<C, A>
		extends CancellableJustificationComputation<C, A> {

	private static final BottomUpOverAndOrGraphsForRepairs.Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	private final BottomUpOverAndOrGraphs<A> computation_;

	public BottomUpOverAndOrGraphsForRepairs(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
			final Monitor monitor) {
		super(inferenceSet, monitor);
		this.computation_ = new BottomUpOverAndOrGraphs<>(monitor);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C conclusion) {
		final Node<A> goal = AndOrGraphs.getDual(AndOrGraphs
				.getAndOrGraphForJustifications(conclusion, getInferenceSet()));
		return computation_.computeJustifications(goal);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C conclusion, final int sizeLimit) {
		final Node<A> goal = AndOrGraphs.getDual(AndOrGraphs
				.getAndOrGraphForJustifications(conclusion, getInferenceSet()));
		return computation_.computeJustifications(goal, sizeLimit);
	}

	private static class Factory<C, A>
			implements JustificationComputation.Factory<C, A> {

		@Override
		public JustificationComputation<C, A> create(
				final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
				final Monitor monitor) {
			return new BottomUpOverAndOrGraphsForRepairs<>(inferenceSet,
					monitor);
		}

	}

}
