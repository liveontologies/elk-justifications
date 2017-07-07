package org.liveontologies.pinpointing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.liveontologies.pinpointing.experiments.CsvQueryDecoder;
import org.liveontologies.proofs.TracingInferenceJustifier;
import org.liveontologies.proofs.adapters.Proofs;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.statistics.Stats;
import org.semanticweb.elk.exceptions.ElkException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class CollectStatisticsUsingElk {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(CollectStatisticsUsingElk.class);

	public static void main(final String[] args) {
		
		if (args.length < 3) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final String ontologyFileName = args[0];
		final String conclusionsFileName = args[1];
		final File recordFile = new File(args[2]);
		if (recordFile.exists()) {
			Utils.recursiveDelete(recordFile);
		}
		final File conclusionStatsFile;
		if (args.length > 3) {
			conclusionStatsFile = new File(args[3]);
		} else {
			conclusionStatsFile = null;
		}
		
		final Map<Conclusion, Integer> conclusionCounters;
		if (conclusionStatsFile == null) {
			conclusionCounters = null;
		} else {
			conclusionCounters = new HashMap<>();
		}
		
		final ElkObjectBaseFactory factory = new ElkObjectBaseFactory();
		
		InputStream ontologyIS = null;
		BufferedReader conclusionReader = null;
		PrintWriter stats = null;
		PrintWriter conclusionStats = null;
		
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
			
			stats = new PrintWriter(recordFile);
			final Recorder recorder = new Recorder(stats);
			
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			
			String line;
			while ((line = conclusionReader.readLine()) != null) {
				
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
				
				LOG.info("Collecting statistics for {} ...", conclusion);
				
				final Recorder.RecordBuilder record = recorder.newRecord();
				record.put("query", line);
				
				collectStatistics(conclusion, reasoner, record,
						conclusionCounters);
				
				recorder.flush();
				
			}
			
			conclusionStats = new PrintWriter(conclusionStatsFile);
			conclusionStats.println("conclusion,nOccurrencesInDifferentProofs");
			for (final Entry<Conclusion, Integer> e : conclusionCounters.entrySet()) {
				conclusionStats.print("\"");
				conclusionStats.print(e.getKey());
				conclusionStats.print("\",");
				conclusionStats.println(e.getValue());
			}
			
		} catch (final FileNotFoundException e) {
			LOG.error("File Not Found!", e);
			System.exit(2);
		} catch (final ElkInconsistentOntologyException e) {
			LOG.error("The ontology is inconsistent!", e);
			System.exit(2);
		} catch (final ElkException e) {
			LOG.error("Could not classify the ontology!", e);
			System.exit(2);
		} catch (final IOException e) {
			LOG.error("Error while reading the conclusion file!", e);
			System.exit(2);
		} finally {
			Utils.closeQuietly(ontologyIS);
			Utils.closeQuietly(conclusionReader);
			Utils.closeQuietly(stats);
			Utils.closeQuietly(conclusionStats);
		}
		
	}
	
	private static void collectStatistics(final ElkSubClassOfAxiom conclusion,
			final Reasoner reasoner, final Recorder.RecordBuilder record,
			final Map<Conclusion, Integer> conclusionCounters)
					throws ElkException {
		
		final long startNanos = System.nanoTime();
		
		final Conclusion expression = Utils
				.getFirstDerivedConclusionForSubsumption(reasoner, conclusion);
		final Proof<Conclusion> proof =
				reasoner.explainConclusion(expression);
		final TracingInferenceJustifier justifier =
				TracingInferenceJustifier.INSTANCE;
		
		final Set<ElkAxiom> axiomExprs =
				new HashSet<ElkAxiom>();
		final Set<Conclusion> lemmaExprs =
				new HashSet<Conclusion>();
		final Set<Inference<Conclusion>> inferences =
				new HashSet<Inference<Conclusion>>();
		
		Utils.traverseProofs(expression, proof, justifier,
				new Function<Inference<Conclusion>, Void>() {
					@Override
					public Void apply(
							final Inference<Conclusion> inf) {
						inferences.add(inf);
						return null;
					}
				},
				new Function<Conclusion, Void>() {
					@Override
					public Void apply(final Conclusion expr) {
						lemmaExprs.add(expr);
						if (conclusionCounters != null) {
							Integer count = conclusionCounters.get(expr);
							if (count == null) {
								count = 0;
							}
							conclusionCounters.put(expr, count + 1);
						}
						return null;
					}
				},
				new Function<ElkAxiom, Void>() {
					@Override
					public Void apply(final ElkAxiom axiom) {
						axiomExprs.add(axiom);
						return null;
					}
				}
		);
		
		record.put("nAxiomsInAllProofs", axiomExprs.size());
		record.put("nConclusionsInAllProofs", lemmaExprs.size());
		record.put("nInferencesInAllProofs", inferences.size());
		
		final boolean hasCycle =
				Proofs.hasCycle(proof, expression);
		record.put("isCycleInInferenceGraph", hasCycle);
		
		final StronglyConnectedComponents<Conclusion> components =
				StronglyConnectedComponentsComputation.computeComponents(
						proof, expression);
		
		final List<List<Conclusion>> comps = components.getComponents();
		final List<Conclusion> maxComp =
				Collections.max(comps, SIZE_COMPARATOR);
		record.put("sizeOfMaxComponentInInferenceGraph", maxComp.size());
		
		final Collection<List<Conclusion>> nonSingletonComps =
				Collections2.filter(comps, new Predicate<List<Conclusion>>() {
			@Override
			public boolean apply(final List<Conclusion> comp) {
				return comp.size() > 1;
			}
		});
		record.put("nNonSingletonComponentsInInferenceGraph", nonSingletonComps.size());
		
		final long runTimeNanos = System.nanoTime() - startNanos;
		LOG.info("... took {}s", runTimeNanos / 1000000000.0);
		record.put("time", runTimeNanos / 1000000.0);
		
		final Runtime runtime = Runtime.getRuntime();
		final long totalMemory = runtime.totalMemory();
		final long usedMemory = totalMemory - runtime.freeMemory();
		record.put("usedMemory", usedMemory);
		
		final Map<String, Object> stats = Stats.copyIntoMap(reasoner,
				new TreeMap<String, Object>());
		for (final Map.Entry<String, Object> entry : stats.entrySet()) {
			record.put(entry.getKey(), entry.getValue());
		}
		
	}
	
	private static final Comparator<Collection<?>> SIZE_COMPARATOR =
			new Comparator<Collection<?>>() {
		@Override
		public int compare(final Collection<?> o1, final Collection<?> o2) {
			return Integer.compare(o1.size(), o2.size());
		}
	};
	
}
