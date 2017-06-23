package org.semanticweb.elk.justifications.experiments;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.MinimalSubsetsFromProofs;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.Stats;

public abstract class BaseJustificationExperiment<C, A>
		extends AbstractJustificationExperiment {

	private MinimalSubsetsFromProofs.Factory<C, A> factory_;

	private volatile MinimalSubsetEnumerator.Factory<C, A> computation_ = null;
	private volatile C goal_;
	private volatile Proof<C> proof_;
	private volatile InferenceJustifier<C, ? extends Set<? extends A>> justifier_;

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
			final MinimalSubsetsFromProofs.Factory<C, A> factory = (MinimalSubsetsFromProofs.Factory<C, A>) getFactory
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
	public String before(final String query) throws ExperimentException {
		justificationCounter_.reset();
		if (computation_ != null) {
			Stats.resetStats(computation_);
		}

		goal_ = decodeQuery(query);
		proof_ = newProof(goal_);
		justifier_ = newJustifier();

		return query;
	}

	@Override
	public void run(final InterruptMonitor monitor) throws ExperimentException {

		computation_ = factory_.create(proof_, justifier_, monitor);
		computation_.newEnumerator(goal_).enumerate(justificationCounter_);

	}

	protected abstract C decodeQuery(String query) throws ExperimentException;

	protected abstract Proof<C> newProof(C query) throws ExperimentException;

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
	public MinimalSubsetEnumerator.Factory<C, A> getJustificationComputation() {
		return computation_;
	}

	private class JustificationCounter
			implements MinimalSubsetEnumerator.Listener<A> {

		private volatile int count_ = 0;

		@Override
		public void newMinimalSubset(final Set<A> justification) {
			fireNewJustification();
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
