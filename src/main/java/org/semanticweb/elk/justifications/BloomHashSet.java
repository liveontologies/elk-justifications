package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set enhanced with a Bloom filter to quickly check containment of elements.
 * As common with Bloom filters, removal of elements is not supported.
 * 
 * @see Set#contains(Object)
 * @see Set#containsAll(Collection)
 * 
 * @author Yevgeny Kazakov
 *
 * @param <E>
 *            the type of elements maintained by this set
 */
class BloomHashSet<E> extends HashSet<E> {

	private static final long serialVersionUID = -4655731436488514715L;

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(BloomHashSet.class);

	private static final boolean COLLECT_STATS_ = true;

	private static int STATS_CONTAINS_ALL_COUNT_ = 0,
			STATS_CONTAINS_ALL_FILTERED_ = 0;

	private static final short SHIFT_ = 6;

	private static final int MASK_ = (1 << SHIFT_) - 1;

	private long filter_ = 0L;

	public BloomHashSet() {
		super();
	}

	public BloomHashSet(int initialCapacity) {
		super(initialCapacity);
	}

	public BloomHashSet(Collection<? extends E> c) {
		this(c.size());
		addAll(c);
	}

	@Override
	public boolean add(E e) {
		boolean success = super.add(e);
		if (success) {
			int hash = e.hashCode();
			filter_ |= 1 << (hash & MASK_);
			hash >>= SHIFT_;
			filter_ |= 1 << (hash & MASK_);
			hash >>= SHIFT_;
			filter_ |= 1 << (hash & MASK_);
		}
		return success;
	}

	@Override
	public boolean contains(Object o) {
		int hash = o.hashCode();
		long mask = 1 << (hash & MASK_);
		hash >>= SHIFT_;
		mask |= 1 << (hash & MASK_);
		hash >>= SHIFT_;
		mask |= 1 << (hash & MASK_);
		if ((filter_ & mask) != mask) {
			return false;
		}
		// else
		return super.contains(o);
	}

	@Override
	public boolean remove(Object o) {
		throw new UnsupportedOperationException(
				"Removal of elements not supported");
	}

	@Override
	public void clear() {
		super.clear();
		filter_ = 0;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		if (COLLECT_STATS_) {
			STATS_CONTAINS_ALL_COUNT_++;
		}
		if (c instanceof BloomHashSet<?>) {
			long otherFilter = ((BloomHashSet<?>) c).filter_;
			if ((filter_ & otherFilter) != otherFilter) {
				if (COLLECT_STATS_) {
					STATS_CONTAINS_ALL_FILTERED_++;
				}
				return false;
			}
		}
		for (Object e : c)
			// no need to apply filter for contains test
			if (!super.contains(e))
				return false;
		return true;
	}

	public static void printStatistics() {

		if (LOGGER_.isDebugEnabled()) {
			if (STATS_CONTAINS_ALL_COUNT_ != 0) {
				float containsAllSuccessRatio = (float) STATS_CONTAINS_ALL_FILTERED_
						/ STATS_CONTAINS_ALL_COUNT_;
				LOGGER_.debug(
						"{} out of {} ({}%) containsAll(Collection) tests filtered",
						STATS_CONTAINS_ALL_FILTERED_, STATS_CONTAINS_ALL_COUNT_,
						String.format("%.2f", containsAllSuccessRatio * 100));
			}
		}
	}

	public static void resetStatistics() {
		STATS_CONTAINS_ALL_COUNT_ = 0;
		STATS_CONTAINS_ALL_FILTERED_ = 0;
	}

}
