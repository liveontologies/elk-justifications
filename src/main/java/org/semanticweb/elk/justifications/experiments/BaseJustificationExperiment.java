package org.semanticweb.elk.justifications.experiments;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation.Factory;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.statistics.NestedStats;
import org.semanticweb.elk.statistics.Stats;

public abstract class BaseJustificationExperiment<C, A>
		extends JustificationExperiment {

	private final JustificationComputation.Factory<C, A> factory_;

	private volatile JustificationComputation<C, A> computation_ = null;

	public BaseJustificationExperiment(final String[] args)
			throws ExperimentException {
		super(args);

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
	public void init() {
		super.init();
		if (computation_ != null) {
			Stats.resetStats(computation_);
		}
	}

	@Override
	public void run(final String query, final Monitor monitor)
			throws ExperimentException {

		final C goal = decodeQuery(query);
		final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet = newInferenceSet(
				goal);
		computation_ = factory_.create(inferenceSet, monitor);
		// TODO: enumerate !!!
		final Collection<? extends Set<A>> justifications = computation_
				.computeJustifications(goal);

		justCount = justifications.size();
	}

	protected abstract C decodeQuery(String query) throws ExperimentException;

	protected abstract GenericInferenceSet<C, ? extends JustifiedInference<C, A>> newInferenceSet(
			C query) throws ExperimentException;

	@NestedStats
	public JustificationComputation<C, A> getJustificationComputation() {
		return computation_;
	}

}
