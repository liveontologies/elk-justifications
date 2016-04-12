package org.semanticweb.elk.proofs.adapters;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferencePrinter;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapitools.proofs.OWLInference;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;

/**
 * An adapter from an {@link OWLInference} to an {@link Inference} that has the
 * corresponding conclusion and premises of this inference and the empty
 * justification.
 * 
 * @see OWLInference#getConclusion()
 * @see OWLInference#getPremises()
 * 
 * @author Yevgeny Kazakov
 */
class OWLInferenceInferenceAdapter
		implements Inference<OWLExpression, OWLAxiom> {

	private final OWLInference inference_;

	OWLInferenceInferenceAdapter(OWLInference inference) {
		this.inference_ = inference;
	}

	@Override
	public OWLExpression getConclusion() {
		return inference_.getConclusion();
	}

	@Override
	public Collection<? extends OWLExpression> getPremises() {
		return inference_.getPremises();
	}

	@Override
	public Set<? extends OWLAxiom> getJustification() {
		return Collections.emptySet();
	}

	@Override
	public String toString() {
		return InferencePrinter.toString(this);
	}

}
