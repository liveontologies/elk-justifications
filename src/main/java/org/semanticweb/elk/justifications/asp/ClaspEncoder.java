package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

public abstract class ClaspEncoder<C, A> extends AspEncoder<C, A> {
	
	protected final Index<String> literalIndex;
	
	public ClaspEncoder(
			final PrintWriter output,
			final InferenceSet<C, A> inferenceSet,
			final Index<C> conclIndex,
			final Index<A> axiomIndex,
			final Index<Inference<C, A>> infIndex,
			final Index<String> literalIndex) {
		super(output, inferenceSet, conclIndex, axiomIndex, infIndex);
		this.literalIndex = literalIndex;
	}
	
}
