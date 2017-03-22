package org.semanticweb.elk.justifications;

import org.semanticweb.elk.justifications.ResolutionJustificationComputation.Selection;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.SelectionFactory;

public class ThresholdSelectionFactory<C, A> implements SelectionFactory<C, A> {

	public static final int DEFAULT_THRESHOLD = 2;

	@Override
	public Selection<C, A> createSelection(
			final ResolutionJustificationComputation<C, A> computation) {
		return createSelection(computation, DEFAULT_THRESHOLD);
	}

	public Selection<C, A> createSelection(
			final ResolutionJustificationComputation<C, A> computation,
			final int threshold) {
		return computation.new ThresholdSelection(threshold);
	}

}
