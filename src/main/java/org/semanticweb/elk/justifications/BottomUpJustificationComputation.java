package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

public class BottomUpJustificationComputation
		extends CancellableJustificationComputation {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(BottomUpJustificationComputation.class);

	/**
	 * conclusions for which to compute justifications
	 */
	private final Queue<OWLExpression> toDo_ = new LinkedList<OWLExpression>();

	/**
	 * caches conclusions for which justification computation has started
	 */
	private final Set<OWLExpression> done_ = new HashSet<OWLExpression>();

	/**
	 * a map from conclusions to inferences in which they participate
	 */
	private final Multimap<OWLExpression, OWLInference> inferencesByPremises_ = HashMultimap
			.create();
	/**
	 * newly computed justifications to be propagated
	 */
	private final Queue<Job> toDoJustifications_ = new LinkedList<Job>();

	/**
	 * a map from conclusions to their justifications
	 */
	private final SetMultimap<OWLExpression, Set<OWLAxiom>> justsByConcls_ = HashMultimap
			.create();

	public BottomUpJustificationComputation(
			final ExplainingOWLReasoner reasoner) {
		super(reasoner);
	}

	@Override
	public Set<Set<OWLAxiom>> computeJustifications(
			final OWLSubClassOfAxiom conclusion)
			throws ProofGenerationException, InterruptedException {

		final OWLAxiomExpression expression = reasoner
				.getDerivedExpression(conclusion);

		process(expression);

		return justsByConcls_.get(expression);
	}

	private void process(OWLExpression exp) throws ProofGenerationException {

		toDo(exp);

		while ((exp = toDo_.poll()) != null) {
			LOGGER_.trace("{}: new lemma", exp);

			if (exp instanceof OWLAxiomExpression) {
				final OWLAxiomExpression axExp = (OWLAxiomExpression) exp;
				if (axExp.isAsserted()) {
					final Set<OWLAxiom> just = new HashSet<OWLAxiom>();
					just.add(axExp.getAxiom());
					process(new Job(axExp, just));
				}
			}

			for (OWLInference inf : exp.getInferences()) {
				process(inf);
			}

		}

	}

	private void toDo(OWLExpression exp) {
		if (done_.add(exp)) {
			toDo_.add(exp);
		}
	}

	private void process(OWLInference inf) throws ProofGenerationException {
		LOGGER_.trace("{}: new inference", inf);
		// new inference, propagate existing the justification for premises
		List<Set<OWLAxiom>> conclusionJusts = new ArrayList<Set<OWLAxiom>>();
		conclusionJusts.add(new HashSet<OWLAxiom>());
		for (OWLExpression premise : inf.getPremises()) {
			inferencesByPremises_.put(premise, inf);
			toDo(premise);
			conclusionJusts = join(conclusionJusts,
					justsByConcls_.get(premise));
		}
		OWLExpression conclusion = inf.getConclusion();
		for (Set<OWLAxiom> just : conclusionJusts) {
			process(new Job(conclusion, just));
		}
	}

	/**
	 * propagates the newly computed justification until the fixpoint
	 * 
	 * @throws ProofGenerationException
	 */
	private void process(Job job) throws ProofGenerationException {
		toDoJustifications_.add(job);
		while ((job = toDoJustifications_.poll()) != null) {
			LOGGER_.trace("{}: new justification: {}", job.expr, job.just);

			if (job.just.isEmpty()) {
				// all justifications are computed,
				// the inferences are not needed anymore
				for (OWLInference inf : job.expr.getInferences()) {
					for (OWLExpression premise : inf.getPremises()) {
						inferencesByPremises_.remove(premise, inf);
					}
				}
			}

			if (merge(job.just, justsByConcls_.get(job.expr))) {

				/*
				 * propagating justification over inferences
				 */
				for (OWLInference inf : inferencesByPremises_.get(job.expr)) {

					Collection<Set<OWLAxiom>> conclusionJusts = new ArrayList<Set<OWLAxiom>>();
					conclusionJusts.add(new HashSet<OWLAxiom>(job.just));
					for (final OWLExpression premise : inf.getPremises()) {
						if (!premise.equals(job.expr)) {
							conclusionJusts = join(conclusionJusts,
									justsByConcls_.get(premise));
						}
					}

					for (Set<OWLAxiom> conclJust : conclusionJusts) {
						toDoJustifications_
								.add(new Job(inf.getConclusion(), conclJust));
					}

				}

			}

		}

	}

	/**
	 * Merges a given justification into a given collection of justifications.
	 * The justification is added to the collection unless its subset is already
	 * contained in the collection. Furthermore, all proper subsets of the
	 * justification are removed from the collection.
	 * 
	 * @param just
	 * @param justs
	 * @return {@code true} if the collection is modified as a result of this
	 *         operation and {@code false} otherwise
	 */
	private boolean merge(Set<OWLAxiom> just, Collection<Set<OWLAxiom>> justs) {
		int justSize = just.size();
		final Iterator<Set<OWLAxiom>> oldJustIter = justs.iterator();
		boolean justNew = false; // true if the justification is new
		while (oldJustIter.hasNext()) {
			final Set<OWLAxiom> oldJust = oldJustIter.next();
			if (justSize < oldJust.size()) {
				if (oldJust.containsAll(just)) {
					// new justification is smaller
					oldJustIter.remove();
					justNew = true;
				}
			} else if (!justNew & just.containsAll(oldJust)) {
				// a subset is already a justification
				return false;
			}
		}
		// justification survived all tests, it is new
		justs.add(just);
		return true;
	}

	/**
	 * @param first
	 * @param second
	 * @return the list of all pairwise unions of the sets in the first and the
	 *         second collections
	 */
	private static <T> List<Set<T>> join(Collection<? extends Set<T>> first,
			Collection<? extends Set<T>> second) {
		if (first.isEmpty() || second.isEmpty()) {
			return Collections.emptyList();
		}
		List<Set<T>> result = new ArrayList<Set<T>>(
				first.size() * second.size());
		for (Set<T> firstSet : first) {
			for (Set<T> secondSet : second) {
				Set<T> union = new HashSet<T>();
				union.addAll(firstSet);
				union.addAll(secondSet);
				result.add(union);
			}
		}
		return result;
	}

	private static class Job {
		final OWLExpression expr;
		final Set<OWLAxiom> just;

		public Job(final OWLExpression expr, final Set<OWLAxiom> just) {
			this.expr = expr;
			this.just = just;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "(" + expr + ", " + just + ")";
		}

	}

}
