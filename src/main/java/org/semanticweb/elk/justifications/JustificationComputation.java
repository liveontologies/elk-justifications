package org.semanticweb.elk.justifications;

import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;

public abstract class JustificationComputation {

	protected final ExplainingOWLReasoner reasoner;

	public JustificationComputation(final ExplainingOWLReasoner reasoner) {
		this.reasoner = reasoner;
	}
	
	public abstract Set<Set<OWLAxiom>> computeJustifications(
			final OWLSubClassOfAxiom conclusion)
					throws ProofGenerationException, InterruptedException;
	
}
