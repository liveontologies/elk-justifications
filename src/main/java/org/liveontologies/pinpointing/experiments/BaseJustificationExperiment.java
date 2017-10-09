package org.liveontologies.pinpointing.experiments;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.liveontologies.puli.pinpointing.MinimalSubsetEnumerator;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.Stats;

public abstract class BaseJustificationExperiment<C, I extends Inference<? extends C>, A>
		extends AbstractJustificationExperiment {

	private MinimalSubsetsFromProofs.Factory<C, I, A> factory_;

	private volatile MinimalSubsetEnumerator.Factory<C, A> computation_ = null;
	private volatile C goal_;
	private volatile Proof<? extends I> proof_;
	private volatile InferenceJustifier<? super I, ? extends Set<? extends A>> justifier_;

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
			final MinimalSubsetsFromProofs.Factory<C, I, A> factory = (MinimalSubsetsFromProofs.Factory<C, I, A>) getFactory
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
	public void before(final String query) throws ExperimentException {
		justificationCounter_.reset();
		if (computation_ != null) {
			Stats.resetStats(computation_);
		}

		goal_ = decodeQuery(query);
		proof_ = newProof(goal_);
		justifier_ = newJustifier();

	}

	@Override
	public void run(final InterruptMonitor monitor) throws ExperimentException {

		computation_ = factory_.create(proof_, justifier_, monitor);
		computation_.newEnumerator(goal_).enumerate(justificationCounter_);

	}

	protected abstract C decodeQuery(String query) throws ExperimentException;

	protected abstract Proof<? extends I> newProof(C query)
			throws ExperimentException;

	protected abstract InferenceJustifier<? super I, ? extends Set<? extends A>> newJustifier()
			throws ExperimentException;

	@Override
	public void after() throws ExperimentException {
		// Empty.
	}

	@Override
	public void dispose() {
		// Empty.
	}

	@NestedStats
	public MinimalSubsetEnumerator.Factory<C, A> getJustificationComputation() {
		return computation_;
	}

	private class JustificationCounter
			implements MinimalSubsetEnumerator.Listener<A> {

		@Override
		public void newMinimalSubset(final Set<A> justification) {
			fireNewJustification();
		}

		public void reset() {
			// Empty.
		}

	}

}
