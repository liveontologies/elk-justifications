package org.semanticweb.elk.justifications;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.DerivedInference;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.SelectionFunction;

public class BottomUpSelection<C, A> implements SelectionFunction<C, A> {

	@Override
	public C getResolvingAtom(DerivedInference<C, A> inference,
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			C goal) {
		// select the premise that is derived by the fewest inferences;
		// if there are no premises, select the conclusion
		C result = null;
		int minInferenceCount = Integer.MAX_VALUE;
		for (C c : inference.getPremises()) {
			int inferenceCount = inferences.getInferences(c).size();
			if (inferenceCount < minInferenceCount) {
				result = c;
				minInferenceCount = inferenceCount;
			}
		}
		return result;
	}

}
