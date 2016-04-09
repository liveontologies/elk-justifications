package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.semanticweb.owlapitools.proofs.OWLInference;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLAxiomExpression;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class BottomUpJustificationComputation
		extends CancellableJustificationComputation {

	private Multimap<OWLExpression, OWLInference> inferencesForPremises;
	private Queue<Job> newJustifications;
	
	public BottomUpJustificationComputation(
			final ExplainingOWLReasoner reasoner) {
		super(reasoner);
	}

	@Override
	public Set<Set<OWLAxiom>> computeJustifications(
			final OWLSubClassOfAxiom conclusion)
					throws ProofGenerationException, InterruptedException {
		
		final OWLAxiomExpression expression =
				reasoner.getDerivedExpression(conclusion);
		
		/* 
		 * Collect mapping from premises to inferences in which they are used
		 * and axioms that are asserted.
		 */
		/* 
		 * Start with the asserted expressions.
		 */
		initialize(expression);
		
		final HashMultimap<OWLExpression, Set<OWLAxiom>> justsForConcls = HashMultimap.create();
		
		for (;;) {
			final Job next = newJustifications.poll();
			if (next == null) {
				break;
			}
			
			final Set<Set<OWLAxiom>> oldJusts = justsForConcls.get(next.expr);
			
			for (final Set<OWLAxiom> newJust : next.justs) {
				
				if (checkMinimalityAndPut(newJust, oldJusts, justsForConcls, next.expr)) {
//				// If the NOT MINIMIZED justification is new, add it and do the job.
//				if (justsForConcls.put(next.expr, newJust)) {
					
					/* 
					 * Update justifications of conclusion of each inference that
					 * uses this expression as a premise.
					 * 
					 * The new justifications are product of justifications of all
					 * other premises and this new justification.
					 */
					for (final OWLInference inf : inferencesForPremises.get(next.expr)) {
						
						final Collection<? extends OWLExpression> premises =
								inf.getPremises();
						final List<Set<Set<OWLAxiom>>> premiseJusts =
								new ArrayList<Set<Set<OWLAxiom>>>(premises.size());
						for (final OWLExpression premise : premises) {
							if (!premise.equals(next.expr)) {
								premiseJusts.add(justsForConcls.get(premise));
							}
						}
						
						final Set<Set<OWLAxiom>> conclJusts = product(premiseJusts);
						for (final Set<OWLAxiom> j : conclJusts) {
							j.addAll(newJust);
						}
						
						if (!conclJusts.isEmpty()) {
							newJustifications.add(new Job(inf.getConclusion(), conclJusts));
						}
					}
					
				}
				
			}
			
		}
		
		return justsForConcls.get(expression);
	}
	
	private static boolean checkMinimalityAndPut(final Set<OWLAxiom> newJust,
			final Set<Set<OWLAxiom>> oldJusts,
			final HashMultimap<OWLExpression, Set<OWLAxiom>> justsForConcls,
			final OWLExpression key) {
		
		/* 
		 * If this justification is a superset of some we already have,
		 * do nothing.
		 */
		boolean isASuperset = false;
		for (final Set<OWLAxiom> oldJust : oldJusts) {
			if (newJust.containsAll(oldJust)) {
				isASuperset = true;
				break;
			}
		}
		if (isASuperset) {
			return false;
		}
		
		/* 
		 * If this justification is a subset of some we already have,
		 * remove that one and continue.
		 * (It's subjustifications will be recomputed.)
		 */
		final Iterator<Set<OWLAxiom>> it = oldJusts.iterator();
		while (it.hasNext()) {
			final Set<OWLAxiom> oldJust = it.next();
			if (oldJust.containsAll(newJust)) {
				it.remove();
			}
		}
		
		// If this justification is really new, add it and do the job.
		justsForConcls.put(key, newJust);
		
		return true;
	}

	private void initialize(final OWLAxiomExpression expression)
			throws ProofGenerationException {
		
		inferencesForPremises = HashMultimap.create();
		newJustifications = new LinkedList<Job>();
		
		final Queue<OWLExpression> toDo = new LinkedList<OWLExpression>();
		final Set<OWLExpression> done = new HashSet<OWLExpression>();
		
		toDo.add(expression);
		
		for (;;) {
			final OWLExpression next = toDo.poll();
			
			if (next == null) {
				break;
			}
			
			if (done.add(next)) {
				
				if (next instanceof OWLAxiomExpression) {
					final OWLAxiomExpression axExp = (OWLAxiomExpression) next;
					if (axExp.isAsserted()) {
						final Set<OWLAxiom> just = new HashSet<OWLAxiom>();
						just.add(axExp.getAxiom());
						final Set<Set<OWLAxiom>> justs = new HashSet<Set<OWLAxiom>>();
						justs.add(just);
						newJustifications.add(new Job(axExp, justs));
					}
				}
				
				for (final OWLInference inf : next.getInferences()) {
					final Collection<? extends OWLExpression> premises =
							inf.getPremises();
					if (premises.isEmpty()) {// TODO: The other inferences are useless, because the empty just will be subset of all others!!!
						// This is a tautology, so it is justified by an empty set!
						final Set<OWLAxiom> just = new HashSet<OWLAxiom>();
						final Set<Set<OWLAxiom>> justs = new HashSet<Set<OWLAxiom>>();
						justs.add(just);
						newJustifications.add(new Job(next, justs));
					}
					for (final OWLExpression premise : premises) {
						inferencesForPremises.put(premise, inf);
						toDo.add(premise);
					}
				}
				
			}
		}
		
	}
	
	private static class Job {
		public final OWLExpression expr;
		public final Set<Set<OWLAxiom>> justs;
		public Job(final OWLExpression expr, final Set<Set<OWLAxiom>> justs) {
			this.expr = expr;
			this.justs = justs;
		}
		@Override
		public String toString() {
			return getClass().getSimpleName() + "(" + expr + ", " + justs + ")";
		}
	}
	
	private static Set<Set<OWLAxiom>> product(List<Set<Set<OWLAxiom>>> arg) {
		if (arg == null || arg.isEmpty()) {
			final Set<Set<OWLAxiom>> result = new HashSet<Set<OWLAxiom>>();
			result.add(new HashSet<OWLAxiom>());
			return result;
		} else if (arg.size() == 1) {
			// Return a deep copy !!
			final Set<Set<OWLAxiom>> result = new HashSet<Set<OWLAxiom>>();
			for (final Set<OWLAxiom> set : arg.get(0)) {
				result.add(new HashSet<OWLAxiom>(set));
			}
			return result;
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
