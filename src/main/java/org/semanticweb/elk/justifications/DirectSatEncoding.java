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

import org.liveontologies.owlapi.proof.OWLProofNode;
import org.semanticweb.elk.justifications.ConvertToElSatKrssInput.ElSatPrinterVisitor;
import org.semanticweb.elk.owlapi.ElkProver;
import org.semanticweb.elk.owlapi.ElkProverFactory;
import org.semanticweb.elk.owlapi.wrapper.OwlConverter;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.adapters.OWLExpressionInferenceSetAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
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
			
			final ElkProverFactory proverFactory = new ElkProverFactory();
			final ElkProver reasoner = proverFactory.createReasoner(ont);
			
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
		} catch (final FileNotFoundException e) {
			LOG.error("File Not Found!", e);
			System.exit(2);
		}
		
	}

	private static void encodeConclusion(final ElkProver reasoner,
			final OWLSubClassOfAxiom conclusion, final File outputDirectory)
					throws FileNotFoundException {

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
			
			final OWLProofNode proofNode = reasoner.getProof(conclusion);
			final OWLExpressionInferenceSetAdapter inferenceSet =
					new OWLExpressionInferenceSetAdapter(reasoner.getRootOntology());
			
			final Set<OWLAxiom> axiomExprs =
					new HashSet<OWLAxiom>();
			final Set<OWLProofNode> lemmaExprs =
					new HashSet<OWLProofNode>();
			
			Utils.traverseProofs(proofNode, inferenceSet,
					Functions.<Inference<OWLProofNode, OWLAxiom>>identity(),
					new Function<OWLProofNode, Void>(){
						@Override
						public Void apply(final OWLProofNode expr) {
							lemmaExprs.add(expr);
							return null;
						}
					},
					new Function<OWLAxiom, Void>(){
						@Override
						public Void apply(final OWLAxiom axiom) {
							axiomExprs.add(axiom);
							return null;
						}
					}
			);
			
			final Counter literalCounter = new Counter(1);
			final Counter clauseCounter = new Counter();
			
			final Map<OWLAxiom, Integer> axiomIndex =
					new HashMap<OWLAxiom, Integer>();
			for (final OWLAxiom axExpr : axiomExprs) {
				axiomIndex.put(axExpr, literalCounter.next());
			}
			final Map<OWLProofNode, Integer> conclusionIndex =
					new HashMap<OWLProofNode, Integer>();
			for (final OWLProofNode expr : lemmaExprs) {
				conclusionIndex.put(expr, literalCounter.next());
			}
			
			// cnf
			Utils.traverseProofs(proofNode, inferenceSet,
					new Function<Inference<OWLProofNode, OWLAxiom>, Void>() {
						@Override
						public Void apply(
								final Inference<OWLProofNode, OWLAxiom> inf) {
							
							LOG.trace("processing {}", inf);
							
							for (final OWLAxiom axiom :
									inf.getJustification()) {
								cnf.print(-axiomIndex.get(axiom));
								cnf.print(" ");
							}
							
							for (final OWLProofNode premise :
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
					Functions.<OWLProofNode>identity(),
					Functions.<OWLAxiom>identity());
			
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
			writeLines(Collections.singleton(conclusionIndex.get(proofNode)),
					questionFile);
			
			// zzz
			final SortedMap<Integer, OWLAxiom> gcis =
					new TreeMap<Integer, OWLAxiom>();
			final SortedMap<Integer, OWLAxiom> ris =
					new TreeMap<Integer, OWLAxiom>();
			for (final Entry<OWLAxiom, Integer> entry
					: axiomIndex.entrySet()) {
				final OWLAxiom expr = entry.getKey();
				final int lit = entry.getValue();
				if (expr instanceof OWLPropertyAxiom) {
					ris.put(lit, expr);
				} else {
					gcis.put(lit, expr);
				}
			}
			final SortedMap<Integer, OWLProofNode> lemmas =
					new TreeMap<Integer, OWLProofNode>();
			for (final Entry<OWLProofNode, Integer> entry
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
	
	private static final OwlConverter OWLCONVERTER = OwlConverter.getInstance();
	
	private static final Function<Entry<Integer, OWLProofNode>, String> PRINT =
			new Function<Entry<Integer, OWLProofNode>, String>() {
		
		@Override
		public String apply(final Entry<Integer, OWLProofNode> entry) {
			final StringBuilder result = new StringBuilder();
			
			result.append(entry.getKey()).append(" ");
			
			final ElSatPrinterVisitor printer = new ElSatPrinterVisitor(result);
			
			OWLCONVERTER.convert(entry.getValue().getMember()).accept(printer);
			
			result.setLength(result.length() - 1);// Remove the last line end.
			
			return result.toString();
		}
		
	};
	
	private static final Function<Entry<Integer, OWLAxiom>, String> PRINT2 =
			new Function<Entry<Integer, OWLAxiom>, String>() {
		
		@Override
		public String apply(final Entry<Integer, OWLAxiom> entry) {
			final StringBuilder result = new StringBuilder();
			
			result.append(entry.getKey()).append(" ");
			
			final ElSatPrinterVisitor printer = new ElSatPrinterVisitor(result);
			
			OWLCONVERTER.convert(entry.getValue()).accept(printer);
			
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
