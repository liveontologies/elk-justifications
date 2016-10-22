package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

public class DotEncoder<C, A> extends AspEncoder<C, A> {
	
	public DotEncoder(
			final PrintWriter output,
			final InferenceSet<C, A> inferenceSet,
			final Index<C> conclIndex,
			final Index<A> axiomIndex,
			final Index<Inference<C, A>> infIndex) {
		super(output, inferenceSet, conclIndex, axiomIndex, infIndex);
	}

	@Override
	public void encodeCommon(final C goalConclusion) {
		// Empty.
	}
	
	@Override
	public void encodeInference(final Inference<C, A> inference) {
		
		final int i = infIndex.get(inference);
		
		for (final C premise : inference.getPremises()) {
			
			final int p = conclIndex.get(premise);
			
			// write: "i$(i) -> c$(p);"
			output.print('i');
			output.print(i);
			output.print(" -> ");
			output.print('c');
			output.print(p);
			output.println(";");
			
		}
		
		for (final A axiom : inference.getJustification()) {
			
			final int a = axiomIndex.get(axiom);
			
			// write: "i$(i) -> a$(a);"
			output.print('i');
			output.print(i);
			output.print(" -> ");
			output.print('a');
			output.print(a);
			output.println(";");
			
		}
		
		// write: "i$(i) [shape=square];"
		output.print('i');
		output.print(i);
		output.println(" [shape=square];");
		
	}
	
	@Override
	public void encodeConclusion(final C conclusion) {
		
		final int c = conclIndex.get(conclusion);
		
		for (final Inference<C, A> inf
				: inferenceSet.getInferences(conclusion)) {
			
			final int i = infIndex.get(inf);
			
			/* 
			 * write: "c$(c) -> i$(i);"
			 */
			output.print('c');
			output.print(c);
			output.print(" -> ");
			output.print('i');
			output.print(i);
			output.println(";");
			
		}
		
	}
	
	@Override
	public void encodeAxiom(final A axiom) {
		// Empty.
	}
	
	@Override
	public void encodeSuffix() {
		// Empty
	}
	
}
