package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.proofs.adapters.InferenceSets;
import org.semanticweb.elk.statistics.NestedStats;

/**
 * The {@link BottomUpJustificationComputation} applied to the binarization of
 * the input inference set.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class BinarizedJustificationComputation<C, A>
		extends AbstractJustificationComputation<C, A> {

	private final JustificationComputation<List<C>, A> computaiton_;

	BinarizedJustificationComputation(
			JustificationComputation.Factory<List<C>, A> mainFactory,
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final Monitor monitor) {
		super(inferences);
		computaiton_ = mainFactory.create(InferenceSets.binarize(inferences),
				monitor);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(C conclusion) {
		return computaiton_
				.computeJustifications(Collections.singletonList(conclusion));
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(C conclusion,
			int sizeLimit) {
		return computaiton_.computeJustifications(
				Collections.singletonList(conclusion), sizeLimit);
	}

	@NestedStats
	public JustificationComputation<List<C>, A> getDelegate() {
		return computaiton_;
	}

	public static <C, A> JustificationComputation.Factory<C, A> getFactory(
			JustificationComputation.Factory<List<C>, A> mainFactory) {
		return new Factory<C, A>(mainFactory);
	}

	private static class Factory<C, A>
			implements JustificationComputation.Factory<C, A> {

		JustificationComputation.Factory<List<C>, A> mainFactory_;

		Factory(JustificationComputation.Factory<List<C>, A> mainFactory) {
			this.mainFactory_ = mainFactory;
		}

		@Override
		public JustificationComputation<C, A> create(
				final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
				final Monitor monitor) {
			return new BinarizedJustificationComputation<C, A>(mainFactory_,
					inferenceSet, monitor);
		}

	}

}
