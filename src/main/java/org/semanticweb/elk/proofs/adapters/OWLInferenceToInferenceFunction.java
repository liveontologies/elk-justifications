package org.semanticweb.elk.proofs.adapters;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapitools.proofs.OWLInference;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;

import com.google.common.base.Function;

/**
 * A simple function transforming {@link OWLInference} to an {@link Inference}
 * with corresponding conclusion and premises and empty justification.
 * 
 * @author Yevgeny Kazakov
 */
class OWLInferenceToInferenceFunction
		implements Function<OWLInference, Inference<OWLExpression, OWLAxiom>> {

	@Override
	public Inference<OWLExpression, OWLAxiom> apply(OWLInference input) {
		return new OWLInferenceInferenceAdapter(input);
	}

}
