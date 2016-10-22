package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;

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
 * concl(goalConclusion,_).
 * where goalConclusion is the goal conclusion.
 * 
 * concl(P,_) :- inf(I,_).
 * where P is a premise of I.
 * 
 * axiom(A,_) :- inf(I,_).
 * where A is in justification of I.
 * 
 * inf(I1,_) | ... | inf(In,_) :- concl(C,_).
 * where I1, ..., In are all inferences that derive C.
 * 
 * </pre>
 * 
 * @author aifargonos
 *
 * @param <C>
 * @param <A>
 */
public class BackwardGringoEncoder<C, A> extends AspEncoder<C, A> {
	
	public BackwardGringoEncoder(
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
		
		final String cLit = AspUtils.getConclLiteral(goalConclusion, conclIndex);
		
		// write "cl."
		AspUtils.writeGringoFact(output, cLit);
		
	}
	
	@Override
	public void encodeInference(final Inference<C, A> inf) {
		
		final String iLit = AspUtils.getInfLiteral(inf, infIndex);
		
		for (final C premise : inf.getPremises()) {
			
			final String pLit = AspUtils.getConclLiteral(premise, conclIndex);
			
			// write "pLit :- iLit."
			AspUtils.writeGringoRule(output, pLit, iLit);
			
		}
		
		for (final A axiom : inf.getJustification()) {
			
			final String aLit = AspUtils.getAxiomLiteral(axiom, axiomIndex);
			
			// write "aLit :- iLit."
			AspUtils.writeGringoRule(output, aLit, iLit);
			
		}
		
	}
	
	@Override
	public void encodeConclusion(final C conclusion) {
		
		final String cLit = AspUtils.getConclLiteral(conclusion, conclIndex);
		
		final Iterable<Inference<C, A>> infs =
				inferenceSet.getInferences(conclusion);
		
		// write "il1 | il2 | ... | iln :- cl."
		AspUtils.writeGringoRule(output,
				Iterators.transform(infs.iterator(), new Function<Inference<C, A>, String>() {
					@Override
					public String apply(final Inference<C, A> inf) {
						return AspUtils.getInfLiteral(inf, infIndex);
					}
				}),
				cLit);
		
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
