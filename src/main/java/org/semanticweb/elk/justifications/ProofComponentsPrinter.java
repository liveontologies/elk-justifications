package org.semanticweb.elk.justifications;

import java.io.BufferedWriter;
import java.io.IOException;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.proofs.ProofPrinter;

/**
 * A simple pretty printer of proofs together with components numbers for
 * conclusions.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of the conclusions in proofs
 * @param <A>
 *            the type of the axioms in proofs
 */
public class ProofComponentsPrinter<C, A> extends ProofPrinter<C, A> {

	private final StronglyConnectedComponents<C> components_;

	ProofComponentsPrinter(
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			C conclusion) {
		super(inferences);
		this.components_ = StronglyConnectedComponentsComputation
				.computeComponents(inferences, conclusion);
	}

	public static <C, A> void print(
			GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			C conclusion) throws IOException {
		ProofPrinter<C, A> pp = new ProofComponentsPrinter<>(inferences,
				conclusion);
		pp.printProof(conclusion);
	}

	@Override
	protected void writeConclusion(C conclusion) throws IOException {
		BufferedWriter w = getWriter();
		w.write('[');
		w.write(Integer.toString(components_.getComponentId(conclusion)));
		w.write(']');
		w.write(' ');
		super.writeConclusion(conclusion);
	}

}
