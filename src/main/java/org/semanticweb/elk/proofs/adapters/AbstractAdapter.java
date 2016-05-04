package org.semanticweb.elk.proofs.adapters;

public abstract class AbstractAdapter<T> {

	protected final T adapted_;

	public AbstractAdapter(final T adapted) {
		this.adapted_ = adapted;
	}

	@Override
	public int hashCode() {
		return adapted_.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}

		if (getClass() != obj.getClass()) {
			return false;
		}

		final AbstractAdapter<?> other = (AbstractAdapter<?>) obj;
		return adapted_ == null ? other.adapted_ == null
				: adapted_.equals(other.adapted_);
	}

	@Override
	public String toString() {
		return adapted_.toString();
	}
	
}
