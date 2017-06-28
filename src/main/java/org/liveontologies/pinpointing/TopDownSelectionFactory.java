package org.liveontologies.pinpointing;

import org.liveontologies.puli.pinpointing.ResolutionJustificationComputation;

public class TopDownSelectionFactory<C, A>
		implements ResolutionJustificationComputation.SelectionFactory<C, A> {

	@Override
	public ResolutionJustificationComputation.Selection<C, A> createSelection(
			final ResolutionJustificationComputation<C, A> computation) {
		return computation.new TopDownSelection();
	}

}
