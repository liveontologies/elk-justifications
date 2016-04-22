package org.semanticweb.elk.proofs;

import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

/**
 * A simple pretty printer for proofs.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of the conclusions in proofs
 * @param <A>
 *            the type of the axioms in proofs
 */
public class ProofPrinter<C, A> {

	private final InferenceSet<C, A> inferences_;

	private final Deque<Iterator<Inference<C, A>>> inferenceStack_ = new LinkedList<Iterator<Inference<C, A>>>();

	private final Deque<Iterator<? extends C>> conclusionStack_ = new LinkedList<Iterator<? extends C>>();

	private final Deque<Iterator<? extends A>> justificationStack_ = new LinkedList<Iterator<? extends A>>();

	private final Set<C> visited_ = new HashSet<C>();

	protected ProofPrinter(InferenceSet<C, A> inferences) {
		this.inferences_ = inferences;
	}

	public void printProof(C conclusion) {
		process(conclusion);
		process();
	}

	public static <C, A> void print(InferenceSet<C, A> inferences,
			C conclusion) {
		ProofPrinter<C, A> pp = new ProofPrinter<>(inferences);
		pp.printProof(conclusion);
	}

	private boolean process(C conclusion) {
		StringBuilder sb = new StringBuilder();
		appendPrefix(sb);
		appendConclusion(sb, conclusion);
		boolean result = false;
		if (visited_.add(conclusion)) {
			inferenceStack_
					.push(inferences_.getInferences(conclusion).iterator());
			result = true;
		} else {
			sb.append(" *");
		}
		System.out.println(sb);
		return result;
	}

	private void print(A just) {
		StringBuilder sb = new StringBuilder();
		appendPrefix(sb).append(just);
		System.out.println(sb);
	}

	protected void appendConclusion(StringBuilder sb, C conclusion) {
		// can be overridden
		sb.append(conclusion);
	}

	private void process() {
		for (;;) {
			// processing inferences
			Iterator<Inference<C, A>> infIter = inferenceStack_.peek();
			if (infIter == null) {
				return;
			}
			// else
			if (infIter.hasNext()) {
				Inference<C, A> inf = infIter.next();
				conclusionStack_.push(inf.getPremises().iterator());
				justificationStack_.push(inf.getJustification().iterator());
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
				} else {
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
	}

	StringBuilder appendPrefix(StringBuilder sb) {
		Iterator<Iterator<? extends C>> conclStackItr = conclusionStack_
				.descendingIterator();
		Iterator<Iterator<? extends A>> justStackItr = justificationStack_
				.descendingIterator();
		while (conclStackItr.hasNext()) {
			Iterator<? extends C> conclIter = conclStackItr.next();
			Iterator<? extends A> justIter = justStackItr.next();
			boolean hasNext = conclIter.hasNext() || justIter.hasNext();
			if (conclStackItr.hasNext()) {
				sb.append(hasNext ? "|  " : "   ");
			} else {
				sb.append(hasNext ? "+- " : "\\- ");
			}
		}
		return sb;
	}

}
