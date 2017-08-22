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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.liveontologies.pinpointing.ConvertToElSatKrssInput.ElSatPrinterVisitor;
import org.liveontologies.pinpointing.experiments.CsvQueryDecoder;
import org.liveontologies.proofs.TracingInferenceJustifier;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.Proof;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkClassAxiom;
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
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class DirectSatEncodingUsingElkCsvQuery {

	private static final Logger LOG_ = LoggerFactory
			.getLogger(DirectSatEncodingUsingElkCsvQuery.class);

	private static final ElkObjectBaseFactory FACTORY_ = new ElkObjectBaseFactory();

	public static final String ONTOLOGY_OPT = "ontology";
	public static final String QUERIES_OPT = "queries";
	public static final String OUTDIR_OPT = "outdir";

	public static class Options {
		@Arg(dest = ONTOLOGY_OPT)
		public File ontologyFile;
		@Arg(dest = QUERIES_OPT)
		public File queriesFile;
		@Arg(dest = OUTDIR_OPT)
		public File outDir;
	}

	public static void main(final String[] args) {

		final ArgumentParser parser = ArgumentParsers
				.newArgumentParser(
						DirectSatEncodingUsingElkCsvQuery.class.getSimpleName())
				.description("Export proofs into *.wcnf files.");
		parser.addArgument(ONTOLOGY_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("ontology file");
		parser.addArgument(QUERIES_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("query file");
		parser.addArgument(OUTDIR_OPT).type(File.class)
				.help("output directory");

		InputStream ontologyIS = null;
		BufferedReader queryReader = null;

		try {

			final Options opt = new Options();
			parser.parseArgs(args, opt);

			if (!Utils.cleanDir(opt.outDir)) {
				LOG_.error("Could not prepare the output directory!");
				System.exit(2);
			}

			ontologyIS = new FileInputStream(opt.ontologyFile);

			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory()
					.createReasoner(loader);

			LOG_.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

			queryReader = new BufferedReader(new FileReader(opt.queriesFile));
			int queryCount = 0;
			String line;
			while ((line = queryReader.readLine()) != null) {
				queryCount++;
			}
			queryReader.close();

			queryReader = new BufferedReader(new FileReader(opt.queriesFile));

			int queryIndex = 0;
			while ((line = queryReader.readLine()) != null) {

				LOG_.debug("Encoding {} of {}: {}", queryIndex, queryCount,
						line);

				encode(line, reasoner, opt.outDir, queryCount, queryIndex++);

			}

		} catch (final FileNotFoundException e) {
			LOG_.error("File Not Found!", e);
			System.exit(2);
		} catch (final ElkInconsistentOntologyException e) {
			LOG_.error("The ontology is inconsistent!", e);
			System.exit(2);
		} catch (final ElkException e) {
			LOG_.error("Could not classify the ontology!", e);
			System.exit(2);
		} catch (final IOException e) {
			LOG_.error("I/O error!", e);
			System.exit(2);
		} catch (final ArgumentParserException e) {
			parser.handleError(e);
			System.exit(2);
		} finally {
			Utils.closeQuietly(ontologyIS);
			Utils.closeQuietly(queryReader);
		}

	}

	private static void encode(final String line, final Reasoner reasoner,
			final File outputDirectory, final int queryCount,
			final int queryIndex) throws ElkException, IOException {

		final ElkSubClassOfAxiom query = CsvQueryDecoder.decode(line,
				new CsvQueryDecoder.Factory<ElkSubClassOfAxiom>() {

					@Override
					public ElkSubClassOfAxiom createQuery(final String subIri,
							final String supIri) {
						return FACTORY_.getSubClassOfAxiom(
								FACTORY_.getClass(new ElkFullIri(subIri)),
								FACTORY_.getClass(new ElkFullIri(supIri)));
					}

				});

		// final String conclName = Utils.toFileName(conclusion);
		final String queryName = String.format(
				"%0" + Integer.toString(queryCount).length() + "d", queryIndex);
		final File outDir = new File(outputDirectory, queryName);
		final File hFile = new File(outDir, "encoding.h");
		final File cnfFile = new File(outDir, "encoding.cnf");
		final File questionFile = new File(outDir, "encoding.question");
		final File queryFile = new File(outDir, "encoding.query");
		final File pppFile = new File(outDir, "encoding.ppp");
		final File pppguFile = new File(outDir, "encoding.ppp.g.u");
		final File zzzFile = new File(outDir, "encoding.zzz");
		final File zzzgciFile = new File(outDir, "encoding.zzz.gci");
		final File zzzriFile = new File(outDir, "encoding.zzz.ri");
		outDir.mkdirs();

		PrintWriter cnfWriter = null;
		PrintWriter hWriter = null;

		try {

			cnfWriter = new PrintWriter(cnfFile);
			hWriter = new PrintWriter(hFile);
			final PrintWriter cnf = cnfWriter;

			final Conclusion expression = Utils
					.getFirstDerivedConclusionForSubsumption(reasoner, query);
			final Proof<Conclusion> proof = reasoner
					.getProof();
			final TracingInferenceJustifier justifier = TracingInferenceJustifier.INSTANCE;

			final Set<ElkAxiom> axioms = new HashSet<ElkAxiom>();
			final Set<Conclusion> conclusions = new HashSet<Conclusion>();

			Utils.traverseProofs(expression, proof, justifier,
					Functions.<Inference<Conclusion>> identity(),
					new Function<Conclusion, Void>() {
						@Override
						public Void apply(final Conclusion expr) {
							conclusions.add(expr);
							return null;
						}
					}, new Function<ElkAxiom, Void>() {
						@Override
						public Void apply(final ElkAxiom axiom) {
							axioms.add(axiom);
							return null;
						}
					});

			final Utils.Counter literalCounter = new Utils.Counter(1);
			final Utils.Counter clauseCounter = new Utils.Counter();

			final Map<ElkAxiom, Integer> axiomIndex = new HashMap<ElkAxiom, Integer>();
			for (final ElkAxiom axiom : axioms) {
				axiomIndex.put(axiom, literalCounter.next());
			}
			final Map<Conclusion, Integer> conclusionIndex = new HashMap<Conclusion, Integer>();
			for (final Conclusion conclusion : conclusions) {
				conclusionIndex.put(conclusion, literalCounter.next());
			}

			// cnf
			Utils.traverseProofs(expression, proof, justifier,
					new Function<Inference<Conclusion>, Void>() {
						@Override
						public Void apply(final Inference<Conclusion> inf) {

							LOG_.trace("processing {}", inf);

							for (final ElkAxiom axiom : justifier
									.getJustification(inf)) {
								cnf.print(-axiomIndex.get(axiom));
								cnf.print(" ");
							}

							for (final Conclusion premise : inf.getPremises()) {
								cnf.print(-conclusionIndex.get(premise));
								cnf.print(" ");
							}

							cnf.print(conclusionIndex.get(inf.getConclusion()));
							cnf.println(" 0");
							clauseCounter.next();

							return null;
						}
					}, Functions.<Conclusion> identity(),
					Functions.<ElkAxiom> identity());

			final int lastLiteral = literalCounter.next();

			// h
			hWriter.println(
					"p cnf " + (lastLiteral - 1) + " " + clauseCounter.next());

			// ppp
			writeLines(axiomIndex.values(), pppFile);

			// ppp.g.u
			final List<Integer> orderedAxioms = new ArrayList<Integer>(
					axiomIndex.values());
			Collections.sort(orderedAxioms);
			writeLines(orderedAxioms, pppguFile);

			// question
			writeLines(Collections.singleton(conclusionIndex.get(expression)),
					questionFile);

			// query
			writeLines(Collections.singleton(line), queryFile);

			// zzz
			final SortedMap<Integer, ElkAxiom> gcis = new TreeMap<Integer, ElkAxiom>();
			final SortedMap<Integer, ElkAxiom> ris = new TreeMap<Integer, ElkAxiom>();
			for (final Entry<ElkAxiom, Integer> entry : axiomIndex.entrySet()) {
				final ElkAxiom expr = entry.getKey();
				final int lit = entry.getValue();
				if (expr instanceof ElkClassAxiom) {
					gcis.put(lit, expr);
				} else {
					ris.put(lit, expr);
				}
			}
			final SortedMap<Integer, Conclusion> lemmas = new TreeMap<Integer, Conclusion>();
			for (final Entry<Conclusion, Integer> entry : conclusionIndex
					.entrySet()) {
				lemmas.put(entry.getValue(), entry.getKey());
			}

			writeLines(Iterables.transform(gcis.entrySet(), PRINT2),
					zzzgciFile);
			writeLines(Iterables.transform(ris.entrySet(), PRINT2), zzzriFile);
			writeLines(Iterables.transform(lemmas.entrySet(), PRINT), zzzFile);

		} finally {
			Utils.closeQuietly(cnfWriter);
			Utils.closeQuietly(hWriter);
		}

	}

	private static void writeLines(final Iterable<?> lines, final File file)
			throws FileNotFoundException {

		PrintWriter writer = null;

		try {
			writer = new PrintWriter(file);

			for (final Object line : lines) {
				writer.println(line);
			}

		} finally {
			if (writer != null) {
				writer.close();
			}
		}

	}

	private static final Function<Entry<Integer, Conclusion>, String> PRINT = new Function<Entry<Integer, Conclusion>, String>() {

		@Override
		public String apply(final Entry<Integer, Conclusion> entry) {
			final StringBuilder result = new StringBuilder();

			result.append(entry.getKey()).append(" ").append(entry.getValue());

			return result.toString();
		}

	};

	private static final Function<Entry<Integer, ElkAxiom>, String> PRINT2 = new Function<Entry<Integer, ElkAxiom>, String>() {

		@Override
		public String apply(final Entry<Integer, ElkAxiom> entry) {
			final StringBuilder result = new StringBuilder();

			result.append(entry.getKey()).append(" ");

			final ElSatPrinterVisitor printer = new ElSatPrinterVisitor(result);

			entry.getValue().accept(printer);

			result.setLength(result.length() - 1);// Remove the last line end.

			return result.toString();
		}

	};

}
