package org.semanticweb.elk.justifications;

import org.semanticweb.elk.justifications.ResolutionJustificationComputation.Selection;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.SelectionFactory;

public class TopDownSelectionFactory<C, A> implements SelectionFactory<C, A> {

	@Override
	public Selection<C, A> createSelection(
			final ResolutionJustificationComputation<C, A> computation) {
		return computation.new TopDownSelection();
	}

}
