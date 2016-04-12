package org.semanticweb.elk.proofs;

import java.util.Collection;
import java.util.Set;

/**
 * A general type of inferences, which can be used in proofs. If all premises of
 * an inference are provable, then one can prove its conclusion by applying this
 * inference. An inference is also associated with a set of axioms that justify
 * this inference. This could be, for example, axioms occurring in the ontology.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusions and premises this inference operate with
 * @param <A>
 *            the type of axioms this inference operates with
 */
public interface Inference<C, A> {

	/**
	 * @return the conclusion that is derived using this inference
	 */
	C getConclusion();

	/**
	 * @return the premises from which the conclusion of this inference is
	 *         derived
	 */
	Collection<? extends C> getPremises();

	/**
	 * @return the axioms by which this inference is justified; the axioms can
	 *         be different from premises in the sense that may not be derived
	 */
	Set<? extends A> getJustification();

}
