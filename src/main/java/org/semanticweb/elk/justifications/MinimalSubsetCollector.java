package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.MinimalSubsetsFromInferences;
import org.liveontologies.puli.justifications.PriorityComparators;

public class MinimalSubsetCollector<C, A> {

	private final MinimalSubsetEnumerator.Factory<C, A> enumeratorFactory_;

	private final CancellableMonitor monitor_ = new CancellableMonitor();

	public MinimalSubsetCollector(
			final MinimalSubsetsFromInferences.Factory<C, A> factory,
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		this.enumeratorFactory_ = factory.create(inferenceSet, justifier,
				monitor_);
	}

	public Collection<? extends Set<A>> collect(final C query,
			final int sizeLimit) {
		final int limit = sizeLimit <= 0 ? Integer.MAX_VALUE : sizeLimit;

		final List<Set<A>> sets = new ArrayList<>();

		final MinimalSubsetEnumerator.Listener<A> listener = new MinimalSubsetEnumerator.Listener<A>() {

			@Override
			public void newMinimalSubset(final Set<A> set) {
				if (set.size() <= limit) {
					sets.add(set);
				} else {
					monitor_.cancel();
				}
			}

		};

		enumeratorFactory_.newEnumerator(query).enumerate(listener,
				PriorityComparators.<A> cardinality());

		return sets;
	}

	public Collection<? extends Set<A>> collect(final C query) {
		return collect(query, Integer.MAX_VALUE);
	}

	private static class CancellableMonitor implements InterruptMonitor {

		private volatile boolean cancelled_ = false;

		@Override
		public boolean isInterrupted() {
			return cancelled_;
		}

		public void cancel() {
			cancelled_ = true;
		}

	}

}
