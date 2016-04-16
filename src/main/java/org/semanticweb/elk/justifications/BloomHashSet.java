package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set enhanced with a Bloom filter to quickly check set inclusion. The Bloom
 * filter uses just one hash function; containment of elements is not optimized.
 * As it is common with Bloom filters, removal of elements is not supported.
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
			STATS_CONTAINS_ALL_POSITIVE_ = 0, STATS_CONTAINS_ALL_FILTERED_ = 0;

	private static final short SHIFT_ = 6; // 2^6 = 64

	// = 11..1 SHIFT_ times
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
			filter_ |= 1 << (e.hashCode() & MASK_);
		}
		return success;
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
		if (super.containsAll(c)) {
			if (COLLECT_STATS_) {
				STATS_CONTAINS_ALL_POSITIVE_++;
			}
			return true;
		}
		// else
		return false;
	}

	public static void logStatistics() {

		if (LOGGER_.isDebugEnabled()) {
			if (STATS_CONTAINS_ALL_COUNT_ != 0) {
				int negativeTests = STATS_CONTAINS_ALL_COUNT_
						- STATS_CONTAINS_ALL_POSITIVE_;
				if (negativeTests > 0) {
					float containsAllSuccessRatio = (float) STATS_CONTAINS_ALL_FILTERED_
							/ STATS_CONTAINS_ALL_COUNT_;
					LOGGER_.debug(
							"{} containsAll tests, {} negative, {} ({}%) filtered",
							STATS_CONTAINS_ALL_COUNT_, negativeTests,
							STATS_CONTAINS_ALL_FILTERED_, String.format("%.2f",
									containsAllSuccessRatio * 100));
				} else {
					LOGGER_.debug("{} containsAll tests, all positive",
							STATS_CONTAINS_ALL_COUNT_);
				}
			}
		}
	}

	public static void resetStatistics() {
		STATS_CONTAINS_ALL_COUNT_ = 0;
		STATS_CONTAINS_ALL_FILTERED_ = 0;
		STATS_CONTAINS_ALL_POSITIVE_ = 0;
	}

}
