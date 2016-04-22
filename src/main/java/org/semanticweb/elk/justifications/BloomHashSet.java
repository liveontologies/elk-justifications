package org.semanticweb.elk.justifications;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 * @param <C>
 *            the type of the conclusion for which the justification is computed
 * @param <A>
 *            the type of axioms in the justification
 */
class BloomHashSet<C, A> extends HashSet<A> implements Justification<C, A> {

	private static final long serialVersionUID = -4655731436488514715L;

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(BloomHashSet.class);

	private static final boolean COLLECT_STATS_ = true;

	public static final String STAT_NAME_CONTAINS_ALL_COUNT = "BloomHashSet.CONTAINS_ALL_COUNT";
	public static final String STAT_NAME_CONTAINS_ALL_POSITIVE = "BloomHashSet.CONTAINS_ALL_POSITIVE";
	public static final String STAT_NAME_CONTAINS_ALL_FILTERED = "BloomHashSet.CONTAINS_ALL_FILTERED";

	private static long STATS_CONTAINS_ALL_COUNT_ = 0,
			STATS_CONTAINS_ALL_POSITIVE_ = 0, STATS_CONTAINS_ALL_FILTERED_ = 0;

	private static final short SHIFT_ = 7; // 2^7 = 128

	// = 11..1 SHIFT_ times
	private static final int MASK_ = (1 << SHIFT_) - 1;

	private final C conclusion_;

	/**
	 * the age of this justification
	 */
	private final int age_;

	/**
	 * filters for subset tests
	 */
	private long filter1_ = 0L;
	private long filter2_ = 0L;

	/**
	 * marks if this set as obsolete (i.e., not a minimal justifications)
	 */
	private boolean obsolete_ = false;

	@SafeVarargs
	public BloomHashSet(C conclusion, int age,
			Collection<? extends A>... collections) {
		super(getCombinedSize(collections));
		this.conclusion_ = conclusion;
		this.age_ = age;
		for (int i = 0; i < collections.length; i++) {
			addAll(collections[i]);
		}
	}

	@SafeVarargs
	private static <E> int getCombinedSize(
			Collection<? extends E>... collections) {
		int result = 0;
		for (int i = 0; i < collections.length; i++) {
			result += collections[i].size();
		}
		return result;
	}

	@Override
	public C getConclusion() {
		return conclusion_;
	}

	@Override
	public boolean isObsolete() {
		return obsolete_;
	}

	@Override
	public int getAge() {
		return age_;
	}

	@Override
	public void setObsolete() {
		obsolete_ = true;
	}

	@Override
	public boolean add(A e) {
		boolean success = super.add(e);
		if (success) {
			int shift = e.hashCode() & MASK_;
			if (shift < 64) {
				filter1_ |= 1 << shift;
			} else {
				shift -= 64;
				filter2_ |= 1 << shift;
			}
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
		filter1_ = 0;
		filter2_ = 0;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		if (COLLECT_STATS_) {
			STATS_CONTAINS_ALL_COUNT_++;
		}
		if (c instanceof BloomHashSet<?, ?>) {
			BloomHashSet<?, ?> other = (BloomHashSet<?, ?>) c;
			if ((filter1_ & other.filter1_) != other.filter1_
					|| (filter2_ & other.filter2_) != other.filter2_) {
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

	@Override
	public String toString() {
		Object[] elements = toArray();
		Arrays.sort(elements, new Comparator<Object>() {
			@Override
			public int compare(Object o1, Object o2) {
				return String.valueOf(o1).compareTo(String.valueOf(o2));
			}
		});
		return getAge() + "-gen-" + getConclusion() + ": "
				+ Arrays.toString(elements);
	}
	
	@Override
	public int compareTo(Justification<C, A> o) {
		// first prioritize smaller justifications
		int sizeDiff = size() - o.size();
		if (sizeDiff != 0) {
			return sizeDiff;
		}		
		// next prioritize younger justifications
		return getAge() - o.getAge();
	}


	public static String[] getStatNames() {
		return new String[] { STAT_NAME_CONTAINS_ALL_COUNT,
				STAT_NAME_CONTAINS_ALL_POSITIVE,
				STAT_NAME_CONTAINS_ALL_FILTERED, };
	}

	public static Map<String, Object> getStatistics() {
		final Map<String, Object> stats = new HashMap<String, Object>();
		stats.put(STAT_NAME_CONTAINS_ALL_COUNT, STATS_CONTAINS_ALL_COUNT_);
		stats.put(STAT_NAME_CONTAINS_ALL_POSITIVE,
				STATS_CONTAINS_ALL_POSITIVE_);
		stats.put(STAT_NAME_CONTAINS_ALL_FILTERED,
				STATS_CONTAINS_ALL_FILTERED_);
		return stats;
	}

	public static void logStatistics() {

		if (LOGGER_.isDebugEnabled()) {
			if (STATS_CONTAINS_ALL_COUNT_ != 0) {
				long negativeTests = STATS_CONTAINS_ALL_COUNT_
						- STATS_CONTAINS_ALL_POSITIVE_;
				if (negativeTests > 0) {
					float negativeSuccessRatio = (float) STATS_CONTAINS_ALL_FILTERED_
							/ negativeTests;
					LOGGER_.debug(
							"{} containsAll tests, {} negative, {} ({}%) filtered",
							STATS_CONTAINS_ALL_COUNT_, negativeTests,
							STATS_CONTAINS_ALL_FILTERED_,
							String.format("%.2f", negativeSuccessRatio * 100));
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
