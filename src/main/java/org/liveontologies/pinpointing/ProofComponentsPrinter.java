package org.liveontologies.pinpointing;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.ProofPrinter;

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

	ProofComponentsPrinter(final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion) {
		super(proof, justifier);
		this.components_ = StronglyConnectedComponentsComputation
				.computeComponents(proof, conclusion);
	}

	public static <C, A> void print(final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion) throws IOException {
		ProofPrinter<C, A> pp = new ProofComponentsPrinter<>(proof, justifier,
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
