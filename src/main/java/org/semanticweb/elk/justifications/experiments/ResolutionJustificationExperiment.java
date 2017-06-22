package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.ResolutionJustificationComputation;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.Stat;
import org.liveontologies.puli.statistics.Stats;
import org.semanticweb.elk.justifications.RunJustificationExperiments;
import org.semanticweb.elk.justifications.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public abstract class ResolutionJustificationExperiment<C, A>
		implements JustificationExperiment {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(ResolutionJustificationExperiment.class);

	public static final String SELECTION_OPT = "selection";
	public static final String SAVE_OPT = "s";

	public static final String INDEX_FILE_NAME = "axiom_index";

	private ResolutionJustificationComputation.SelectionFactory<C, A> selectionFactory_;

	private File outputDir_;
	private PrintWriter indexWriter_;
	private Utils.Index<A> axiomIndex_;

	private volatile MinimalSubsetEnumerator.Factory<C, A> computation_ = null;
	private volatile String lastQuery_ = null;
	private volatile C goal_;
	private volatile Proof<C> proof_;
	private volatile InferenceJustifier<C, ? extends Set<? extends A>> justifier_;
	private volatile long runStartTimeNanos_;

	private JustificationCounter justificationListener_;

	// Statistics
	private int minJustSizeize_, maxJustSize_;
	private double obtainingInferencesTimeMillis_;
	private double firstQuartileJustSize_, medianJustSize_, meanJustSize_,
			thirdQuartileJustSize_;
	@Stat
	public String just1Time;
	@Stat
	public String just2Time;
	@Stat
	public String justHalfTime;

	@Override
	public final void init(final String[] args) throws ExperimentException {

		final ArgumentParser parser = ArgumentParsers
				.newArgumentParser(getClass().getSimpleName()).description(
						"Experiment using Resolutionun Justification Computation.");
		parser.addArgument(SELECTION_OPT).help("selection class name");
		parser.addArgument("-" + SAVE_OPT).type(File.class).help(
				"if provided, save justification into specified directory");

		addArguments(parser);

		try {

			final Namespace options = parser.parseArgs(args);

			final String selectorClassName = options.get(SELECTION_OPT);

			final Class<?> selectorClass = Class.forName(selectorClassName);
			final Constructor<?> constructor = selectorClass.getConstructor();
			final Object object = constructor.newInstance();
			@SuppressWarnings("unchecked")
			final ResolutionJustificationComputation.SelectionFactory<C, A> selectionFactory = (ResolutionJustificationComputation.SelectionFactory<C, A>) object;
			this.selectionFactory_ = selectionFactory;

			this.outputDir_ = options.get(SAVE_OPT);
			if (outputDir_ == null) {
				this.justificationListener_ = new JustificationCounter();
				this.indexWriter_ = null;
				this.axiomIndex_ = null;
			} else {
				Utils.cleanDir(outputDir_);
				this.justificationListener_ = new JustificationCollector();
				this.indexWriter_ = new PrintWriter(new FileWriter(
						new File(outputDir_, INDEX_FILE_NAME), true));
				this.axiomIndex_ = new Utils.Index<>(new Utils.IndexRecorder<>(
						new Utils.Counter(1), indexWriter_));
			}

			init(options);

		} catch (final ClassNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final NoSuchMethodException e) {
			throw new ExperimentException(e);
		} catch (final SecurityException e) {
			throw new ExperimentException(e);
		} catch (final IllegalAccessException e) {
			throw new ExperimentException(e);
		} catch (final InvocationTargetException e) {
			throw new ExperimentException(e);
		} catch (final InstantiationException e) {
			throw new ExperimentException(e);
		} catch (final IOException e) {
			throw new ExperimentException(e);
		} catch (final ArgumentParserException e) {
			parser.handleError(e);
			System.exit(2);
		}

	}

	protected abstract void addArguments(ArgumentParser parser);

	protected abstract void init(Namespace options) throws ExperimentException;

	@Override
	public String before(final String query) throws ExperimentException {
		justificationListener_.reset();
		resetStats();
		if (computation_ != null) {
			Stats.resetStats(computation_);
		}

		lastQuery_ = query;

		final long startTimeNanos = System.nanoTime();
		goal_ = decodeQuery(query);
		proof_ = newProof(goal_);
		justifier_ = newJustifier();
		obtainingInferencesTimeMillis_ = (System.nanoTime() - startTimeNanos)
				/ RunJustificationExperiments.NANOS_IN_MILLIS;

		return query;
	}

	@Override
	public void run(final InterruptMonitor monitor) throws ExperimentException {
		runStartTimeNanos_ = System.nanoTime();

		computation_ = ResolutionJustificationComputation.<C, A> getFactory()
				.create(proof_, justifier_, monitor, selectionFactory_);
		computation_.newEnumerator(goal_).enumerate(justificationListener_);

	}

	protected abstract C decodeQuery(String query) throws ExperimentException;

	protected abstract Proof<C> newProof(C query) throws ExperimentException;

	protected abstract InferenceJustifier<C, ? extends Set<? extends A>> newJustifier()
			throws ExperimentException;

	@Override
	public void after() throws ExperimentException {

		computeJustStats(justificationListener_.getSizes());

		final List<Long> justTimes = justificationListener_.getTimes();
		if (justTimes != null && justTimes.size() >= 1) {
			just1Time = "" + ((justTimes.get(0) - runStartTimeNanos_)
					/ RunJustificationExperiments.NANOS_IN_MILLIS);
			if (justTimes.size() >= 2) {
				just2Time = "" + ((justTimes.get(1) - runStartTimeNanos_)
						/ RunJustificationExperiments.NANOS_IN_MILLIS);
			}
			final int halfIndex;
			final int size = justTimes.size();
			if (size % 2 == 0) {
				halfIndex = size / 2 - 1;
			} else {
				halfIndex = size / 2;
			}
			justHalfTime = "" + ((justTimes.get(halfIndex) - runStartTimeNanos_)
					/ RunJustificationExperiments.NANOS_IN_MILLIS);
		}

		if (outputDir_ == null) {
			return;
		}
		// else

		final Collection<Set<A>> justs = justificationListener_
				.getJustifications();

		PrintWriter out = null;
		try {

			out = new PrintWriter(
					new File(outputDir_, Utils.toFileName(lastQuery_)));

			for (final Set<A> just : justs) {
				for (final A axiom : just) {
					out.print(axiomIndex_.get(axiom));
					out.print(" ");
				}
				out.println();
			}

		} catch (final FileNotFoundException e) {
			LOGGER_.error(e.getMessage(), e);
		} finally {
			Utils.closeQuietly(out);
		}

	}

	@Override
	public void dispose() {
		Utils.closeQuietly(indexWriter_);
	}

	@Override
	public int getJustificationCount() {
		return justificationListener_.getCount();
	}

	@NestedStats(name = "ResolutionJustificationComputation")
	public MinimalSubsetEnumerator.Factory<C, A> getJustificationComputation() {
		return computation_;
	}

	private class JustificationCounter
			implements MinimalSubsetEnumerator.Listener<A> {

		private volatile int count_ = 0;
		private final List<Integer> justSizes_ = new ArrayList<>();
		private final List<Long> justTimes_ = new ArrayList<>();

		@Override
		public void newMinimalSubset(final Set<A> justification) {
			justTimes_.add(System.nanoTime());
			count_++;
			justSizes_.add(justification.size());
		}

		public int getCount() {
			return count_;
		}

		public Collection<Set<A>> getJustifications() {
			return null;
		}

		public List<Integer> getSizes() {
			return justSizes_;
		}

		public List<Long> getTimes() {
			return justTimes_;
		}

		public void reset() {
			count_ = 0;
			justSizes_.clear();
			justTimes_.clear();
		}

	}

	private class JustificationCollector extends JustificationCounter {

		private final Collection<Set<A>> justifications_ = new ArrayList<>();

		@Override
		public void newMinimalSubset(final Set<A> justification) {
			super.newMinimalSubset(justification);
			justifications_.add(justification);
		}

		public Collection<Set<A>> getJustifications() {
			return justifications_;
		}

		@Override
		public void reset() {
			super.reset();
			justifications_.clear();
		}

	}

	@Stat
	public int minJustSize() {
		return minJustSizeize_;
	}

	@Stat
	public int maxJustSize() {
		return maxJustSize_;
	}

	@Stat
	public double obtainingInferencesTime() {
		return obtainingInferencesTimeMillis_;
	}

	@Stat
	public double firstQuartileJustSize() {
		return firstQuartileJustSize_;
	}

	@Stat
	public double medianJustSize() {
		return medianJustSize_;
	}

	@Stat
	public double meanJustSize() {
		return meanJustSize_;
	}

	@Stat
	public double thirdQuartileJustSize() {
		return thirdQuartileJustSize_;
	}

	private void resetStats() {
		minJustSizeize_ = maxJustSize_ = 0;
		firstQuartileJustSize_ = medianJustSize_ = meanJustSize_ = thirdQuartileJustSize_ = 0.0;
		just1Time = just2Time = justHalfTime = "";
	}

	private void computeJustStats(final List<Integer> sizes) {

		if (sizes == null || sizes.isEmpty()) {
			resetStats();
			return;
		}
		// else

		Collections.sort(sizes);

		minJustSizeize_ = sizes.get(0);
		maxJustSize_ = sizes.get(sizes.size() - 1);

		firstQuartileJustSize_ = firstQuartile(sizes);
		medianJustSize_ = median(sizes);
		meanJustSize_ = mean(sizes);
		thirdQuartileJustSize_ = thirdQuartile(sizes);

	}

	private double median(final List<Integer> numbers) {
		final int half = numbers.size() / 2;
		if (numbers.size() % 2 == 0) {
			return (numbers.get(half - 1) + numbers.get(half)) / 2.0;
		} else {
			return numbers.get(half);
		}
	}

	private double firstQuartile(final List<Integer> numbers) {
		final int half = numbers.size() / 2;
		if (numbers.size() % 2 == 0) {
			return median(numbers.subList(0, half));
		} else {
			return median(numbers.subList(0, half + 1));
		}
	}

	private double thirdQuartile(final List<Integer> numbers) {
		final int half = numbers.size() / 2;
		return median(numbers.subList(half, numbers.size()));
	}

	private double mean(final List<Integer> numbers) {
		int sum = 0;
		for (final Integer number : numbers) {
			sum += number;
		}
		return sum / (double) numbers.size();
	}

}
