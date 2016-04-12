package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A set with optimized implementation of inclusion of a collection, in case the
 * collection is of the same type.
 * 
 * @see Set#containsAll(Collection)
 * 
 * @author Yevgeny Kazakov
 *
 * @param <E>
 *            the type of elements maintained by this set
 */
class CountingHashSet<E> extends HashSet<E> {

	private static final long serialVersionUID = -2805422564617676450L;

	private static final int MASK_ = 15;

	private final int[] counts_ = new int[MASK_ + 1];

	public CountingHashSet() {
		super();
	}

	public CountingHashSet(int initialCapacity) {
		super(initialCapacity);
	}

	public CountingHashSet(Collection<? extends E> c) {
		this(c.size());
		addAll(c);
	}

	@Override
	public boolean add(E e) {
		boolean success = super.add(e);
		if (success) {
			counts_[e.hashCode() & MASK_] += 1;
		}
		return success;
	}

	@Override
	public boolean remove(Object o) {
		boolean success = super.remove(o);
		if (success) {
			counts_[o.hashCode() & MASK_] -= 1;
		}
		return success;
	}

	@Override
	public void clear() {
		super.clear();
		for (int i = 0; i < counts_.length; i++) {
			counts_[i] = 0;
		}
	}

	@Override
	public boolean containsAll(Collection<?> other) {
		if (other instanceof CountingHashSet<?>) {
			int[] otherCounts = ((CountingHashSet<?>) other).counts_;
			for (int i = 0; i < counts_.length; i++) {
				if (counts_[i] < otherCounts[i]) {
					return false;
				}
			}
		}
		return super.containsAll(other);
	}

}
