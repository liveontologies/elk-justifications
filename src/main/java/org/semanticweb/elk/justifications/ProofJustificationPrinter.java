package org.semanticweb.elk.justifications;

import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.ProofPrinter;

/**
 * A simple pretty printer of proofs together with justification numbers for
 * conclusions
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

	ProofJustificationPrinter(
			JustificationComputation.Factory<C, A> justFactory,
			InferenceSet<C, A> inferences) {
		super(inferences);
		this.computation_ = justFactory.create(inferences, new DummyMonitor());
	}

	public static <C, A> void print(
			JustificationComputation.Factory<C, A> justFactory,
			InferenceSet<C, A> inferences, C conclusion) {
		ProofPrinter<C, A> pp = new ProofJustificationPrinter<>(justFactory,
				inferences);
		pp.printProof(conclusion);
	}

	@Override
	protected void appendConclusion(StringBuilder sb, C conclusion) {
		sb.append('[');
		sb.append(computation_.computeJustifications(conclusion).size());
		sb.append(']');
		sb.append(' ');
		sb.append(conclusion);
	}

}
