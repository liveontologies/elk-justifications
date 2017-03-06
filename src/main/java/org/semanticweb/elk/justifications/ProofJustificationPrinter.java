package org.semanticweb.elk.justifications;

import java.io.BufferedWriter;
import java.io.IOException;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
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

	private final JustificationCollector<C, A> collector_;

	private final int sizeLimit_;

	ProofJustificationPrinter(JustificationComputation.Factory<C, A> factory,
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			int sizeLimit) {
		super(inferences);
		this.collector_ = new JustificationCollector<>(factory, inferences);
		this.sizeLimit_ = sizeLimit;
	}

	public static <C, A> void print(JustificationComputation.Factory<C, A> factory,
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			C conclusion, int sizeLimit) throws IOException {
		ProofPrinter<C, A> pp = new ProofJustificationPrinter<>(factory,
				inferences, sizeLimit);
		pp.printProof(conclusion);
	}

	public static <C, A> void print(JustificationComputation.Factory<C, A> factory,
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			C conclusion) throws IOException {
		print(factory, inferences, conclusion, Integer.MAX_VALUE);
	}

	@Override
	protected void writeConclusion(C conclusion) throws IOException {
		BufferedWriter w = getWriter();
		w.write('[');
		w.write(Integer.toString(collector_
				.collectJustifications(conclusion, sizeLimit_).size()));
		w.write(']');
		w.write(' ');
		super.writeConclusion(conclusion);
	}

}
