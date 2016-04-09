package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.semanticweb.owlapitools.proofs.OWLInference;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLAxiomExpression;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;
import org.semanticweb.owlapitools.proofs.util.CycleFreeProofRoot;
import org.semanticweb.owlapitools.proofs.util.OWLProofUtils;

public class SimpleJustificationComputation
		extends CancellableJustificationComputation {
	
	public SimpleJustificationComputation(
			final ExplainingOWLReasoner reasoner) {
		super(reasoner);
	}

	@Override
	public Set<Set<OWLAxiom>> computeJustifications(
			final OWLSubClassOfAxiom conclusion)
					throws ProofGenerationException, InterruptedException {
		
		final OWLAxiomExpression expression =
				reasoner.getDerivedExpression(conclusion);
		
		final CycleFreeProofRoot cycleFree = new CycleFreeProofRoot(
				expression, OWLProofUtils.computeInferenceGraph(expression));
		final Set<Set<OWLAxiom>> candidates =
				computeJustificationCandidates(cycleFree);
		
		/* 
		 * If there is a candidate that is a proper subset of a candidate,
		 * the latter is not an justification.
		 */
		
		final Set<Set<OWLAxiom>> justifications = new HashSet<Set<OWLAxiom>>();
		
		for (final Set<OWLAxiom> candidate1 : candidates) {
			boolean thereIsAProperSubset = false;
			for (final Set<OWLAxiom> candidate2 : candidates) {
				if (!candidate1.equals(candidate2)
						&& candidate1.containsAll(candidate2)) {
					thereIsAProperSubset = true;
					break;
				}
			}
			if (!thereIsAProperSubset) {
				justifications.add(candidate1);
			}
		}
		
		return justifications;
	}
	
	private Set<Set<OWLAxiom>> computeJustificationCandidates(
			final OWLExpression expression)
					throws ProofGenerationException, InterruptedException {
		
		checkCancelled();
		
		/* 
		 * Union of justifications obtained for each inference.
		 * Product of justifications obtained for each premise.
		 * Or if this expression is asserted, it is an justification.
		 */
		final Set<Set<OWLAxiom>> candidates = new HashSet<Set<OWLAxiom>>();
		
		for (final OWLInference inference : expression.getInferences()) {
			/* 
			 * Set of justifications for an inference is a product of sets of
			 * justifications for its premises.
			 */
			final Collection<? extends OWLExpression> premises =
					inference.getPremises();
			final List<Set<Set<OWLAxiom>>> premiseCandidates =
					new ArrayList<Set<Set<OWLAxiom>>>(premises.size());
			
			for (final OWLExpression premise : premises) {
				premiseCandidates.add(computeJustificationCandidates(premise));
			}
			
			candidates.addAll(product(premiseCandidates));
		}
		
		// A set containing only this expression is a justification.
		if (expression instanceof OWLAxiomExpression) {
			final OWLAxiomExpression axExp = (OWLAxiomExpression) expression;
			if (axExp.isAsserted()) {
				candidates.add(Collections.singleton(axExp.getAxiom()));
			}
		}
		
		return candidates;
	}
	
	/**
	 * <strong>Caution!</strong> The result should be treated as immutable!
	 * 
	 * @param arg
	 * @return
	 */
	private static Set<Set<OWLAxiom>> product(List<Set<Set<OWLAxiom>>> arg) {
		if (arg == null || arg.isEmpty()) {
			return Collections.singleton(Collections.<OWLAxiom>emptySet());
		} else if (arg.size() == 1) {
			return arg.get(0);
		} else {
			final Set<Set<OWLAxiom>> result = new HashSet<Set<OWLAxiom>>();
			for (final Set<OWLAxiom> set2
					: product(arg.subList(1, arg.size()))) {
				for (final Set<OWLAxiom> set1 : arg.get(0)) {
					final HashSet<OWLAxiom> set = new HashSet<OWLAxiom>(set1);
					set.addAll(set2);
					result.add(set);
				}
			}
			return result;
		}
	}

}
