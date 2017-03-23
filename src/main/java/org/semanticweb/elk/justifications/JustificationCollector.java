package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.liveontologies.puli.justifications.InterruptMonitor;

public class JustificationCollector<C, A> {

	private final JustificationComputation<C, A> computation_;

	private final CancellableMonitor monitor_ = new CancellableMonitor();

	public JustificationCollector(
			final JustificationComputation.Factory<C, A> factory,
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences) {
		this.computation_ = factory.create(inferences, monitor_);
	}

	public Collection<? extends Set<A>> collectJustifications(final C query,
			final int sizeLimit) {
		final int limit = sizeLimit <= 0 ? Integer.MAX_VALUE : sizeLimit;

		final List<Set<A>> justifications = new ArrayList<>();

		final JustificationComputation.Listener<A> listener = new JustificationComputation.Listener<A>() {

			@Override
			public void newJustification(final Set<A> justification) {
				if (justification.size() <= limit) {
					justifications.add(justification);
				} else {
					monitor_.cancel();
				}
			}

		};

		computation_.enumerateJustifications(query, SIZE_ORDER_, listener);

		return justifications;
	}

	public Collection<? extends Set<A>> collectJustifications(final C query) {
		return collectJustifications(query, Integer.MAX_VALUE);
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

	private Comparator<? super Set<A>> SIZE_ORDER_ = new Comparator<Set<A>>() {

		@Override
		public int compare(final Set<A> just1, final Set<A> just2) {
			return Integer.compare(just1.size(), just2.size());
		}

	};

}
