package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

public abstract class AspEncoder<C, A> implements Encoder<C, A> {
	
	protected final PrintWriter output;
	
	protected final InferenceSet<C, A> inferenceSet;
	
	protected final Index<C> conclIndex;
	protected final Index<A> axiomIndex;
	protected final Index<Inference<C, A>> infIndex;
	
	public AspEncoder(
			final PrintWriter output,
			final InferenceSet<C, A> inferenceSet,
			final Index<C> conclIndex,
			final Index<A> axiomIndex,
			final Index<Inference<C, A>> infIndex) {
		this.output = output;
		this.inferenceSet = inferenceSet;
		this.conclIndex = conclIndex;
		this.axiomIndex = axiomIndex;
		this.infIndex = infIndex;
	}
	
}
