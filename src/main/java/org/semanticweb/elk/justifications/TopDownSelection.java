package org.semanticweb.elk.justifications;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.Job;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation.SelectionFunction;

public class TopDownSelection<C, A> implements SelectionFunction<C, A> {

	@Override
	public C selectResolvent(final Job<C, A> job,
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final C goal) {
		// select the conclusion, unless it is the goal conclusion and
		// there are premises, in which case select the premise derived
		// by the fewest inferences
		C result = null;
		if (goal.equals(job.getConclusion())) {
			int minInferenceCount = Integer.MAX_VALUE;
			for (C c : job.getPremises()) {
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
