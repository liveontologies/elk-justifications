package org.semanticweb.elk.justifications;

import org.liveontologies.puli.pinpointing.ResolutionJustificationComputation;
import org.liveontologies.puli.pinpointing.ResolutionJustificationComputation.Selection;
import org.liveontologies.puli.pinpointing.ResolutionJustificationComputation.SelectionFactory;

public class BottomUpSelectionFactory<C, A> implements SelectionFactory<C, A> {

	@Override
	public Selection<C, A> createSelection(
			final ResolutionJustificationComputation<C, A> computation) {
		return computation.new BottomUpSelection();
	}

}
