package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileNotFoundException;
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

import org.semanticweb.elk.justifications.ConvertToElSatKrssInput.ElSatPrinterVisitor;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.owlapi.wrapper.OwlConverter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.semanticweb.owlapitools.proofs.OWLInference;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLAxiomExpression;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpressionVisitor;
import org.semanticweb.owlapitools.proofs.expressions.OWLLemmaExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;

public class DirectSatEncoding {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(DirectSatEncoding.class);

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
		
		final OWLOntologyManager manager =
				OWLManager.createOWLOntologyManager();

		try {
			
			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager.loadOntologyFromOntologyDocument(
					new File(ontologyFileName));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Loaded ontology: {}", ont.getOntologyID());
			
			LOG.info("Loading conclusions ...");
			start = System.currentTimeMillis();
			final OWLOntology conclusionsOnt =
					manager.loadOntologyFromOntologyDocument(
							new File(conclusionsFileName));
			final Set<OWLSubClassOfAxiom> conclusions =
					conclusionsOnt.getAxioms(AxiomType.SUBCLASS_OF);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Number of conclusions: {}", conclusions.size());
			
			final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			final ExplainingOWLReasoner reasoner =
					(ExplainingOWLReasoner) reasonerFactory.createReasoner(ont);
			
			LOG.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);

			for (final OWLSubClassOfAxiom conclusion : conclusions) {
				encodeConclusion(reasoner, conclusion, outputDirectory);
			}
			
		} catch (final OWLOntologyCreationException e) {
			LOG.error("Could not load the ontology!", e);
			System.exit(2);
		} catch (final UnsupportedEntailmentTypeException e) {
			LOG.error("Could not obtain the proof!", e);
			System.exit(3);
		} catch (final ProofGenerationException e) {
			LOG.error("Could not obtain the proof!", e);
			System.exit(3);
		} catch (final FileNotFoundException e) {
			LOG.error("File Not Found!", e);
			System.exit(2);
		}
		
	}

	private static void encodeConclusion(final ExplainingOWLReasoner reasoner,
			final OWLSubClassOfAxiom conclusion, final File outputDirectory)
					throws ProofGenerationException, FileNotFoundException {

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
			
			final OWLAxiomExpression expression = reasoner
					.getDerivedExpression(conclusion);
			
			final Set<OWLAxiomExpression> axiomExprs =
					new HashSet<OWLAxiomExpression>();
			final Set<OWLLemmaExpression> lemmaExprs =
					new HashSet<OWLLemmaExpression>();
			
			Utils.traverseProofs(expression, false,
					Functions.<OWLInference>identity(),
					new OWLExpressionVisitor<Void>() {
						@Override
						public Void visit(final OWLAxiomExpression expression) {
							axiomExprs.add(expression);
							return null;
						}
						@Override
						public Void visit(final OWLLemmaExpression expression) {
							lemmaExprs.add(expression);
							return null;
						}
			});
			
			final Counter literalCounter = new Counter(1);
			final Counter clauseCounter = new Counter();
			
			final Map<OWLExpression, Integer> conclusionIndex =
					new HashMap<OWLExpression, Integer>();
			final Set<Integer> axiomLiterals = new HashSet<Integer>();
			for (final OWLAxiomExpression axExpr : axiomExprs) {
				if (isAsserted(axExpr)) {
					final int literal = literalCounter.next();
					conclusionIndex.put(axExpr, literal);
					axiomLiterals.add(literal);
				}
			}
			for (final OWLAxiomExpression axExpr : axiomExprs) {
				if (!isAsserted(axExpr)) {
					conclusionIndex.put(axExpr, literalCounter.next());
				}
			}
			for (final OWLLemmaExpression expr : lemmaExprs) {
				conclusionIndex.put(expr, literalCounter.next());
			}
			
			// cnf
			Utils.traverseProofs(expression, false,
					new Function<OWLInference, Void>() {
						@Override
						public Void apply(final OWLInference inf) {
							
							LOG.trace("processing {}", inf);
							
							for (final OWLExpression premise :
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
					DUMMY_EXPRESSION_VISITOR);
			
			final int lastLiteral = literalCounter.next();
			
			// h
			hWriter.println("p cnf " + (lastLiteral - 1)
					+ " " + clauseCounter.next());
			
			// ppp
			writeLines(axiomLiterals, pppFile);
			
			// ppp.g.u
			final List<Integer> orderedAxioms =
					new ArrayList<Integer>(axiomLiterals);
			Collections.sort(orderedAxioms);
			writeLines(orderedAxioms, pppguFile);
			
			// question
			writeLines(Collections.singleton(conclusionIndex.get(expression)),
					questionFile);
			
			// zzz
			final SortedMap<Integer, OWLExpression> gcis =
					new TreeMap<Integer, OWLExpression>();
			final SortedMap<Integer, OWLExpression> ris =
					new TreeMap<Integer, OWLExpression>();
			final SortedMap<Integer, OWLExpression> lemmas =
					new TreeMap<Integer, OWLExpression>();
			for (final Entry<OWLExpression, Integer> entry
					: conclusionIndex.entrySet()) {
				final OWLExpression expr = entry.getKey();
				final int lit = entry.getValue();
				
				if (axiomLiterals.contains(lit)) {
					if (((OWLAxiomExpression) expression).getAxiom() instanceof OWLPropertyAxiom) {
						ris.put(lit, expr);
					} else {
						gcis.put(lit, expr);
					}
				} else {
					lemmas.put(lit, expr);
				}
				
			}
			
			writeLines(Iterables.transform(gcis.entrySet(), PRINT), zzzgciFile);
			writeLines(Iterables.transform(ris.entrySet(), PRINT), zzzriFile);
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
	
	private static final OwlConverter OWLCONVERTER = OwlConverter.getInstance();
	
	private static final Function<Entry<Integer, OWLExpression>, String> PRINT =
			new Function<Entry<Integer, OWLExpression>, String>() {
		
		@Override
		public String apply(final Entry<Integer, OWLExpression> entry) {
			final StringBuilder result = new StringBuilder();
			
			result.append(entry.getKey()).append(" ");
			
			final ElSatPrinterVisitor printer = new ElSatPrinterVisitor(result);
			
			if (entry.getValue() instanceof OWLAxiomExpression) {
				final OWLAxiomExpression axExpr =
						(OWLAxiomExpression) entry.getValue();
				OWLCONVERTER.convert(axExpr.getAxiom()).accept(printer);
			} else {
				result.append(entry.getValue()).append("\n");
			}
			
			result.setLength(result.length() - 1);// Remove the last line end.
			
			return result.toString();
		}
		
	};
	
	private static boolean isAsserted(final OWLExpression expression) {
		if (expression instanceof OWLAxiomExpression) {
			if (((OWLAxiomExpression) expression).isAsserted()) {
				return true;
			}
		}
		return false;
	}
	
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
	
	private static final OWLExpressionVisitor<Void> DUMMY_EXPRESSION_VISITOR =
			new OWLExpressionVisitor<Void>() {

		@Override
		public Void visit(final OWLAxiomExpression expression) {
			return null;
		}

		@Override
		public Void visit(final OWLLemmaExpression expression) {
			return null;
		}
		
	};
	
}
