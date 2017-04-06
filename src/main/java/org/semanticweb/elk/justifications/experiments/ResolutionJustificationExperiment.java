package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.justifications.MinimalSubsetEnumerator;
import org.liveontologies.puli.justifications.ResolutionJustificationComputation;
import org.liveontologies.puli.statistics.NestedStats;
import org.liveontologies.puli.statistics.Stats;
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

	private JustificationCounter justificationListener_;

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
				this.indexWriter_ = new PrintWriter(
						new File(outputDir_, INDEX_FILE_NAME));
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
		} catch (final IllegalArgumentException e) {
			throw new ExperimentException(e);
		} catch (final InvocationTargetException e) {
			throw new ExperimentException(e);
		} catch (final InstantiationException e) {
			throw new ExperimentException(e);
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final ArgumentParserException e) {
			parser.handleError(e);
			System.exit(2);
		}

	}

	protected abstract void addArguments(ArgumentParser parser);

	protected abstract void init(Namespace options) throws ExperimentException;

	@Override
	public void before() {
		justificationListener_.reset();
		if (computation_ != null) {
			Stats.resetStats(computation_);
		}
	}

	@Override
	public void run(final String query, final InterruptMonitor monitor)
			throws ExperimentException {
		lastQuery_ = query;

		final C goal = decodeQuery(query);
		final InferenceSet<C> inferenceSet = newInferenceSet(goal);
		final InferenceJustifier<C, ? extends Set<? extends A>> justifier = newJustifier();
		computation_ = ResolutionJustificationComputation.<C, A> getFactory()
				.create(inferenceSet, justifier, monitor, selectionFactory_);
		computation_.newEnumerator(goal).enumerate(justificationListener_);

	}

	protected abstract C decodeQuery(String query) throws ExperimentException;

	protected abstract InferenceSet<C> newInferenceSet(C query)
			throws ExperimentException;

	protected abstract InferenceJustifier<C, ? extends Set<? extends A>> newJustifier()
			throws ExperimentException;

	@Override
	public void after() throws ExperimentException {

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

	@NestedStats
	public MinimalSubsetEnumerator.Factory<C, A> getJustificationComputation() {
		return computation_;
	}

	private class JustificationCounter
			implements MinimalSubsetEnumerator.Listener<A> {

		private volatile int count_ = 0;

		@Override
		public void newMinimalSubset(final Set<A> justification) {
			count_++;
		}

		public int getCount() {
			return count_;
		}

		public Collection<Set<A>> getJustifications() {
			return null;
		}

		public void reset() {
			count_ = 0;
		}

	}

	private class JustificationCollector extends JustificationCounter {

		// TODO: do I need some synchronization ?!?!
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

}
