package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.BinarizedInferenceSetAdapter;

/**
 * The {@link BottomUpJustificationComputation} applied to the binarization of
 * the input inference set.
 * 
 * @see BinarizedInferenceSetAdapter
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 * @param <A>
 */
public class BinarizedBottomUpJustificationComputation<C, A>
		extends JustificationComputation<C, A> {

	private final JustificationComputation<List<C>, A> computaiton_;

	public BinarizedBottomUpJustificationComputation(
			InferenceSet<C, A> inferences) {
		super(inferences);
		computaiton_ = new BottomUpJustificationComputation<List<C>, A>(
				new BinarizedInferenceSetAdapter<C, A>(inferences));
	}

	@Override
	public Collection<Set<A>> computeJustifications(C conclusion)
			throws InterruptedException {
		return computaiton_
				.computeJustifications(Collections.singletonList(conclusion));
	}

	@Override
	public void logStatistics() {
		computaiton_.logStatistics();
	}

}
