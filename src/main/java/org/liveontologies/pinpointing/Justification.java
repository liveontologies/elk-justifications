package org.liveontologies.pinpointing;

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
public interface Justification<C, A> extends Set<A> {

	/**
	 * @return the conclusion for which this justification is computed
	 */
	C getConclusion();

	/**
	 * Copies the justification to another conclusion
	 * 
	 * @param conclusion
	 * @return the justification containing the same axioms as this
	 *         justification but for the given conclusion
	 */
	public Justification<C, A> copyTo(C conclusion);

	/**
	 * @param added
	 * @return a justification obtained from this one by adding all elements in
	 *         the given set; this justification is not modified
	 */
	public Justification<C, A> addElements(Set<? extends A> added);

	/**
	 * @param removed
	 * @return a justification obtained from this one by removing all elements
	 *         in the given set; this justification is not modified
	 */
	public Justification<C, A> removeElements(Set<? extends A> removed);

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
