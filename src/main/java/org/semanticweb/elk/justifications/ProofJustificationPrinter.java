package org.semanticweb.elk.justifications;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.ProofPrinter;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;

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

	private final MinimalSubsetCollector<C, A> collector_;

	private final int sizeLimit_;

	ProofJustificationPrinter(
			final MinimalSubsetsFromProofs.Factory<C, A> factory,
			final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			int sizeLimit) {
		super(proof, justifier);
		this.collector_ = new MinimalSubsetCollector<>(factory, proof,
				justifier);
		this.sizeLimit_ = sizeLimit;
	}

	public static <C, A> void print(
			final MinimalSubsetsFromProofs.Factory<C, A> factory,
			final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion, int sizeLimit) throws IOException {
		ProofPrinter<C, A> pp = new ProofJustificationPrinter<>(factory, proof,
				justifier, sizeLimit);
		pp.printProof(conclusion);
	}

	public static <C, A> void print(
			final MinimalSubsetsFromProofs.Factory<C, A> factory,
			final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion) throws IOException {
		print(factory, proof, justifier, conclusion, Integer.MAX_VALUE);
	}

	@Override
	protected void writeConclusion(C conclusion) throws IOException {
		BufferedWriter w = getWriter();
		w.write('[');
		w.write(Integer
				.toString(collector_.collect(conclusion, sizeLimit_).size()));
		w.write(']');
		w.write(' ');
		super.writeConclusion(conclusion);
	}

}
