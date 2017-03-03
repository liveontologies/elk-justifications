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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.semanticweb.elk.justifications.experiments.ExperimentException;
import org.semanticweb.elk.justifications.experiments.JustificationExperiment;
import org.semanticweb.elk.statistics.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunJustificationExperiments {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(RunJustificationExperiments.class);

	public static final long TIMEOUT_DELAY_MILLIS = 10l;
	public static final double NANOS_IN_MILLIS = 1000000.0d;
	public static final double MILLIS_IN_SECOND = 1000.0d;

	public static void main(final String[] args) {

		final int requiredArgCount = 6;

		if (args.length < requiredArgCount) {
			LOGGER_.error("Insufficient arguments!");
			System.exit(1);
		}

		final File recordFile = new File(args[0]);
		if (recordFile.exists()) {
			Utils.recursiveDelete(recordFile);
		}
		final long timeOutMillis = Long.parseLong(args[1]);
		final long globalTimeOutMillis = Long.parseLong(args[2]);
		final int warmupCount = Integer.parseInt(args[3]);
		final File queryFile = new File(args[4]);
		final String experimentClassName = args[5];

		BufferedReader queryReader = null;
		PrintWriter recordWriter = null;

		try {

			final JustificationExperiment experiment = newExperiment(
					experimentClassName,
					Arrays.copyOfRange(args, requiredArgCount, args.length));

			if (warmupCount > 0) {
				LOGGER_.info("Warm Up");
				run(experiment, queryFile, timeOutMillis, globalTimeOutMillis,
						warmupCount, null);
			}

			LOGGER_.info("Actual Experiment Run");
			recordWriter = new PrintWriter(recordFile);
			run(experiment, queryFile, timeOutMillis, globalTimeOutMillis, 0,
					recordWriter);

		} catch (final ExperimentException e) {
			LOGGER_.error(e.getMessage(), e);
			System.exit(2);
		} catch (final FileNotFoundException e) {
			LOGGER_.error("File not found!", e);
			System.exit(2);
		} catch (final IOException e) {
			LOGGER_.error("Cannot read query!", e);
			System.exit(2);
		} finally {
			Utils.closeQuietly(queryReader);
			Utils.closeQuietly(recordWriter);
		}

	}

	private static JustificationExperiment newExperiment(
			final String experimentClassName, final String[] args)
			throws ExperimentException {

		try {
			final Class<?> experimentClass = RunJustificationExperiments.class
					.getClassLoader().loadClass(experimentClassName);
			final Constructor<?> constructor = experimentClass
					.getConstructor(String[].class);
			final Object object = constructor.newInstance((Object) args);
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

	private static void run(final JustificationExperiment experiment,
			final File queryFile, final long timeOutMillis,
			final long globalTimeOutMillis, final int maxIterations,
			final PrintWriter recordWriter) throws IOException {

		BufferedReader queryReader = null;

		try {

			queryReader = new BufferedReader(new FileReader(queryFile));

			final Record record = new Record(recordWriter);

			final long globalStartTimeMillis = System.currentTimeMillis();
			final long globalStopTimeMillis = globalTimeOutMillis > 0
					? globalStartTimeMillis + globalTimeOutMillis
					: Long.MAX_VALUE;

			boolean didSomeExperimentRun = false;
			for (int nIter = 0; nIter < maxIterations
					|| maxIterations <= 0; nIter++) {
				final String query = queryReader.readLine();
				if (query == null) {
					break;
				}

				if (maxIterations > 0) {
					LOGGER_.info("Run number {} of {}", nIter + 1,
							maxIterations);
				} else {
					LOGGER_.info("Run number {}", nIter + 1);
				}

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

				experiment.init();
				System.gc();

				final long localStartTimeMillis = System.currentTimeMillis();
				final long localStopTimeMillis = timeOutMillis > 0
						? localStartTimeMillis + timeOutMillis : Long.MAX_VALUE;

				final long stopTimeMillis = Math.min(globalStopTimeMillis,
						localStopTimeMillis);

				final Thread worker = new Thread() {
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
				final long startTimeNanos = System.nanoTime();
				worker.start();
				// wait for timeout
				try {
					worker.join(timeOutMillis + TIMEOUT_DELAY_MILLIS);
				} catch (final InterruptedException e) {
					LOGGER_.warn("Waiting for the worker thread interruptet!",
							e);
				}
				final long runTimeNanos = System.nanoTime() - startTimeNanos;
				final int nJust = experiment.getJustificationCount();
				didSomeExperimentRun = true;
				killIfAlive(worker);

				final boolean didTimeOut = (startTimeNanos + runTimeNanos)
						/ NANOS_IN_MILLIS > stopTimeMillis;
				record.put("didTimeOut", didTimeOut);
				record.put("time", runTimeNanos / NANOS_IN_MILLIS);
				record.put("nJust", nJust);
				final Map<String, Object> stats = Stats
						.copyIntoMap(experiment, new TreeMap<String, Object>());
				for (final Map.Entry<String, Object> entry : stats.entrySet()) {
					record.put(shortenStatName(entry.getKey()),
							entry.getValue());
				}
				record.flush();

			}

		} finally {
			Utils.closeQuietly(queryReader);
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
	private static class TimeOutMonitor implements Monitor {

		private final long stopTimeMillis_;

		private volatile boolean cancelled = false;

		public TimeOutMonitor(final long stopTimeMillis) {
			this.stopTimeMillis_ = stopTimeMillis;
		}

		@Override
		public boolean isCancelled() {
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
