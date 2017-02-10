package org.semanticweb.elk.proofs;

import java.util.Collection;

import org.liveontologies.puli.JustifiedInference;

/**
 * Static methods for printing inferences
 * 
 * @author Yevgeny Kazakov
 */
public class InferencePrinter {

	public static <C, A> String toString(JustifiedInference<C, A> inference) {
		String result = inference.getConclusion() + " -| ";
		boolean first = true;
		for (C premise : inference.getPremises()) {
			if (!first) {
				result += "; ";
			} else {
				first = false;
			}
			result += premise;
		}
		Collection<? extends A> axioms = inference.getJustification();
		if (axioms.isEmpty()) {
			return result;
		}
		// else
		result += " [";
		first = true;
		for (A axiom : inference.getJustification()) {
			if (!first) {
				result += "; ";
			} else {
				first = false;
			}
			result += axiom;
		}
		result += "]";
		return result;
	}

}
