package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

/**
 * <pre>
 * % Predicates
 * 
 * axiom(A,L)
 * Axiom A is used in the proof of justification
 * and its label is L.
 * 
 * conclDerived(C)
 * Conclusion C is derived from the justification.
 * 
 * % Rules
 * 
 * :- not conclDerived(goalConclusion).
 * where goalConclusion is the goal conclusion.
 * 
 * conclDerived(C) :- conclDerived(P1), ..., conclDerived(Pm),
 * 		axiom(A1), ..., axiom(An).
 * where C is the conclusion of an inference with premises P1, ..., Pm
 * and justification A1, ..., An.
 * 
 * </pre>
 * 
 * @author aifargonos
 *
 * @param <C>
 * @param <A>
 */
public class DerivabilityGringoEncoder<C, A> extends AspEncoder<C, A> {
	
	public DerivabilityGringoEncoder(
			final PrintWriter output,
			final InferenceSet<C, A> inferenceSet,
			final Index<C> conclIndex,
			final Index<A> axiomIndex,
			final Index<Inference<C, A>> infIndex) {
		super(
				output,
				inferenceSet,
				conclIndex,
				axiomIndex,
				infIndex
			);
	}
	
	@Override
	public void encodeCommon(final C goalConclusion) {
		
		final String cdLit = AspUtils.getConclDerivedLiteral(goalConclusion, conclIndex);
		
		// write ":- not cdl."
		AspUtils.writeGringoConstraint(output,
				Collections.<String>emptyIterator(), cdLit);
		
	}
	
	@Override
	public void encodeInference(final Inference<C, A> inference) {
		
		final C conclusion = inference.getConclusion();
		
		final String cdLit = AspUtils.getConclDerivedLiteral(conclusion, conclIndex);
		
		final Collection<? extends C> premises = inference.getPremises();
		final Set<? extends A> axioms = inference.getJustification();
		
		// write "cdl :- pdl1, ..., pdlm, a1, ..., an."
		AspUtils.writeGringoRule(output, cdLit,
				Iterables.concat(
						Collections2.transform(premises, new Function<C, String>() {
							@Override
							public String apply(final C premise) {
								
								return AspUtils.getConclDerivedLiteral(premise, conclIndex);
								
							}
						}),
						Collections2.transform(axioms, new Function<A, String>() {
							@Override
							public String apply(final A axiom) {
								
								return AspUtils.getAxiomLiteral(axiom, axiomIndex);
								
							}
						})
					)
			);
		
	}
	
	@Override
	public void encodeConclusion(final C conclusion) {
		// Empty
	}
	
	@Override
	public void encodeAxiom(final A axiom) {
		// Empty
	}
	
	@Override
	public void encodeSuffix() {
		// Empty
	}
	
}
