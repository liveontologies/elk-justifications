package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.util.collections.Operations;

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
public class DerivabilityClaspEncoder<C, A> extends ClaspEncoder<C, A> {
	
	public DerivabilityClaspEncoder(
			final PrintWriter output,
			final InferenceSet<C, A> inferenceSet,
			final Index<C> conclIndex,
			final Index<A> axiomIndex,
			final Index<Inference<C, A>> infIndex,
			final Index<String> literalIndex) {
		super(
				output,
				inferenceSet,
				conclIndex,
				axiomIndex,
				infIndex,
				literalIndex
			);
	}
	
	@Override
	public void encodeCommon(final C goalConclusion) {
		
		final String cdLit = AspUtils.getConclDerivedLiteral(goalConclusion, conclIndex);
		final int cdl = literalIndex.get(cdLit);
		
		// write ":- not cdl."
		AspUtils.writeClaspConstraint(output, Collections.<Integer>emptyList(),
				cdl);
		
	}
	
	@Override
	public void encodeInference(final Inference<C, A> inference) {
		
		final C conclusion = inference.getConclusion();
		
		final String cdLit = AspUtils.getConclDerivedLiteral(conclusion, conclIndex);
		final int cdl = literalIndex.get(cdLit);
		
		final Collection<? extends C> premises = inference.getPremises();
		final Set<? extends A> axioms = inference.getJustification();
		
		// write "cdl :- pdl1, ..., pdlm, a1, ..., an."
		AspUtils.writeNormalClaspRule(output, cdl,
				Operations.getCollection(
						Iterables.concat(
								Collections2.transform(premises, new Function<C, Integer>() {
									@Override
									public Integer apply(final C premise) {
										
										final String pdLit = AspUtils.getConclDerivedLiteral(premise, conclIndex);
										return literalIndex.get(pdLit);
										
									}
								}),
								Collections2.transform(axioms, new Function<A, Integer>() {
									@Override
									public Integer apply(final A axiom) {
										
										final String aLit = AspUtils.getAxiomLiteral(axiom, axiomIndex);
										return literalIndex.get(aLit);
										
									}
								})
							),
						premises.size() + axioms.size()
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
