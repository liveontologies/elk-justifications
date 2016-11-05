package org.semanticweb.elk.proofs.adapters;

import org.liveontologies.owlapi.proof.OWLProofNode;
import org.liveontologies.owlapi.proof.OWLProofStep;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.owlapi.model.OWLAxiom;

import com.google.common.base.Function;

/**
 * A simple function transforming {@link OWLInference} to an {@link Inference}
 * with corresponding conclusion and premises and empty justification.
 * 
 * @author Yevgeny Kazakov
 */
class OWLInferenceToInferenceFunction
		implements Function<OWLProofStep, Inference<OWLProofNode, OWLAxiom>> {

	@Override
	public Inference<OWLProofNode, OWLAxiom> apply(OWLProofStep input) {
		return new OWLInferenceInferenceAdapter(input);
	}

}
