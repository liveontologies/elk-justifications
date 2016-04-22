package org.semanticweb.elk.justifications;

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

import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.ConvertToElSatKrssInput.ElSatPrinterVisitor;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkClassAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.TracingInferenceSetInferenceSetAdapter;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.saturation.conclusions.model.ClassConclusion;
import org.semanticweb.elk.reasoner.stages.RestartingStageExecutor;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;

public class DirectSatEncodingUsingElkCsvQuery {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(DirectSatEncodingUsingElkCsvQuery.class);

	public static void main(final String[] args) {
		
		if (args.length < 3) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final String ontologyFileName = args[0];
		final String conclusionsFileName = args[1];
		final File outputDirectory = new File(args[2]);
		if (!Utils.cleanDir(outputDirectory)) {
			LOG.error("Could not prepare the output directory!");
			System.exit(2);
		}
		
		final ElkObjectBaseFactory factory = new ElkObjectBaseFactory();
		
		InputStream ontologyIS = null;
		BufferedReader conclusionReader = null;
		
		try {
			
			ontologyIS = new FileInputStream(ontologyFileName);
			
			final AxiomLoader ontologyLoader = new Owl2StreamLoader(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory().createReasoner(
					ontologyLoader, new RestartingStageExecutor());
			
			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			
			int conclCount = 0;
			String line;
			while ((line = conclusionReader.readLine()) != null) {
				conclCount++;
			}
			conclusionReader.close();
			
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			
			int conclIndex = 0;
			while ((line = conclusionReader.readLine()) != null) {
				
				final String[] columns = line.split(",");
				if (columns.length < 2) {
					return;
				}
				
				final String subIri = strip(columns[0]);
				final String supIri = strip(columns[1]);
				
				final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
						factory.getClass(new ElkFullIri(subIri)),
						factory.getClass(new ElkFullIri(supIri)));
				
				LOG.debug("Collecting statistics for {}", conclusion);
				
				encode(conclusion, reasoner, outputDirectory, conclCount,
						conclIndex++);
				
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
			LOG.error("I/O error!", e);
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
		}
		
	}
	
	private static String strip(final String s) {
		final String trimmed = s.trim();
		int start = 0;
		if (trimmed.charAt(0) == '"') {
			start = 1;
		}
		int end = trimmed.length();
		if (trimmed.charAt(trimmed.length() - 1) == '"') {
			end = trimmed.length() - 1;
		}
		return trimmed.substring(start, end);
	}
	
	private static void encode(final ElkSubClassOfAxiom conclusion,
			final Reasoner reasoner, final File outputDirectory,
			final int conclCount, final int conclIndex)
					throws ElkException, IOException {
		
//		final String conclName = Utils.toFileName(conclusion);
		final String conclName = String.format(
				"%0" + Integer.toString(conclCount).length() + "d", conclIndex);
		final File outDir = new File(outputDirectory, conclName);
		final File hFile = new File(outDir, "encoding.h");
		final File cnfFile = new File(outDir, "encoding.cnf");
		final File questionFile = new File(outDir, "encoding.question");
		final File conclusionFile = new File(outDir, "encoding.conclusion");
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
			
			final ClassConclusion expression =
					reasoner.getConclusion(conclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner.explainConclusion(expression));
			
			final Set<ElkAxiom> axiomExprs =
					new HashSet<ElkAxiom>();
			final Set<Conclusion> lemmaExprs =
					new HashSet<Conclusion>();
			
			Utils.traverseProofs(expression, inferenceSet,
					Functions.<Inference<Conclusion, ElkAxiom>>identity(),
					new Function<Conclusion, Void>(){
						@Override
						public Void apply(final Conclusion expr) {
							lemmaExprs.add(expr);
							return null;
						}
					},
					new Function<ElkAxiom, Void>(){
						@Override
						public Void apply(final ElkAxiom axiom) {
							axiomExprs.add(axiom);
							return null;
						}
					}
			);
			
			final Counter literalCounter = new Counter(1);
			final Counter clauseCounter = new Counter();
			
			final Map<ElkAxiom, Integer> axiomIndex =
					new HashMap<ElkAxiom, Integer>();
			for (final ElkAxiom axExpr : axiomExprs) {
				axiomIndex.put(axExpr, literalCounter.next());
			}
			final Map<Conclusion, Integer> conclusionIndex =
					new HashMap<Conclusion, Integer>();
			for (final Conclusion expr : lemmaExprs) {
				conclusionIndex.put(expr, literalCounter.next());
			}
			
			// cnf
			Utils.traverseProofs(expression, inferenceSet,
					new Function<Inference<Conclusion, ElkAxiom>, Void>() {
						@Override
						public Void apply(
								final Inference<Conclusion, ElkAxiom> inf) {
							
							LOG.trace("processing {}", inf);
							
							for (final ElkAxiom axiom :
									inf.getJustification()) {
								cnf.print(-axiomIndex.get(axiom));
								cnf.print(" ");
							}
							
							for (final Conclusion premise :
									inf.getPremises()) {
								cnf.print(-conclusionIndex.get(premise));
								cnf.print(" ");
							}
							
							cnf.print(conclusionIndex.get(inf.getConclusion()));
							cnf.println(" 0");
							clauseCounter.next();
							
							return null;
						}
					},
					Functions.<Conclusion>identity(),
					Functions.<ElkAxiom>identity());
			
			final int lastLiteral = literalCounter.next();
			
			// h
			hWriter.println("p cnf " + (lastLiteral - 1)
					+ " " + clauseCounter.next());
			
			// ppp
			writeLines(axiomIndex.values(), pppFile);
			
			// ppp.g.u
			final List<Integer> orderedAxioms =
					new ArrayList<Integer>(axiomIndex.values());
			Collections.sort(orderedAxioms);
			writeLines(orderedAxioms, pppguFile);
			
			// question
			writeLines(Collections.singleton(conclusionIndex.get(expression)),
					questionFile);
			
			// conclusion
			writeLines(Collections.singleton(
					"" + conclusionIndex.get(expression) + " " + conclusion),
					conclusionFile);
			
			// zzz
			final SortedMap<Integer, ElkAxiom> gcis =
					new TreeMap<Integer, ElkAxiom>();
			final SortedMap<Integer, ElkAxiom> ris =
					new TreeMap<Integer, ElkAxiom>();
			for (final Entry<ElkAxiom, Integer> entry
					: axiomIndex.entrySet()) {
				final ElkAxiom expr = entry.getKey();
				final int lit = entry.getValue();
				if (expr instanceof ElkClassAxiom) {
					gcis.put(lit, expr);
				} else {
					ris.put(lit, expr);
				}
			}
			final SortedMap<Integer, Conclusion> lemmas =
					new TreeMap<Integer, Conclusion>();
			for (final Entry<Conclusion, Integer> entry
					: conclusionIndex.entrySet()) {
				lemmas.put(entry.getValue(), entry.getKey());
			}
			
			writeLines(Iterables.transform(gcis.entrySet(), PRINT2), zzzgciFile);
			writeLines(Iterables.transform(ris.entrySet(), PRINT2), zzzriFile);
			writeLines(Iterables.transform(lemmas.entrySet(), PRINT), zzzFile);
			
		} finally {
			if (cnfWriter != null) {
				cnfWriter.close();
			}
			if (hWriter != null) {
				hWriter.close();
			}
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
	
	private static final Function<Entry<Integer, Conclusion>, String> PRINT =
			new Function<Entry<Integer, Conclusion>, String>() {
		
		@Override
		public String apply(final Entry<Integer, Conclusion> entry) {
			final StringBuilder result = new StringBuilder();
			
			result.append(entry.getKey()).append(" ").append(entry.getValue());
			
			return result.toString();
		}
		
	};
	
	private static final Function<Entry<Integer, ElkAxiom>, String> PRINT2 =
			new Function<Entry<Integer, ElkAxiom>, String>() {
		
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
	
	private static class Counter {
		private int counter;
		public Counter() {
			this(0);
		}
		public Counter(final int first) {
			this.counter = first;
		}
		public int next() {
			return counter++;
		}
	}
	
}
