package org.semanticweb.elk.proofs.adapters;

import org.liveontologies.puli.ProofNode;
import org.liveontologies.puli.ProofStep;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.owlapi.model.OWLAxiom;

import com.google.common.base.Function;

/**
 * A simple function transforming {@link OWLInference} to an {@link Inference}
 * with corresponding conclusion and premises and empty justification.
 * 
 * @author Yevgeny Kazakov
 */
class OWLInferenceToInferenceFunction implements
		Function<ProofStep<OWLAxiom>, Inference<ProofNode<OWLAxiom>, OWLAxiom>> {

	@Override
	public Inference<ProofNode<OWLAxiom>, OWLAxiom> apply(
			final ProofStep<OWLAxiom> input) {
		return new OWLInferenceInferenceAdapter(input);
	}

}
