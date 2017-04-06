package org.semanticweb.elk.justifications;

import org.liveontologies.puli.justifications.ResolutionJustificationComputation;

public class TopDownSelectionFactory<C, A>
		implements ResolutionJustificationComputation.SelectionFactory<C, A> {

	@Override
	public ResolutionJustificationComputation.Selection<C, A> createSelection(
			final ResolutionJustificationComputation<C, A> computation) {
		return computation.new TopDownSelection();
	}

}
