package org.semanticweb.elk.proofs.adapters;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.liveontologies.proof.util.ProofNode;
import org.liveontologies.proof.util.ProofStep;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferencePrinter;
import org.semanticweb.owlapi.model.OWLAxiom;

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
class OWLInferenceInferenceAdapter extends AbstractAdapter<ProofStep<OWLAxiom>>
		implements Inference<ProofNode<OWLAxiom>, OWLAxiom> {

	OWLInferenceInferenceAdapter(final ProofStep<OWLAxiom> inference) {
		super(inference);
	}

	@Override
	public ProofNode<OWLAxiom> getConclusion() {
		return adapted_.getConclusion();
	}

	@Override
	public Collection<? extends ProofNode<OWLAxiom>> getPremises() {
		return adapted_.getPremises();
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
