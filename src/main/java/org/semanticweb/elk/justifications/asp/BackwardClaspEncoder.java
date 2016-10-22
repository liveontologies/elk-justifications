package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

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
public class BackwardClaspEncoder<C, A> extends ClaspEncoder<C, A> {
	
	public BackwardClaspEncoder(
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
		
		final String cLit = AspUtils.getConclLiteral(goalConclusion, conclIndex);
		final int cl = literalIndex.get(cLit);
		
		// write "cl."
		AspUtils.writeNormalClaspRule(output, cl);
		
	}
	
	@Override
	public void encodeInference(final Inference<C, A> inf) {
		
		final String iLit = AspUtils.getInfLiteral(inf, infIndex);
		final int il = literalIndex.get(iLit);
		
		for (final C premise : inf.getPremises()) {
			
			final String pLit = AspUtils.getConclLiteral(premise, conclIndex);
			final int pl = literalIndex.get(pLit);
			
			// write "pl :- il."
			AspUtils.writeNormalClaspRule(output, pl, il);
			
		}
		
		for (final A axiom : inf.getJustification()) {
			
			final String aLit = AspUtils.getAxiomLiteral(axiom, axiomIndex);
			final int al = literalIndex.get(aLit);
			
			// write "al :- il."
			AspUtils.writeNormalClaspRule(output, al, il);
			
		}
		
	}
	
	@Override
	public void encodeConclusion(final C conclusion) {
		
		final String cLit = AspUtils.getConclLiteral(conclusion, conclIndex);
		final int cl = literalIndex.get(cLit);
		
		final Iterable<Inference<C, A>> infs =
				inferenceSet.getInferences(conclusion);
		final Collection<Integer> head = new ArrayList<>();
		for (final Inference<C, A> inf : infs) {
			
			final String iLit = AspUtils.getInfLiteral(inf, infIndex);
			head.add(literalIndex.get(iLit));
			
		}
		
		// write "il1 | il2 | ... | iln :- cl."
		AspUtils.writeDisjunctiveClaspRule(output, head, cl);
		
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
