package org.semanticweb.elk.justifications;

import java.io.BufferedWriter;
import java.io.IOException;

import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.ProofPrinter;

/**
 * A simple pretty printer of proofs together with justification numbers for
 * conclusions.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of the conclusions in proofs
 * @param <A>
 *            the type of the axioms in proofs
 */
public class ProofJustificationPrinter<C, A> extends ProofPrinter<C, A> {

	private final JustificationComputation<C, A> computation_;
	
	private final int sizeLimit_;

	ProofJustificationPrinter(JustificationComputation<C, A> computation,
			InferenceSet<C, A> inferences, int sizeLimit) {
		super(inferences);
		this.computation_ = computation;
		this.sizeLimit_ = sizeLimit;
	}

	public static <C, A> void print(JustificationComputation<C, A> computation,
			InferenceSet<C, A> inferences, C conclusion, int sizeLimit)
			throws IOException {
		ProofPrinter<C, A> pp = new ProofJustificationPrinter<>(computation,
				inferences, sizeLimit);
		pp.printProof(conclusion);
	}
	
	public static <C, A> void print(JustificationComputation<C, A> computation,
			InferenceSet<C, A> inferences, C conclusion)
			throws IOException {
		print(computation, inferences, conclusion, Integer.MAX_VALUE);		
	}

	@Override
	protected void writeConclusion(C conclusion) throws IOException {
		BufferedWriter w = getWriter();
		w.write('[');
		w.write(Integer.toString(computation_
				.computeJustifications(conclusion, sizeLimit_).size()));
		w.write(']');
		w.write(' ');
		super.writeConclusion(conclusion);
	}

}
