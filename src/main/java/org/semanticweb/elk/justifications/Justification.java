package org.semanticweb.elk.justifications;

import java.util.Set;

/**
 * The set interface enhanced with methods for handling justifications
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of the conclusion for which the justification is computed
 * @param <A>
 *            the type of axioms in the justification
 */
public interface Justification<C, A>
		extends Set<A>, Comparable<Justification<C, A>> {

	/**
	 * @return the conclusion for which this justification is computed
	 */
	C getConclusion();

	/**
	 * @return the age of this justifications, i.e., the depth of the proof that
	 *         derives the conclusion from the axioms in the justification
	 */
	int getAge();

	/**
	 * The visitor pattern for instances
	 * 
	 * @author Yevgeny Kazakov
	 *
	 * @param <C>
	 *            the type of the conclusion for which the justification is
	 *            computed
	 * @param <A>
	 *            the type of axioms in the justification
	 *
	 * @param <O>
	 *            the type of the output
	 */
	public interface Visitor<C, A, O> {

		O visit(Justification<C, A> just);

	}

}
