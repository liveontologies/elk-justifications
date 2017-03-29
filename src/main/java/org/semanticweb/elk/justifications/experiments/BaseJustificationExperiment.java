package org.semanticweb.elk.justifications.experiments;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.liveontologies.puli.justifications.JustificationComputation.Factory;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.Stats;

public abstract class BaseJustificationExperiment<C, A>
		implements JustificationExperiment {

	private JustificationComputation.Factory<C, A> factory_;

	private volatile JustificationComputation<C, A> computation_ = null;

	private final JustificationCounter justificationCounter_ = new JustificationCounter();

	@Override
	public void init(final String[] args) throws ExperimentException {

		final int requiredArgCount = 1;

		if (args.length < requiredArgCount) {
			throw new ExperimentException("Insufficient arguments!");
		}

		final String computationClassName = args[0];

		try {
			final Class<?> computationClass = Class
					.forName(computationClassName);
			final Method getFactory = computationClass.getMethod("getFactory");
			@SuppressWarnings("unchecked")
			final Factory<C, A> factory = (JustificationComputation.Factory<C, A>) getFactory
					.invoke(null);
			factory_ = factory;
		} catch (final ClassNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final NoSuchMethodException e) {
			throw new ExperimentException(e);
		} catch (final SecurityException e) {
			throw new ExperimentException(e);
		} catch (final IllegalAccessException e) {
			throw new ExperimentException(e);
		} catch (final IllegalArgumentException e) {
			throw new ExperimentException(e);
		} catch (final InvocationTargetException e) {
			throw new ExperimentException(e);
		}

	}

	@Override
	public void before() {
		justificationCounter_.reset();
		if (computation_ != null) {
			Stats.resetStats(computation_);
		}
	}

	@Override
	public void run(final String query, final InterruptMonitor monitor)
			throws ExperimentException {

		final C goal = decodeQuery(query);
		final InferenceSet<C> inferenceSet = newInferenceSet(goal);
		final InferenceJustifier<C, ? extends Set<? extends A>> justifier = newJustifier();
		computation_ = factory_.create(inferenceSet, justifier, monitor);
		computation_.enumerateJustifications(goal, null, justificationCounter_);

	}

	protected abstract C decodeQuery(String query) throws ExperimentException;

	protected abstract InferenceSet<C> newInferenceSet(C query)
			throws ExperimentException;

	protected abstract InferenceJustifier<C, ? extends Set<? extends A>> newJustifier()
			throws ExperimentException;

	@Override
	public void after() throws ExperimentException {
		// Empty.
	}

	@Override
	public void dispose() {
		// Empty.
	}

	@Override
	public int getJustificationCount() {
		return justificationCounter_.getCount();
	}

	@NestedStats
	public JustificationComputation<C, A> getJustificationComputation() {
		return computation_;
	}

	private class JustificationCounter
			implements JustificationComputation.Listener<A> {

		private volatile int count_ = 0;

		@Override
		public void newJustification(final Set<A> justification) {
			count_++;
		}

		public int getCount() {
			return count_;
		}

		public void reset() {
			count_ = 0;
		}

	}

}
