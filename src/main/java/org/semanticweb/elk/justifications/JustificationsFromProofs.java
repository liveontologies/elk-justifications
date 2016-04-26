package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.semanticweb.elk.justifications.experiments.Experiment;
import org.semanticweb.elk.justifications.experiments.ExperimentException;
import org.semanticweb.elk.justifications.experiments.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JustificationsFromProofs {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(JustificationsFromProofs.class);
	
	public static final long WARMUP_TIMEOUT = 20000l;
	
	public static void main(final String[] args) {
		
		if (args.length < 3) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final File recordFile = new File(args[0]);
		if (recordFile.exists()) {
			Utils.recursiveDelete(recordFile);
		}
		final long timeOut = Long.parseLong(args[1]);
		final long globalTimeOut = Long.parseLong(args[2]);
		final int warmupCount = Integer.parseInt(args[3]);
		final String experimentClassName = args[4];
		
		final long timeOutCheckInterval = Math.min(timeOut/4, 1000);
		
		PrintWriter record = null;
		
		try {
			
			final Class<?> experimentClass = JustificationsFromProofs.class
					.getClassLoader().loadClass(experimentClassName);
			final Constructor<?> constructor =
					experimentClass.getConstructor(String[].class);
			final Object object = constructor.newInstance(
					(Object) Arrays.copyOfRange(args, 5, args.length));
			if (!(object instanceof Experiment)) {
				LOG.error("The passed argument is not a subclass of Experiment!");
				System.exit(2);
			}
			final Experiment experiment = (Experiment) object;
			
			record = new PrintWriter(recordFile);
			record.print("conclusion,didTimeOut,time,nJust");
			for (final String statName : experiment.getStatNames()) {
				record.print(",");
				record.print(statName);
			}
			record.println();
			
			final TimeOutMonitor monitor = new TimeOutMonitor();
			
			LOG.info("Warm Up ...");
			final Thread warmUpWorker = new Thread() {
				@Override
				public void run() {
					
					try {
						experiment.init();
						int count = warmupCount;
						while (count > 0 && experiment.hasNext()) {
							LOG.info("... {} ...", count);
							
							monitor.cancelled = false;
							monitor.startTime.set(System.currentTimeMillis());
							experiment.run(monitor);
							
							--count;
							if (!experiment.hasNext()) {
								experiment.init();
							}
						}
					} catch (final ExperimentException e) {
						throw new RuntimeException(e);
					}
					
				}
			};
			
			warmUpWorker.start();
			while (warmUpWorker.isAlive()) {
				try {
					Thread.sleep(1000);
				} catch (final InterruptedException e) {
					LOG.warn("Waiting for the worker thread interruptet!", e);
				}
				final long runTime =
						System.currentTimeMillis() - monitor.startTime.get();
				if (runTime > WARMUP_TIMEOUT) {
					monitor.cancelled = true;
				}
			}
			
			LOG.info("... that's enough");
			
			final PrintWriter rec = record;
			final Thread worker = new Thread() {
				@Override
				public void run() {
					final long globalStartTime = System.currentTimeMillis();
					
					try {
						experiment.init();
						while (experiment.hasNext()) {
							final long currentRunTime =
									System.currentTimeMillis() - globalStartTime;
							if (currentRunTime > globalTimeOut
									&& globalTimeOut != 0) {
								break;
							}
							
							experiment.resetStatistics();
							
							if (globalTimeOut == 0) {
								LOG.info("Obtaining justifications ...");
							} else {
								LOG.info("Obtaining justifications ...");
								LOG.info("{}s left until global timeout",
										(globalTimeOut - currentRunTime)/1000d);
							}
							
							monitor.cancelled = false;
							monitor.startTime.set(System.currentTimeMillis());
							Record record = experiment.run(monitor);
							
							final boolean didTimeOut =
									(record.time > timeOut && timeOut != 0);
							
							final String conclusion = experiment.getInputName();
							
							rec.print("\"");
							rec.print(conclusion);
							rec.print("\",");
							rec.flush();
							rec.print(didTimeOut?"TRUE":"FALSE");
							rec.print(",");
							rec.flush();
							
							if (didTimeOut) {
								LOG.info("... timeout {}s", record.time/1000.0);
								
								final int justificationSize = record.nJust;
								LOG.info("found {} justifications for {}",
										justificationSize, conclusion);
								
								rec.print(record.time);
								rec.print(",");
								rec.print(justificationSize);
								
							} else {
								LOG.info("... took {}s", record.time/1000.0);
								
								final int justificationSize = record.nJust;
								LOG.info("found {} justifications for {}",
										justificationSize, conclusion);
								
								rec.print(record.time);
								rec.print(",");
								rec.print(justificationSize);
								
								experiment.processResult();
								experiment.logStatistics();
								
							}
							
							final Map<String, Object> stats =
									experiment.getStatistics();
							if (stats != null) for (final String statName
									: experiment.getStatNames()) {
								rec.print(",");
								rec.print(stats.get(statName));
							}
							rec.println();
							rec.flush();
							
						}
					} catch (final ExperimentException e) {
						throw new RuntimeException(e);
					}
					
				}
			};
			
			worker.start();
			while (worker.isAlive()) {
				try {
					Thread.sleep(timeOutCheckInterval);
				} catch (final InterruptedException e) {
					LOG.warn("Waiting for the worker thread interruptet!", e);
				}
				final long runTime =
						System.currentTimeMillis() - monitor.startTime.get();
				if (runTime > timeOut && timeOut != 0) {
					monitor.cancelled = true;
				}
			}
			
		} catch (final FileNotFoundException e) {
			LOG.error("File not found!", e);
			System.exit(2);
		} catch (final ClassNotFoundException e) {
			LOG.error("Could not setup the experiment!", e);
			System.exit(2);
		} catch (final NoSuchMethodException e) {
			LOG.error("Could not setup the experiment!", e);
			System.exit(2);
		} catch (final SecurityException e) {
			LOG.error("Could not setup the experiment!", e);
			System.exit(2);
		} catch (final InstantiationException e) {
			LOG.error("Could not setup the experiment!", e);
			System.exit(2);
		} catch (final IllegalAccessException e) {
			LOG.error("Could not setup the experiment!", e);
			System.exit(2);
		} catch (final IllegalArgumentException e) {
			LOG.error("Could not setup the experiment!", e);
			System.exit(2);
		} catch (final InvocationTargetException e) {
			LOG.error("Could not setup the experiment!", e);
			System.exit(2);
		} finally {
			if (record != null) {
				record.close();
			}
		}
		
	}

	private static class TimeOutMonitor implements Monitor {
		
		public volatile boolean cancelled = false;
		public final AtomicLong startTime = new AtomicLong();

		@Override
		public boolean isCancelled() {
			return cancelled;
		}
		
	}
	
}
