package org.semanticweb.elk.proofs.adapters;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.liveontologies.proof.util.ProofNode;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferencePrinter;
import org.semanticweb.owlapi.model.OWLAxiom;

/**
 * An adapter from an {@link OWLAxiomExpression} to an {@link Inference} that
 * has this expression as a conclusion, has no premises, and has a singleton
 * justification consisting of the associated axiom.
 * 
 * @see OWLAxiomExpression#getAxiom()
 * 
 * @author Yevgeny Kazakov
 */
class OWLAxiomExpressionInferenceAdapter
		extends AbstractAdapter<ProofNode<OWLAxiom>>
		implements Inference<ProofNode<OWLAxiom>, OWLAxiom> {

	OWLAxiomExpressionInferenceAdapter(ProofNode<OWLAxiom> expression) {
		super(expression);
	}

	@Override
	public ProofNode<OWLAxiom> getConclusion() {
		return adapted_;
	}

	@Override
	public Collection<? extends ProofNode<OWLAxiom>> getPremises() {
		return Collections.emptyList();
	}

	@Override
	public Set<? extends OWLAxiom> getJustification() {
		return Collections.singleton(adapted_.getMember());
	}

	@Override
	public String toString() {
		return InferencePrinter.toString(this);
	}

}
