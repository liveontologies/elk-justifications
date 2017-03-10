package org.semanticweb.elk.justifications.experiments;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.ResolutionJustificationComputation;
import org.semanticweb.elk.statistics.NestedStats;
import org.semanticweb.elk.statistics.Stats;

public abstract class ResolutionJustificationExperiment<C, A>
		extends JustificationExperiment {

	private final ResolutionJustificationComputation.SelectionFunction<C, A> selection_;

	private volatile JustificationComputation<C, A> computation_ = null;

	private final JustificationCounter justificationCounter_ = new JustificationCounter();

	public ResolutionJustificationExperiment(final String[] args)
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
			final Constructor<?> constructor = computationClass
					.getConstructor();
			final Object object = constructor.newInstance();
			@SuppressWarnings("unchecked")
			final ResolutionJustificationComputation.SelectionFunction<C, A> selection = (ResolutionJustificationComputation.SelectionFunction<C, A>) object;
			this.selection_ = selection;
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
		} catch (final InstantiationException e) {
			throw new ExperimentException(e);
		}

	}

	@Override
	public void init() {
		justificationCounter_.reset();
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
		computation_ = new ResolutionJustificationComputation<>(inferenceSet,
				monitor, selection_);
		computation_.enumerateJustifications(goal, null, justificationCounter_);

	}

	protected abstract C decodeQuery(String query) throws ExperimentException;

	protected abstract GenericInferenceSet<C, ? extends JustifiedInference<C, A>> newInferenceSet(
			C query) throws ExperimentException;

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
