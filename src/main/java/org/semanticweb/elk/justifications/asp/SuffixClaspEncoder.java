package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;
import java.util.Map.Entry;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

public class SuffixClaspEncoder<C, A> extends ClaspEncoder<C, A> {
	
	protected final String selectedLiteralPrefix;
	
	public SuffixClaspEncoder(
			final String selectedLiteralPrefix,
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
		this.selectedLiteralPrefix = selectedLiteralPrefix;
	}
	
	@Override
	public void encodeCommon(final C goalConclusion) {
		// Empty
	}
	
	@Override
	public void encodeInference(final Inference<C, A> inf) {
		// Empty
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
		
		// delimiter
		output.println(0);
		
		// Write literal index
		for (final Entry<String, Integer> e : literalIndex.getIndex().entrySet()) {
			if (e.getKey().startsWith(selectedLiteralPrefix)) {
				output.print(e.getValue());
				output.print(' ');
				output.print(e.getKey());
				output.println();
			}
		}
		
		// delimiter
		output.println(0);
		
		// Write the magic at the bottom :-P
		output.println("B+");
		output.println(0);
		output.println("B-");
		output.println(1);
		output.println(0);
		output.println(1);
		
	}
	
}
