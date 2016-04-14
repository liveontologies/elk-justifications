package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.semanticweb.elk.justifications.experiments.Experiment;
import org.semanticweb.elk.justifications.experiments.ExperimentException;
import org.semanticweb.elk.justifications.experiments.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JustificationsFromProofs {
	
	private static final Logger LOG = LoggerFactory.getLogger(JustificationsFromProofs.class);
	
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
		final int warmupCount = Integer.parseInt(args[2]);
		final String experimentClassName = args[3];
		
		PrintWriter record = null;
		
		try {
			
			final Class<?> experimentClass = JustificationsFromProofs.class
					.getClassLoader().loadClass(experimentClassName);
			final Constructor<?> constructor =
					experimentClass.getConstructor(String[].class);
			final Object object = constructor.newInstance(
					(Object) Arrays.copyOfRange(args, 4, args.length));
			if (!(object instanceof Experiment)) {
				LOG.error("The passed argument is not a subclass of Experiment!");
				System.exit(2);
			}
			final Experiment experiment = (Experiment) object;
			
			record = new PrintWriter(recordFile);
			record.println("conclusion,didTimeOut,time,nJust");
			
			LOG.info("Warm Up ...");
			experiment.init();
			int count = warmupCount;
			while (count > 0 && experiment.hasNext()) {
				
				LOG.info("... {} ...", count);
				withTimeout(20000, new Runnable() {
					@Override
					public void run() {
						
						try {
							experiment.run();
						} catch (final ExperimentException e) {
							throw new RuntimeException(e);
						} catch (final InterruptedException e) {
							// Do nothing.
						}
						
					}
				});
				
				--count;
				if (!experiment.hasNext()) {
					experiment.init();
				}
				
			}
			LOG.info("... that's enough");
			
			experiment.init();
			while (experiment.hasNext()) {
				
				final AtomicInteger justSize = new AtomicInteger();
				final AtomicLong time = new AtomicLong();
				
				LOG.info("Obtaining justifications ...");
				final boolean didTimeOut = withTimeout(timeOut, new Runnable() {
					@Override
					public void run() {
						LOG.info("start the worker ...");
						final long s = System.currentTimeMillis();
						
						try {
							
							final Record record = experiment.run();
							time.set(record.time);
							justSize.set(record.nJust);
							
						} catch (final ExperimentException e) {
							throw new RuntimeException(e);
						} catch (final InterruptedException e) {
							LOG.info("... interrupted ...");
						}
						
						LOG.info("... end the worker; took {}s",
								(System.currentTimeMillis() - s)/1000.0);
					}
				});
				
				final String conclusion = experiment.getInputName();
				
				record.print("\"");
				record.print(conclusion);
				record.print("\",");
				record.flush();
				record.print(didTimeOut?"TRUE":"FALSE");
				record.print(",");
				record.flush();
				
				if (didTimeOut) {
					LOG.info("... timeout");
					
					record.print(timeOut);
					record.print(",");
					record.print("0");
					record.println();
					
				} else {
					LOG.info("... took {}s", time.get()/1000.0);
					
					final int justificationSize = justSize.get();
					LOG.info("found {} justifications for {}",
							justificationSize, conclusion);
					
					record.print(time.get());
					record.print(",");
					record.print(justificationSize);
					record.println();
					
					experiment.processResult();
					
				}
				
				record.flush();
				
			}
			
		} catch (final FileNotFoundException e) {
			LOG.error("File not found!", e);
			System.exit(2);
		} catch (final ExperimentException e) {
			LOG.error("Could not setup the experiment!", e);
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

	private static boolean withTimeout(final long timeOut, final Runnable runnable) {
		
		final Thread worker = new Thread(runnable);
		
		boolean didTimeOut = false;
		
		worker.start();
		try {
			if (timeOut > 0) {
				worker.join(timeOut);
				if (worker.isAlive()) {
					didTimeOut = true;
					worker.interrupt();
				}
			}
			worker.join();
		} catch (final InterruptedException e) {
			LOG.warn("Waiting for the worker thread interruptet!", e);
		}
		
		return didTimeOut;
	}
	
}
