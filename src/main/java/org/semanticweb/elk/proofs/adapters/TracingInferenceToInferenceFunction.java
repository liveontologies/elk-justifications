package org.semanticweb.elk.proofs.adapters;

import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.TracingInference;

import com.google.common.base.Function;

/**
 * A simple function transforming {@link TracingInference} to an
 * {@link Inference} with corresponding conclusion, premises, and justification.
 * 
 * @author Yevgeny Kazakov
 */
class TracingInferenceToInferenceFunction
		implements Function<TracingInference, Inference<Conclusion, ElkAxiom>> {

	@Override
	public Inference<Conclusion, ElkAxiom> apply(TracingInference input) {
		return new TracingInferenceInferenceAdapter(input);
	}

}
