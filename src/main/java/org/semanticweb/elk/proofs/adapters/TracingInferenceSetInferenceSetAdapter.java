package org.semanticweb.elk.proofs.adapters;

import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.TracingInference;
import org.semanticweb.elk.reasoner.tracing.TracingInferenceSet;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/**
 * An adapter from {@link TracingInferenceSet} to an {@link InferenceSet} that
 * returns inferences over {@link Conclusion}s with justifications consisting of
 * {@link ElkAxiom}s, that are converted from the corresponding
 * {@link TracingInference}s.
 * 
 * @author Yevgeny Kazakov
 */
public class TracingInferenceSetInferenceSetAdapter
		implements InferenceSet<Conclusion, ElkAxiom> {

	private final static Function<TracingInference, Inference<Conclusion, ElkAxiom>> FUNCTION_ = new TracingInferenceToInferenceFunction();

	private final TracingInferenceSet inferenceSet_;

	public TracingInferenceSetInferenceSetAdapter(
			TracingInferenceSet inferenceSet) {
		this.inferenceSet_ = inferenceSet;
	}

	@Override
	public Iterable<Inference<Conclusion, ElkAxiom>> getInferences(
			Conclusion conclusion) {
		return Iterables.transform(inferenceSet_.getInferences(conclusion),
				FUNCTION_);
	}

}
