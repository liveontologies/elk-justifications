package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.liveontologies.puli.JustifiedInference;
import org.liveontologies.puli.justifications.InterruptMonitor;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.experiments.CsvQueryDecoder;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.TracingInferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class CollectJustificationStatisticsUsingElk {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(CollectJustificationStatisticsUsingElk.class);
	
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
		final int justificationSizeLimit = Integer.parseInt(args[3]);
		final String ontologyFileName = args[4];
		final String conclusionsFileName = args[5];
		
		final ElkObjectBaseFactory factory = new ElkObjectBaseFactory();
		
		final long timeOutCheckInterval = Math.min(timeOut/4, 1000);
		
		InputStream ontologyIS = null;
		BufferedReader conclusionReader = null;
		PrintWriter record = null;
		
		try {
			
			ontologyIS = new FileInputStream(ontologyFileName);
			
			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory().createReasoner(
					loader);
			
			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			record = new PrintWriter(recordFile);
			record.println("conclusion,didTimeOut,sumOfProductsOfPremiseJustNum,sumOfProductsOfInferenceJustNum,sumOfInferenceJustNum");
			
			final TimeOutMonitor monitor = new TimeOutMonitor();
			
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			
			final BufferedReader conclReader = conclusionReader;
			final PrintWriter rec = record;
			final Thread worker = new Thread() {
				@Override
				public void run() {
					final long globalStartTime = System.currentTimeMillis();
					
					try {
						
						String line;
						while ((line = conclReader.readLine()) != null) {
							final long currentRunTime =
									System.currentTimeMillis() - globalStartTime;
							if (currentRunTime > globalTimeOut
									&& globalTimeOut != 0) {
								break;
							}
							
							final ElkSubClassOfAxiom conclusion = CsvQueryDecoder.decode(
									line,
									new CsvQueryDecoder.Factory<ElkSubClassOfAxiom>() {

										@Override
										public ElkSubClassOfAxiom createQuery(
												final String subIri, final String supIri) {
											return factory.getSubClassOfAxiom(
													factory.getClass(
															new ElkFullIri(subIri)),
													factory.getClass(
															new ElkFullIri(supIri)));
										}

									});
							
							System.gc();
							
							LOG.info("Collecting statistics for {}", conclusion);
							if (globalTimeOut != 0) {
								LOG.info("{}s left until global timeout",
										(globalTimeOut - currentRunTime)/1000d);
							}
							
							monitor.cancelled = false;
							monitor.startTime.set(System.currentTimeMillis());
							
							final long startTime = System.nanoTime();
							
							final Conclusion expression = Utils
									.getFirstDerivedConclusionForSubsumption(
											reasoner, conclusion);
							final TracingInferenceSet inferenceSet =
									reasoner.explainConclusion(expression);

							final JustificationCollector<Conclusion, ElkAxiom> collector =
									new JustificationCollector<Conclusion, ElkAxiom>(
											BottomUpJustificationComputation
											.<Conclusion, ElkAxiom> getFactory(),
											inferenceSet);
							
							final int sizeLimit = justificationSizeLimit <= 0
									? Integer.MAX_VALUE
									: justificationSizeLimit;
							
							final List<Long> productSum = Arrays.asList(0l);
							final List<Long> minProductSum = Arrays.asList(0l);
							final List<Long> minSum = Arrays.asList(0l);
							Utils.traverseProofs(expression, inferenceSet,
									new Function<JustifiedInference<Conclusion, ElkAxiom>, Void>() {
										@Override
										public Void apply(final JustifiedInference<Conclusion, ElkAxiom> inf) {
											if (monitor.isInterrupted()) {
												return null;
											}
											
											final Collection<? extends Set<ElkAxiom>> conclJs =
													collector.collectJustifications(inf.getConclusion(), sizeLimit);
											
											long product = 1;
											long minProduct = 1;
											long sum = 0;
											for (final Conclusion premise : inf.getPremises()) {
												
												final Collection<? extends Set<ElkAxiom>> js =
														collector.collectJustifications(premise, sizeLimit);
												
												product *= js.size();
												
												long count = 0;
												for (final Set<ElkAxiom> just : js) {
													if (Utils.isMinimal(
															new BloomSet<>(inf.getConclusion(), just, inf.getJustification()),
															conclJs)) {
														count++;
													}
												}
												minProduct *= count;
												sum += count;
												
											}
											productSum.set(0, productSum.get(0) + product);
											minProductSum.set(0, minProductSum.get(0) + minProduct);
											minSum.set(0, minSum.get(0) + sum);
											
											return null;
										}
									},
									Functions.<Conclusion>identity(),
									Functions.<ElkAxiom>identity());
							
							final double time = (System.nanoTime() - startTime) / 1000000.0;
							
							final boolean didTimeOut =
									(time > timeOut && timeOut != 0);
							
							if (didTimeOut) {
								LOG.info("... timeout {}s", time/1000.0);
							} else {
								LOG.info("... took {}s", time/1000.0);
							}
							
							rec.print("\"");
							rec.print(conclusion);
							rec.print("\",");
							rec.flush();
							rec.print(didTimeOut?"TRUE":"FALSE");
							rec.print(",");
							rec.print(productSum.get(0));
							rec.print(",");
							rec.print(minProductSum.get(0));
							rec.print(",");
							rec.println(minSum.get(0));
							rec.flush();
							
						}
						
					} catch (final IOException | ElkException e) {
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
		} catch (final ElkInconsistentOntologyException e) {
			LOG.error("Inconsistent ontology!", e);
			System.exit(2);
		} catch (final ElkException e) {
			LOG.error("Could not classify the ontology!", e);
			System.exit(2);
		} finally {
			if (ontologyIS != null) {
				try {
					ontologyIS.close();
				} catch (final IOException e) {}
			}
			if (conclusionReader != null) {
				try {
					conclusionReader.close();
				} catch (final IOException e) {}
			}
			if (record != null) {
				record.close();
			}
		}
		
	}
	
	private static class TimeOutMonitor implements InterruptMonitor {
		
		public volatile boolean cancelled = false;
		public final AtomicLong startTime = new AtomicLong();

		@Override
		public boolean isInterrupted() {
			return cancelled;
		}
		
	}
	
}
