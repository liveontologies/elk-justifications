package org.semanticweb.elk.justifications;

import java.util.Set;

import org.liveontologies.puli.justifications.ComparableWrapper;

public class SetRoStringLengthComparable<E>
		implements ComparableWrapper<Set<E>, SetRoStringLengthComparable<E>> {

	private final Set<E> wrapped_;

	private final int length_;

	public SetRoStringLengthComparable(final Set<E> wrapped) {
		this.wrapped_ = wrapped;
		this.length_ = wrapped.toString().length();
	}

	@Override
	public int compareTo(final SetRoStringLengthComparable<E> other) {
		return Integer.compare(length_, other.length_);
	}

	@Override
	public Set<E> getWrapped() {
		return wrapped_;
	}

	private static class Factory<E> implements
			ComparableWrapper.Factory<Set<E>, SetRoStringLengthComparable<E>> {

		@Override
		public SetRoStringLengthComparable<E> wrap(final Set<E> delegate) {
			return new SetRoStringLengthComparable<E>(delegate);
		}

	}

	private static final Factory<?> FACTORY_ = new Factory<>();

	@SuppressWarnings("unchecked")
	public static <E> Factory<E> getFactory() {
		return (Factory<E>) FACTORY_;
	}

}
