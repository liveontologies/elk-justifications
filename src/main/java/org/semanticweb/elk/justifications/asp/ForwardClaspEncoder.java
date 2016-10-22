package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;
import java.util.Collection;
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
 * inf(I,L)
 * Inference I is used in the proof of justification
 * and its label is L.
 * 
 * concl(C,L)
 * Conclusion C is used in the proof of justification
 * and its label is L.
 * 
 * axiom(A,L)
 * Axiom A is used in the proof of justification
 * and its label is L.
 * 
 * % Rules
 * 
 * concl(C,_) :- inf(I,_).
 * where C is the conclusion of I.
 * 
 * inf(I,_) :- concl(P1,_), ..., concl(Pm,_), axiom(A1,_), ..., axiom(An,_).
 * where P1, ..., Pm are all premises of I
 * and A1, ..., An is justification of I.
 * 
 * </pre>
 * 
 * @author aifargonos
 *
 * @param <C>
 * @param <A>
 */
public class ForwardClaspEncoder<C, A> extends ClaspEncoder<C, A> {
	
	public ForwardClaspEncoder(
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
		// Empty.
	}
	
	@Override
	public void encodeInference(final Inference<C, A> inference) {
		
		final C conclusion = inference.getConclusion();
		
		final String iLit = AspUtils.getInfLiteral(inference, infIndex);
		final int il = literalIndex.get(iLit);
		
		final String cLit = AspUtils.getConclLiteral(conclusion, conclIndex);
		final int cl = literalIndex.get(cLit);
		
		// write "cl :- il."
		AspUtils.writeNormalClaspRule(output, cl, il);
		
		final Collection<? extends C> premises = inference.getPremises();
		final Set<? extends A> axioms = inference.getJustification();
		
		// write "il :- pl1, ..., plm, a1, ..., an."
		AspUtils.writeNormalClaspRule(output, il,
				Operations.getCollection(
						Iterables.concat(
								Collections2.transform(premises, new Function<C, Integer>() {
									@Override
									public Integer apply(final C premise) {
										
										final String pLit = AspUtils.getConclLiteral(premise, conclIndex);
										return literalIndex.get(pLit);
										
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
