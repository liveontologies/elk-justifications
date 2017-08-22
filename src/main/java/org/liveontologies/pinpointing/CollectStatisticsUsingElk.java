package org.liveontologies.pinpointing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
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
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkObject;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.indexing.model.IndexedContextRoot;
import org.semanticweb.elk.reasoner.saturation.conclusions.model.ClassConclusion;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.DummyConclusionVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class CollectStatisticsUsingElk {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(CollectStatisticsUsingElk.class);

	public static final String ONTOLOGY_OPT = "ontology";
	public static final String QUERIES_OPT = "queries";
	public static final String RECORD_OPT = "record";
	public static final String CONCLUSION_STATS_OPT = "cstats";
	public static final String QUERY_AGES_OPT = "qages";
	public static final String CUCLE_OPT = "cycle";
	public static final String COMPONENT_OPT = "component";

	public static class Options {
		@Arg(dest = ONTOLOGY_OPT)
		public File ontologyFile;
		@Arg(dest = QUERIES_OPT)
		public File queryFile;
		@Arg(dest = RECORD_OPT)
		public File recordFile;
		@Arg(dest = CONCLUSION_STATS_OPT)
		public File conclusionStatsFile;
		@Arg(dest = QUERY_AGES_OPT)
		public File queryAgesFile;
		@Arg(dest = CUCLE_OPT)
		public boolean detectCycle;
		@Arg(dest = COMPONENT_OPT)
		public boolean countComponents;
	}

	public static void main(final String[] args) {

		final ArgumentParser parser = ArgumentParsers
				.newArgumentParser(
						CollectStatisticsUsingElk.class.getSimpleName())
				.description("Collect statistics using ELK.");
		parser.addArgument(ONTOLOGY_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("ontology file");
		parser.addArgument(QUERIES_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("query file");
		parser.addArgument(RECORD_OPT).type(File.class).help("record file");
		parser.addArgument("--" + CONCLUSION_STATS_OPT).type(File.class)
				.help("collect conclusion statistics");
		parser.addArgument("--" + QUERY_AGES_OPT).type(File.class)
				.help("collect query ages");
		parser.addArgument("--" + CUCLE_OPT).action(Arguments.storeTrue())
				.help("check whether inferences contain a cycle");
		parser.addArgument("--" + COMPONENT_OPT).action(Arguments.storeTrue())
				.help("count strongly connected components in inferences");

		InputStream ontologyIS = null;
		BufferedReader conclusionReader = null;
		PrintWriter stats = null;
		PrintWriter conclusionStatsWriter = null;
		PrintWriter queryAgeWriter = null;

		try {

			final Options opt = new Options();
			parser.parseArgs(args, opt);

			LOGGER_.info("ontologyFile: {}", opt.ontologyFile);
			LOGGER_.info("queryFile: {}", opt.queryFile);
			if (opt.recordFile.exists()) {
				Utils.recursiveDelete(opt.recordFile);
			}
			LOGGER_.info("recordFile: {}", opt.recordFile);
			final Map<Conclusion, ConclusionStat> conclusionStats;
			if (opt.conclusionStatsFile == null) {
				conclusionStats = null;
			} else {
				if (opt.conclusionStatsFile.exists()) {
					Utils.recursiveDelete(opt.conclusionStatsFile);
				}
				conclusionStats = new HashMap<>();
			}
			LOGGER_.info("conclusionStatsFile: {}", opt.conclusionStatsFile);
			if (opt.queryAgesFile != null) {
				if (opt.queryAgesFile.exists()) {
					Utils.recursiveDelete(opt.queryAgesFile);
				}
			}
			LOGGER_.info("queryAgesFile: {}", opt.queryAgesFile);
			LOGGER_.info("detectCycle: {}", opt.detectCycle);
			LOGGER_.info("countComponents: {}", opt.countComponents);

			ontologyIS = new FileInputStream(opt.ontologyFile);

			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory()
					.createReasoner(loader);
			final ElkObject.Factory factory = reasoner.getElkFactory();

			LOGGER_.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOGGER_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

			stats = new PrintWriter(opt.recordFile);
			final Recorder recorder = new Recorder(stats);
			if (opt.queryAgesFile != null) {
				queryAgeWriter = new PrintWriter(opt.queryAgesFile);
				queryAgeWriter.println("queryAge");
			}

			conclusionReader = new BufferedReader(
					new FileReader(opt.queryFile));

			final Utils.Counter conclusionTicks = new Utils.Counter(
					Integer.MIN_VALUE);

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

				LOGGER_.info("Collecting statistics for {} ...", conclusion);

				final Recorder.RecordBuilder record = recorder.newRecord();
				record.put("query", line);

				collectStatistics(conclusion, reasoner, record, opt.detectCycle,
						opt.countComponents, conclusionStats, conclusionTicks,
						queryAgeWriter);

				recorder.flush();

			}

			if (opt.conclusionStatsFile != null) {
				conclusionStatsWriter = new PrintWriter(
						opt.conclusionStatsFile);
				conclusionStatsWriter.println(
						"conclusion,nOccurrencesInDifferentProofs,queryTicks");
				for (final Entry<Conclusion, ConclusionStat> e : conclusionStats
						.entrySet()) {
					conclusionStatsWriter.print("\"");
					conclusionStatsWriter.print(e.getKey());
					conclusionStatsWriter.print("\",");
					conclusionStatsWriter.print(e.getValue().nOccurrences);
					for (final Integer tick : e.getValue().queryTicks) {
						conclusionStatsWriter.print(",");
						conclusionStatsWriter.print(tick);
					}
					conclusionStatsWriter.println();
				}
			}

		} catch (final FileNotFoundException e) {
			LOGGER_.error("File Not Found!", e);
			System.exit(2);
		} catch (final ElkInconsistentOntologyException e) {
			LOGGER_.error("The ontology is inconsistent!", e);
			System.exit(2);
		} catch (final ElkException e) {
			LOGGER_.error("Could not classify the ontology!", e);
			System.exit(2);
		} catch (final IOException e) {
			LOGGER_.error("Error while reading the conclusion file!", e);
			System.exit(2);
		} catch (final ArgumentParserException e) {
			parser.handleError(e);
			System.exit(2);
		} finally {
			Utils.closeQuietly(ontologyIS);
			Utils.closeQuietly(conclusionReader);
			Utils.closeQuietly(stats);
			Utils.closeQuietly(conclusionStatsWriter);
			Utils.closeQuietly(queryAgeWriter);
		}

	}

	private static void collectStatistics(final ElkSubClassOfAxiom conclusion,
			final Reasoner reasoner, final Recorder.RecordBuilder record,
			final boolean detectCycle, final boolean countComponents,
			final Map<Conclusion, ConclusionStat> conclusionStats,
			final Utils.Counter conclusionTicks,
			final PrintWriter queryAgeWriter) throws ElkException {

		final long startNanos = System.nanoTime();

		final Conclusion expression = Utils
				.getFirstDerivedConclusionForSubsumption(reasoner, conclusion);
		final Proof<Conclusion> proof = reasoner.getProof();
		final TracingInferenceJustifier justifier = TracingInferenceJustifier.INSTANCE;

		final Set<ElkAxiom> axiomExprs = new HashSet<ElkAxiom>();
		final Set<Conclusion> lemmaExprs = new HashSet<Conclusion>();
		final Set<IndexedContextRoot> contexts = new HashSet<IndexedContextRoot>();
		final Set<Inference<Conclusion>> inferences = new HashSet<Inference<Conclusion>>();

		Utils.traverseProofs(expression, proof, justifier,
				new Function<Inference<Conclusion>, Void>() {
					@Override
					public Void apply(final Inference<Conclusion> inf) {
						inferences.add(inf);
						return null;
					}
				}, new Function<Conclusion, Void>() {
					@Override
					public Void apply(final Conclusion expr) {
						final int currentTick = conclusionTicks.next();
						lemmaExprs.add(expr);
						expr.accept(new DummyConclusionVisitor<Void>() {
							@Override
							protected Void defaultVisit(
									final ClassConclusion conclusion) {
								contexts.add(conclusion.getTraceRoot());
								return super.defaultVisit(conclusion);
							}
						});
						if (conclusionStats != null) {
							ConclusionStat stat = conclusionStats.get(expr);
							if (stat == null) {
								stat = new ConclusionStat();
								stat.nOccurrences = 0;
								conclusionStats.put(expr, stat);
							} else {
								if (queryAgeWriter != null) {
									final int queryAge = currentTick
											- stat.getLastQueryTick();
									queryAgeWriter.println(queryAge);
								}
							}
							stat.nOccurrences++;
							stat.queryTicks.add(currentTick);
						}
						return null;
					}
				}, new Function<ElkAxiom, Void>() {
					@Override
					public Void apply(final ElkAxiom axiom) {
						axiomExprs.add(axiom);
						return null;
					}
				});

		record.put("nAxiomsInAllProofs", axiomExprs.size());
		record.put("nConclusionsInAllProofs", lemmaExprs.size());
		record.put("nInferencesInAllProofs", inferences.size());
		record.put("nContextsInAllProofs", contexts.size());

		if (detectCycle) {
			final boolean hasCycle = Proofs.hasCycle(proof, expression);
			record.put("isCycleInInferenceGraph", hasCycle);
		}

		if (countComponents) {
			final StronglyConnectedComponents<Conclusion> components = StronglyConnectedComponentsComputation
					.computeComponents(proof, expression);

			final List<List<Conclusion>> comps = components.getComponents();
			final List<Conclusion> maxComp = Collections.max(comps,
					SIZE_COMPARATOR);
			record.put("sizeOfMaxComponentInInferenceGraph", maxComp.size());

			final Collection<List<Conclusion>> nonSingletonComps = Collections2
					.filter(comps, new Predicate<List<Conclusion>>() {
						@Override
						public boolean apply(final List<Conclusion> comp) {
							return comp.size() > 1;
						}
					});
			record.put("nNonSingletonComponentsInInferenceGraph",
					nonSingletonComps.size());
		}

		final long runTimeNanos = System.nanoTime() - startNanos;
		LOGGER_.info("... took {}s", runTimeNanos / 1000000000.0);
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

	private static final Comparator<Collection<?>> SIZE_COMPARATOR = new Comparator<Collection<?>>() {
		@Override
		public int compare(final Collection<?> o1, final Collection<?> o2) {
			return Integer.compare(o1.size(), o2.size());
		}
	};

	private static class ConclusionStat {
		public int nOccurrences;
		public List<Integer> queryTicks = new ArrayList<>();

		public int getLastQueryTick() {
			return queryTicks.get(queryTicks.size() - 1);
		}
	}

}
