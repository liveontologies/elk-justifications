package org.semanticweb.elk.justifications;

import java.util.Set;

/**
 * The set interface enhanced with methods for handling justifications
 * 
 * @author Yevgeny Kazakov
 *
 * @param <E>
 *            the type of elements maintained by this set
 */
public interface JSet<E> extends Set<E> {

	/**
	 * @return {@code true} if this justification is obsolete, i.e., a subset of
	 *         this justification has been found; it is {@code false} if the
	 *         justification is empty
	 */
	boolean isObsolete();

	/**
	 * marks this justification as obsolete; after that {@link #isObsolete()}
	 * returns {@code true}
	 */
	void setObsolete();

}
