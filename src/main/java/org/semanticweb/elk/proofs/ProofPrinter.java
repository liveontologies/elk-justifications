package org.semanticweb.elk.proofs;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;

/**
 * A simple pretty printer for proofs using ASCII characters. Due to potential
 * cycles, inferences for every conclusion are printed only once upon their
 * first occurrence in the proof. Every following occurrence of the same
 * conclusion is labeled by {@code *}.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of the conclusions in proofs
 * @param <A>
 *            the type of the axioms in proofs
 */
public class ProofPrinter<C, A> {

	/**
	 * the set of inferences from which the proofs are formed
	 */
	private final InferenceSet<C> inferences_;

	/**
	 * provides justifications for inferences
	 */
	private final InferenceJustifier<C, ? extends Set<? extends A>> justifier_;

	/**
	 * the current positions of iterators over inferences for conclusions
	 */
	private final Deque<Iterator<? extends Inference<C>>> inferenceStack_ = new LinkedList<Iterator<? extends Inference<C>>>();

	/**
	 * the current positions of iterators over conclusions for inferences
	 */
	private final Deque<Iterator<? extends C>> conclusionStack_ = new LinkedList<Iterator<? extends C>>();

	/**
	 * the current positions of iterators over justifications for inferences
	 */
	private final Deque<Iterator<? extends A>> justificationStack_ = new LinkedList<Iterator<? extends A>>();

	/**
	 * accumulates the printed conclusions to avoid repetitions
	 */
	private final Set<C> printed_ = new HashSet<C>();

	/**
	 * where the output is written
	 */
	private final BufferedWriter writer_;

	protected ProofPrinter(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			BufferedWriter writer) {
		this.inferences_ = inferenceSet;
		this.justifier_ = justifier;
		this.writer_ = writer;
	}

	protected ProofPrinter(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		this(inferenceSet, justifier,
				new BufferedWriter(new OutputStreamWriter(System.out)));
	}

	public void printProof(C conclusion) throws IOException {
		process(conclusion);
		process();
		writer_.flush();
	}

	public static <C, A> void print(final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			C conclusion) throws IOException {
		ProofPrinter<C, A> pp = new ProofPrinter<>(inferenceSet, justifier);
		pp.printProof(conclusion);
	}

	protected BufferedWriter getWriter() {
		return writer_;
	}

	protected void writeConclusion(C conclusion) throws IOException {
		// can be overridden
		writer_.write(conclusion.toString());
	}

	private boolean process(C conclusion) throws IOException {
		writePrefix();
		writeConclusion(conclusion);
		boolean newConclusion = printed_.add(conclusion);
		if (newConclusion) {
			inferenceStack_
					.push(inferences_.getInferences(conclusion).iterator());
		} else {
			// block conclusions appeared earlier in the proof
			writer_.write(" *");
		}
		writer_.newLine();
		return newConclusion;
	}

	private void print(A just) throws IOException {
		writePrefix();
		writer_.write(just.toString());
		writer_.newLine();
	}

	private void process() throws IOException {
		for (;;) {
			// processing inferences
			Iterator<? extends Inference<C>> infIter = inferenceStack_.peek();
			if (infIter == null) {
				return;
			}
			// else
			if (infIter.hasNext()) {
				Inference<C> inf = infIter.next();
				conclusionStack_.push(inf.getPremises().iterator());
				justificationStack_
						.push(justifier_.getJustification(inf).iterator());
			} else {
				inferenceStack_.pop();
			}
			// processing conclusions
			Iterator<? extends C> conclIter = conclusionStack_.peek();
			if (conclIter == null) {
				return;
			}
			// else
			for (;;) {
				if (conclIter.hasNext()) {
					if (process(conclIter.next())) {
						break;
					}
					// else
					continue;
				}
				// else
				// processing justifications
				Iterator<? extends A> justIter = justificationStack_.peek();
				if (justIter == null) {
					return;
				}
				// else
				while (justIter.hasNext()) {
					print(justIter.next());
				}
				conclusionStack_.pop();
				justificationStack_.pop();
				break;
			}
		}
	}

	private void writePrefix() throws IOException {
		Iterator<Iterator<? extends Inference<C>>> inferStackItr = inferenceStack_
				.descendingIterator();
		Iterator<Iterator<? extends C>> conclStackItr = conclusionStack_
				.descendingIterator();
		Iterator<Iterator<? extends A>> justStackItr = justificationStack_
				.descendingIterator();
		while (inferStackItr.hasNext()) {
			Iterator<? extends Inference<C>> inferIter = inferStackItr.next();
			Iterator<? extends C> conclIter = conclStackItr.next();
			Iterator<? extends A> justIter = justStackItr.next();
			boolean hasNextPremise = conclIter.hasNext() || justIter.hasNext();
			if (conclStackItr.hasNext() || justStackItr.hasNext()) {
				writer_.write(hasNextPremise ? "|  "
						: inferIter.hasNext() ? ":  " : "   ");
			} else {
				writer_.write(hasNextPremise ? "+- " : "\\- ");
			}
		}
	}

}
