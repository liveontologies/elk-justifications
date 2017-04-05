package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.liveontologies.puli.justifications.InterruptMonitor;
import org.liveontologies.puli.statistics.Stats;
import org.semanticweb.elk.justifications.experiments.ExperimentException;
import org.semanticweb.elk.justifications.experiments.JustificationExperiment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class RunRepeatingExperiments {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(RunRepeatingExperiments.class);

	public static final String RECORD_OPT = "record";
	public static final String TIMEOUT_OPT = "t";
	public static final String GLOBAL_TIMEOUT_OPT = "g";
	public static final String REPETITION_COUNT_OPT = "r";
	public static final String SEED_OPT = "s";
	public static final String GC_OPT = "gc";
	public static final String QUERIES_OPT = "queries";
	public static final String EXPERIMENT_OPT = "exp";
	public static final String EXPERIMENT_ARGS_OPT = "arg";

	public static class Options {
		@Arg(dest = RECORD_OPT)
		public String recordName;
		@Arg(dest = TIMEOUT_OPT)
		public Long timeOutMillis;
		@Arg(dest = GLOBAL_TIMEOUT_OPT)
		public Long globalTimeOutMillis;
		@Arg(dest = REPETITION_COUNT_OPT)
		public Integer repetitionCount;
		@Arg(dest = SEED_OPT)
		public Long seed;
		@Arg(dest = GC_OPT)
		public boolean runGc;
		@Arg(dest = QUERIES_OPT)
		public File queryFile;
		@Arg(dest = EXPERIMENT_OPT)
		public String experimentClassName;
		@Arg(dest = EXPERIMENT_ARGS_OPT)
		public String[] experimentArgs;
	}

	public static final long TIMEOUT_DELAY_MILLIS = 10l;
	public static final double NANOS_IN_MILLIS = 1000000.0d;
	public static final double MILLIS_IN_SECOND = 1000.0d;

	public static void main(final String[] args) {

		final ArgumentParser parser = ArgumentParsers
				.newArgumentParser(
						RunRepeatingExperiments.class.getSimpleName())
				.description("Run justification experiments.");
		parser.addArgument(RECORD_OPT).help("record file name");
		parser.addArgument("-" + TIMEOUT_OPT).type(Long.class)
				.help("timeout per query in milliseconds");
		parser.addArgument("-" + GLOBAL_TIMEOUT_OPT).type(Long.class)
				.help("global timeout in milliseconds");
		parser.addArgument("-" + REPETITION_COUNT_OPT).type(Integer.class)
				.help("number of repetitions after the first run");
		parser.addArgument("-" + SEED_OPT).type(Long.class).help("random seed");
		parser.addArgument("--" + GC_OPT).action(Arguments.storeTrue())
				.help("run garbage collector before every query");
		parser.addArgument(QUERIES_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("query file");
		parser.addArgument(EXPERIMENT_OPT).help("experiment class name");
		parser.addArgument(EXPERIMENT_ARGS_OPT).nargs("*")
				.help("experiment arguments");

		BufferedReader queryReader = null;
		PrintWriter recordWriter = null;

		try {

			final Options opt = new Options();
			parser.parseArgs(args, opt);

			final String recordName = opt.recordName;
			LOGGER_.info("recordName: {}", recordName);
			final long timeOutMillis = opt.timeOutMillis == null ? 0l
					: opt.timeOutMillis;
			LOGGER_.info("timeOutMillis: {}", timeOutMillis);
			final long globalTimeOutMillis = opt.globalTimeOutMillis == null
					? 0l : opt.globalTimeOutMillis;
			LOGGER_.info("globalTimeOutMillis: {}", globalTimeOutMillis);
			final int repetitionCount = opt.repetitionCount == null ? 0
					: opt.repetitionCount;
			LOGGER_.info("repetitionCount: {}", repetitionCount);
			final Long seed = opt.seed;
			LOGGER_.info("seed: {}", seed);
			final boolean runGc = opt.runGc;
			LOGGER_.info("runGc: {}", runGc);
			final File queryFile = opt.queryFile;
			LOGGER_.info("queryFile: {}", queryFile);
			final String experimentClassName = opt.experimentClassName;
			LOGGER_.info("experimentClassName: {}", experimentClassName);
			final String[] experimentArgs = opt.experimentArgs;
			LOGGER_.info("experimentArgs: {}", Arrays.toString(experimentArgs));

			final JustificationExperiment experiment = newExperiment(
					experimentClassName);

			int runIndex = 0;
			File recordFile = new File(String.format(
					"%s.%0" + Utils.digitCount(repetitionCount + 1) + "d.csv",
					recordName, ++runIndex));
			if (recordFile.exists()) {
				Utils.recursiveDelete(recordFile);
			}
			LOGGER_.info("Run #{}", runIndex);
			recordWriter = new PrintWriter(recordFile);
			experiment.init(experimentArgs);
			final List<String> queries = firstRun(experiment, queryFile,
					timeOutMillis, globalTimeOutMillis, runGc, recordWriter);
			experiment.dispose();
			Utils.closeQuietly(recordWriter);

			final Random random;
			if (seed != null) {
				random = new Random(seed);
			} else {
				random = new Random();
			}

			while (runIndex <= repetitionCount) {
				Collections.shuffle(queries, random);

				recordFile = new File(String.format("%s.%0"
						+ Utils.digitCount(repetitionCount + 1) + "d.csv",
						recordName, ++runIndex));
				if (recordFile.exists()) {
					Utils.recursiveDelete(recordFile);
				}
				LOGGER_.info("Run #{}", runIndex);
				recordWriter = new PrintWriter(recordFile);
				experiment.init(experimentArgs);
				otherRun(experiment, queries, timeOutMillis,
						globalTimeOutMillis, runGc, recordWriter);
				experiment.dispose();
				Utils.closeQuietly(recordWriter);
			}

		} catch (final ExperimentException e) {
			LOGGER_.error(e.getMessage(), e);
			System.exit(2);
		} catch (final FileNotFoundException e) {
			LOGGER_.error("File not found!", e);
			System.exit(2);
		} catch (final IOException e) {
			LOGGER_.error("Cannot read query!", e);
			System.exit(2);
		} catch (final ArgumentParserException e) {
			parser.handleError(e);
			System.exit(2);
		} finally {
			Utils.closeQuietly(queryReader);
			Utils.closeQuietly(recordWriter);
		}

	}

	private static JustificationExperiment newExperiment(
			final String experimentClassName) throws ExperimentException {

		try {
			final Class<?> experimentClass = RunRepeatingExperiments.class
					.getClassLoader().loadClass(experimentClassName);
			final Constructor<?> constructor = experimentClass.getConstructor();
			final Object object = constructor.newInstance();
			if (!(object instanceof JustificationExperiment)) {
				throw new ExperimentException(
						"The specified experiment class is not a subclass of "
								+ JustificationExperiment.class.getName());
			}
			return (JustificationExperiment) object;
		} catch (final ClassNotFoundException e) {
			throw new ExperimentException(
					"The specified experiment class could not be found!", e);
		} catch (final NoSuchMethodException e) {
			throw new ExperimentException(
					"The specified experiment class does not define required constructor!",
					e);
		} catch (final SecurityException e) {
			throw new ExperimentException(
					"The specified experiment could not be instantiated!", e);
		} catch (final InstantiationException e) {
			throw new ExperimentException(
					"The specified experiment could not be instantiated!", e);
		} catch (final IllegalAccessException e) {
			throw new ExperimentException(
					"The specified experiment could not be instantiated!", e);
		} catch (final InvocationTargetException e) {
			throw new ExperimentException(
					"The specified experiment could not be instantiated!", e);
		}

	}

	private static List<String> firstRun(
			final JustificationExperiment experiment, final File queryFile,
			final long timeOutMillis, final long globalTimeOutMillis,
			final boolean runGc, final PrintWriter recordWriter)
			throws IOException, ExperimentException {

		final List<String> queries = new ArrayList<>();
		BufferedReader queryReader = null;

		try {

			queryReader = new BufferedReader(new FileReader(queryFile));

			final Record record = new Record(recordWriter);

			final long globalStartTimeMillis = System.currentTimeMillis();
			final long globalStopTimeMillis = globalTimeOutMillis > 0
					? globalStartTimeMillis + globalTimeOutMillis
					: Long.MAX_VALUE;

			boolean didSomeExperimentRun = false;
			for (int nIter = 0; true; nIter++) {
				final String query = queryReader.readLine();
				if (query == null) {
					break;
				}

				LOGGER_.info("Run number {}", nIter + 1);

				if (globalTimeOutMillis > 0) {
					final long globalTimeLeftMillis = globalStopTimeMillis
							- System.currentTimeMillis();
					LOGGER_.info("{}s left until global timeout",
							globalTimeLeftMillis / MILLIS_IN_SECOND);
					if (globalTimeLeftMillis <= 0l) {
						break;
					}
				}

				record.newRecord();
				record.put("query", query);
				if (didSomeExperimentRun) {
					record.flush();
				}

				experiment.before();
				if (runGc) {
					System.gc();
				}

				final long localStartTimeMillis = System.currentTimeMillis();
				final long localStopTimeMillis = timeOutMillis > 0
						? localStartTimeMillis + timeOutMillis : Long.MAX_VALUE;

				final long stopTimeMillis = localStopTimeMillis;

				final Runnable runnable = new Runnable() {
					@Override
					public void run() {
						try {
							experiment.run(query,
									new TimeOutMonitor(stopTimeMillis));
						} catch (final ExperimentException e) {
							throw new RuntimeException(e);
						}
					}
				};
				final Thread worker = new Thread(runnable);
				final long startTimeNanos = System.nanoTime();
				worker.start();
				// wait for timeout
				try {
					worker.join(timeOutMillis > 0
							? timeOutMillis + TIMEOUT_DELAY_MILLIS : 0);
				} catch (final InterruptedException e) {
					LOGGER_.warn("Waiting for the worker thread interruptet!",
							e);
				}
				final long runTimeNanos = System.nanoTime() - startTimeNanos;
				final int nJust = experiment.getJustificationCount();
				didSomeExperimentRun = true;
				killIfAlive(worker);

				final Runtime runtime = Runtime.getRuntime();
				final long totalMemory = runtime.totalMemory();
				final long usedMemory = totalMemory - runtime.freeMemory();
				final boolean didTimeOut = localStartTimeMillis
						+ (runTimeNanos / NANOS_IN_MILLIS) > stopTimeMillis;
				record.put("didTimeOut", didTimeOut);
				record.put("time", runTimeNanos / NANOS_IN_MILLIS);
				record.put("nJust", nJust);
				record.put("usedMemory", usedMemory);

				experiment.after();

				final Map<String, Object> stats = Stats.copyIntoMap(experiment,
						new TreeMap<String, Object>());
				for (final Map.Entry<String, Object> entry : stats.entrySet()) {
					record.put(shortenStatName(entry.getKey()),
							entry.getValue());
				}
				record.flush();

				queries.add(query);

			}

			return queries;

		} finally {
			Utils.closeQuietly(queryReader);
		}

	}

	private static void otherRun(final JustificationExperiment experiment,
			final List<String> queries, final long timeOutMillis,
			final long globalTimeOutMillis, final boolean runGc,
			final PrintWriter recordWriter)
			throws IOException, ExperimentException {

		final Record record = new Record(recordWriter);

		final long globalStartTimeMillis = System.currentTimeMillis();
		final long globalStopTimeMillis = globalTimeOutMillis > 0
				? globalStartTimeMillis + globalTimeOutMillis : Long.MAX_VALUE;

		boolean didSomeExperimentRun = false;
		int nIter = 0;
		for (final String query : queries) {

			LOGGER_.info("Run number {}", ++nIter);

			if (globalTimeOutMillis > 0) {
				final long globalTimeLeftMillis = globalStopTimeMillis
						- System.currentTimeMillis();
				LOGGER_.info("{}s left until global timeout",
						globalTimeLeftMillis / MILLIS_IN_SECOND);
				if (globalTimeLeftMillis <= 0l) {
					break;
				}
			}

			record.newRecord();
			record.put("query", query);
			if (didSomeExperimentRun) {
				record.flush();
			}

			experiment.before();
			if (runGc) {
				System.gc();
			}

			final long localStartTimeMillis = System.currentTimeMillis();
			final long localStopTimeMillis = timeOutMillis > 0
					? localStartTimeMillis + timeOutMillis : Long.MAX_VALUE;

			final long stopTimeMillis = localStopTimeMillis;

			final Runnable runnable = new Runnable() {
				@Override
				public void run() {
					try {
						experiment.run(query,
								new TimeOutMonitor(stopTimeMillis));
					} catch (final ExperimentException e) {
						throw new RuntimeException(e);
					}
				}
			};
			final Thread worker = new Thread(runnable);
			final long startTimeNanos = System.nanoTime();
			worker.start();
			// wait for timeout
			try {
				worker.join(timeOutMillis > 0
						? timeOutMillis + TIMEOUT_DELAY_MILLIS : 0);
			} catch (final InterruptedException e) {
				LOGGER_.warn("Waiting for the worker thread interruptet!", e);
			}
			final long runTimeNanos = System.nanoTime() - startTimeNanos;
			final int nJust = experiment.getJustificationCount();
			didSomeExperimentRun = true;
			killIfAlive(worker);

			final Runtime runtime = Runtime.getRuntime();
			final long totalMemory = runtime.totalMemory();
			final long usedMemory = totalMemory - runtime.freeMemory();
			final boolean didTimeOut = localStartTimeMillis
					+ (runTimeNanos / NANOS_IN_MILLIS) > stopTimeMillis;
			record.put("didTimeOut", didTimeOut);
			record.put("time", runTimeNanos / NANOS_IN_MILLIS);
			record.put("nJust", nJust);
			record.put("usedMemory", usedMemory);

			experiment.after();

			final Map<String, Object> stats = Stats.copyIntoMap(experiment,
					new TreeMap<String, Object>());
			for (final Map.Entry<String, Object> entry : stats.entrySet()) {
				record.put(shortenStatName(entry.getKey()), entry.getValue());
			}
			record.flush();

		}

	}

	/**
	 * If the specified thread is alive, calls {@link Thread#stop()} on it.
	 * <strong>This breaks any synchronization with the thread.</strong>
	 * 
	 * @param thread
	 */
	@SuppressWarnings("deprecation")
	private static void killIfAlive(final Thread thread) {
		if (thread.isAlive()) {
			LOGGER_.info("killing the thread {}", thread.getName());
			thread.stop();
		}
	}

	private static String shortenStatName(final String fullName) {
		final int lastIndexOfDot = fullName.lastIndexOf('.');
		if (lastIndexOfDot < 0) {
			return fullName;
		}
		// else
		final int secondLastIndexOfDot = fullName.substring(0, lastIndexOfDot)
				.lastIndexOf('.');
		if (secondLastIndexOfDot < 0) {
			return fullName;
		}
		// else
		return fullName.substring(secondLastIndexOfDot + 1);
	}

	/**
	 * Interrupts when the global or local timeout expires. The global timeout
	 * is counted from the passed global start time and the local from the
	 * creation of this object.
	 * 
	 * @author Peter Skocovsky
	 */
	private static class TimeOutMonitor implements InterruptMonitor {

		private final long stopTimeMillis_;

		private volatile boolean cancelled = false;

		public TimeOutMonitor(final long stopTimeMillis) {
			this.stopTimeMillis_ = stopTimeMillis;
		}

		@Override
		public boolean isInterrupted() {
			if (stopTimeMillis_ < System.currentTimeMillis()) {
				cancelled = true;
			}
			return cancelled;
		}

	}

	private static class Record {

		private final PrintWriter output_;

		private final Set<String> names_ = new LinkedHashSet<>();
		private final List<List<Object>> records_ = new ArrayList<>();

		private final Map<String, Object> currentRecord_ = new HashMap<>();

		private int recordIndex_ = 0;
		private int valueIndex_ = 0;

		public Record(final PrintWriter output) {
			this.output_ = output;
		}

		public Object put(final String name, final Object value) {

			LOGGER_.info("{}: {}", name, value);

			names_.add(name);
			return currentRecord_.put(name, value);
		}

		public void newRecord() {
			if (currentRecord_.isEmpty()) {
				return;
			}
			// else
			final List<Object> record = new ArrayList<>(currentRecord_.size());
			for (final String name : names_) {
				final Object value = currentRecord_.get(name);
				record.add(value);
			}
			records_.add(record);
			currentRecord_.clear();
		}

		public void flush() {
			if (output_ == null) {
				return;
			}
			// else

			if (recordIndex_ == 0 && valueIndex_ == 0) {
				// Write header
				final Iterator<String> iter = names_.iterator();
				if (iter.hasNext()) {
					output_.print(iter.next());
					while (iter.hasNext()) {
						output_.print(",");
						output_.print(iter.next());
					}
				}
				output_.println();
			}

			for (; recordIndex_ < records_.size(); recordIndex_++) {
				final List<Object> record = records_.get(recordIndex_);
				for (; valueIndex_ < record.size(); valueIndex_++) {
					if (valueIndex_ != 0) {
						output_.print(",");
					}
					output_.print(valueToString(record.get(valueIndex_)));
				}
				valueIndex_ = 0;
				output_.println();
			}

			final List<Object> record = new ArrayList<>(currentRecord_.size());
			for (final String name : names_) {
				final Object value = currentRecord_.get(name);
				record.add(value);
			}
			final ListIterator<Object> iter = record
					.listIterator(record.size());
			while (iter.hasPrevious()) {
				final Object value = iter.previous();
				if (value == null) {
					iter.remove();
				} else {
					break;
				}
			}
			for (; valueIndex_ < record.size(); valueIndex_++) {
				if (valueIndex_ != 0) {
					output_.print(",");
				}
				output_.print(valueToString(record.get(valueIndex_)));
			}

			output_.flush();
		}

		private String valueToString(final Object value) {
			if (value == null) {
				return "" + value;
			}
			// else
			if (value instanceof String) {
				final String string = (String) value;
				return "\"" + string.replace("\"", "") + "\"";
			}
			// else
			if (value instanceof Boolean) {
				return (Boolean) value ? "TRUE" : "FALSE";
			}
			// else
			return "" + value;
		}

	}

}
