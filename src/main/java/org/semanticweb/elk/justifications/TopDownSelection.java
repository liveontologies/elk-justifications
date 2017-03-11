package org.semanticweb.elk.justifications;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.DerivedInference;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.SelectionFunction;

public class TopDownSelection<C, A> implements SelectionFunction<C, A> {

	@Override
	public C getResolvingAtom(DerivedInference<C, A> inference,
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			C goal) {
		// select the conclusion, unless it is the goal conclusion and
		// there are premises, in which case select the premise derived
		// by the fewest inferences
		C result = null;
		if (goal.equals(inference.getConclusion())) {
			int minInferenceCount = Integer.MAX_VALUE;
			for (C c : inference.getPremises()) {
				int inferenceCount = inferences.getInferences(c).size();
				if (inferenceCount < minInferenceCount) {
					result = c;
					minInferenceCount = inferenceCount;
				}
			}
		}
		return result;
	}

}
