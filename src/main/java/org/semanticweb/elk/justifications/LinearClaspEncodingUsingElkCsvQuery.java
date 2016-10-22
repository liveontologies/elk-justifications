package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.asp.BackwardClaspEncoder;
import org.semanticweb.elk.justifications.asp.DerivabilityClaspEncoder;
import org.semanticweb.elk.justifications.asp.DotEncoder;
import org.semanticweb.elk.justifications.asp.Encoder;
import org.semanticweb.elk.justifications.asp.Encoders;
import org.semanticweb.elk.justifications.asp.ForwardClaspEncoder;
import org.semanticweb.elk.justifications.asp.Index;
import org.semanticweb.elk.justifications.asp.SuffixClaspEncoder;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
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

public class LinearClaspEncodingUsingElkCsvQuery {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(LinearClaspEncodingUsingElkCsvQuery.class);

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
				
				LOG.info("Encoding {} {}", conclIndex, conclusion);
				start = System.currentTimeMillis();
				encode(conclusion, reasoner, outputDirectory, conclCount,
						conclIndex++);
				LOG.info("... took {}s",
						(System.currentTimeMillis() - start)/1000.0);
				
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
	
	private static void encode(final ElkSubClassOfAxiom elkGoalConclusion,
			final Reasoner reasoner, final File outputDirectory,
			final int conclCount, final int conclusionIndex)
					throws ElkException, IOException {
		
		final String conclName = String.format(
				"%0" + Integer.toString(conclCount).length() + "d", conclusionIndex);
		final File outDir = new File(outputDirectory, conclName);
		outDir.mkdirs();
		final File outFile = new File(outDir, "encoding.clasp");
		final File dotFile = new File(outDir, "inferenceGraph.dot");
		
		PrintWriter outWriter = null;
		PrintWriter dotWriter = null;
		
		try {
			
			outWriter = new PrintWriter(outFile);
			dotWriter = new PrintWriter(dotFile);
			
			final ClassConclusion goalConclusion =
					reasoner.getConclusion(elkGoalConclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner.explainConclusion(goalConclusion));
			
			final Index<Conclusion> conclIndex = new Index<>();
			final Index<ElkAxiom> axiomIndex = new Index<>();
			final Index<Inference<Conclusion, ElkAxiom>> infIndex = new Index<>();
			final Index<String> literalIndex = new Index<>(2);// Gringo starts indexing from 2 !!!
			
			/* @formatter:off
			 * 
			 * Encoding:
			 * Inference implies all its premises.
			 * Conclusion implies at least one of its inferences.
			 * Each conclusion and inference has depth at which it is reached
			 * when traversing the proof from the goal conclusion.
			 * Depth increases only when traversing from inference to premise
			 * that is used as premise in multiple inferences
			 * or is the goal conclusion.
			 * Depth must not increase over the maximal depth,
			 * which is the number of premises used in multiple inferences.
			 * 
			 * inf(I,L)
			 * Inference I is used in the proof of justification
			 * and its label is L.
			 * 
			 * concl(C,L)
			 * Conclusion C is used in the proof of justification
			 * and its label is L.
			 * 
			 * axiom(A,L)
			 * Axiom A is used in the proof of justification
			 * and its label is L.
			 * 
			 * conclDerived(C)
			 * Conclusion C is derived from the justification.
			 * 
			 * % Generate justification proof.
			 * 
			 * concl(goalConclusion,_).
			 * where goalConclusion is the goal conclusion.
			 * 
			 * concl(P,_) :- inf(I,_).
			 * where P is a premise of I.
			 * 
			 * axiom(A,_) :- inf(I,_).
			 * where A is in justification of I.
			 * 
			 * inf(I1,_) | ... | inf(In,_) :- concl(C,_).
			 * where I1, ..., In are all inferences that derive C.
			 * 
			 * % Saturation to use ASP minimality
			 * 
			 * concl(C,_) :- inf(I,_).
			 * where C is the conclusion of I.
			 * 
			 * inf(I,_) :- concl(P1,_), ..., concl(Pm,_).
			 * where P1, ..., Pm are all premises of I.
			 * FIXME: I must have empty justification !!!
			 * 
			 * % Check that the goal conclusion is really derived.
			 * 
			 * :- not conclDerived(goalConclusion).
			 * where goalConclusion is the goal conclusion.
			 * 
			 * conclDerived(C) :- conclDerived(P1), ..., conclDerived(Pm),
			 * 		axiom(A1), ..., axiom(An).
			 * where C is the conclusion of an inference with premises P1, ...,
			 * Pm and justification A1, ..., An.
			 * 
			 * @formatter:on
			 */
			
			final Encoder<Conclusion, ElkAxiom> encoder = Encoders.combine(
					new BackwardClaspEncoder<Conclusion, ElkAxiom>(outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
					new ForwardClaspEncoder<Conclusion, ElkAxiom>(outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
					new DerivabilityClaspEncoder<Conclusion, ElkAxiom>(outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
					new SuffixClaspEncoder<Conclusion, ElkAxiom>("", outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
//					new SuffixClaspEncoder<Conclusion, ElkAxiom>("axiom", outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
					new DotEncoder<Conclusion, ElkAxiom>(dotWriter, inferenceSet, conclIndex, axiomIndex, infIndex)
				);
			
			Encoders.encode(inferenceSet, goalConclusion, encoder);
			
			LOG.debug("number of literals: {}", literalIndex.getIndex().size());
			
		} finally {
			if (outWriter != null) {
				outWriter.close();
			}
			if (dotWriter != null) {
				dotWriter.close();
			}
		}
		
	}
	
}
