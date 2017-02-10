package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.ConvertToElSatKrssInput.ElSatPrinterVisitor;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkClassAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.owl.visitors.ElkAxiomProcessor;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.TracingInferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;

public class DirectSatEncodingUsingElk {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(DirectSatEncodingUsingElk.class);

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
		
		InputStream ontologyIS = null;
		InputStream conclusionIS = null;
		
		try {
			
			ontologyIS = new FileInputStream(ontologyFileName);
			conclusionIS = new FileInputStream(conclusionsFileName);
			
			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory().createReasoner(
					loader);
			
			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			final AxiomLoader conclusionLoader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), conclusionIS)
					.getAxiomLoader(Utils.getDummyInterruptMonitor());
			
			final Encoder encoder = new Encoder(reasoner, outputDirectory);
			try {
				conclusionLoader.load(encoder, new DummyAxiomProcessor());
			} catch (final TunnellingFileException e) {
				throw e.e;
			} catch (final TunnellingElkException e) {
				throw e.e;
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
		} finally {
			if (ontologyIS != null) {
				try {
					ontologyIS.close();
				} catch (final IOException e) {}
			}
			if (conclusionIS != null) {
				try {
					conclusionIS.close();
				} catch (final IOException e) {}
			}
		}
		
	}
	
	private static class Encoder implements ElkAxiomProcessor {

		private final Reasoner reasoner;
		private final File outputDirectory;
		
		public Encoder(final Reasoner reasoner, final File outputDirectory) {
			this.reasoner = reasoner;
			this.outputDirectory = outputDirectory;
		}
		
		@Override
		public void visit(final ElkAxiom elkAxiom) {

			if (!(elkAxiom instanceof ElkSubClassOfAxiom)) {
				return;
			}
			final ElkSubClassOfAxiom conclusion = (ElkSubClassOfAxiom) elkAxiom;
			
			final String conclName = Utils.toFileName(conclusion);
			final File outDir = new File(outputDirectory, conclName);
			final File hFile = new File(outDir, "encoding.h");
			final File cnfFile = new File(outDir, "encoding.cnf");
			final File questionFile = new File(outDir, "encoding.question");
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
						.getFirstDerivedConclusionForSubsumption(reasoner,
								conclusion);
				final TracingInferenceSet inferenceSet =
						reasoner.explainConclusion(expression);
				
				final Set<ElkAxiom> axiomExprs =
						new HashSet<ElkAxiom>();
				final Set<Conclusion> lemmaExprs =
						new HashSet<Conclusion>();
				
				Utils.traverseProofs(expression, inferenceSet,
						Functions.<JustifiedInference<Conclusion, ElkAxiom>>identity(),
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
						new Function<JustifiedInference<Conclusion, ElkAxiom>, Void>() {
							@Override
							public Void apply(
									final JustifiedInference<Conclusion, ElkAxiom> inf) {
								
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
				
			} catch (final FileNotFoundException e) {
				throw new TunnellingFileException(e);
			} catch (final ElkException e) {
				throw new TunnellingElkException(e);
			} finally {
				if (cnfWriter != null) {
					cnfWriter.close();
				}
				if (hWriter != null) {
					hWriter.close();
				}
			}
			
		}
		
	}
	
	private static class DummyAxiomProcessor implements ElkAxiomProcessor {
		@Override
		public void visit(final ElkAxiom elkAxiom) {
			// Empty.
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
	
	private static class TunnellingElkException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public final ElkException e;
		public TunnellingElkException(final ElkException cause) {
			super(cause);
			this.e = cause;
		}
	}
	
	private static class TunnellingFileException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public final FileNotFoundException e;
		public TunnellingFileException(final FileNotFoundException cause) {
			super(cause);
			this.e = cause;
		}
	}
	
}
