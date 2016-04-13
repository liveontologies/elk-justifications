package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.proofs.adapters.OWLExpressionInferenceSetAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OwlapiExperiment extends Experiment {
	
	private static final Logger LOG = LoggerFactory.getLogger(OwlapiExperiment.class);

	private final File outputDirectory;
	private final OWLOntologyManager manager;
	private final ExplainingOWLReasoner reasoner;
	private final List<OWLSubClassOfAxiom> conclusions;
	private final JustificationComputation<OWLExpression, OWLAxiom> computation;
	
	private Collection<Set<OWLAxiom>> justifications;
	
	public OwlapiExperiment(final String[] args) throws ExperimentException {
		super(args);
		
		if (args.length < 3) {
			throw new ExperimentException("Insufficient arguments!");
		}
		
		final String ontologyFileName = args[0];
		final String conclusionsFileName = args[1];
		outputDirectory = new File(args[2]);
		if (!Utils.cleanDir(outputDirectory)) {
			LOG.error("Could not prepare the output directory!");
			System.exit(2);
		}
		
		manager = OWLManager.createOWLOntologyManager();
		
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
			conclusions = new ArrayList<OWLSubClassOfAxiom>(
					conclusionsOnt.getAxioms(AxiomType.SUBCLASS_OF));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Number of conclusions: {}", conclusions.size());
			
			final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			reasoner =
					(ExplainingOWLReasoner) reasonerFactory.createReasoner(ont);
			
			LOG.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
		} catch (final OWLOntologyCreationException e) {
			throw new ExperimentException(e);
		}
		
		this.computation =
				new BottomUpJustificationComputation<OWLExpression, OWLAxiom>(
							new OWLExpressionInferenceSetAdapter());
		
	}

	@Override
	public int getInputSize() {
		return conclusions.size();
	}

	@Override
	public String getInputName(final int inputIndex)
			throws ExperimentException {
		return conclusions.get(inputIndex).toString();
	}

	@Override
	public int run(final int inputIndex)
			throws ExperimentException, InterruptedException {
		try {
			justifications = computation.computeJustifications(
					reasoner.getDerivedExpression(conclusions.get(inputIndex)));
			return justifications.size();
		} catch (final UnsupportedEntailmentTypeException e) {
			throw new ExperimentException(e);
		} catch (final ProofGenerationException e) {
			throw new ExperimentException(e);
		}
	}

	@Override
	public void processResult(final int inputIndex) throws ExperimentException {
		
		final OWLSubClassOfAxiom conclusion = conclusions.get(inputIndex);
		
		try {
			
			final String conclName = Utils.toFileName(conclusion);
			final File outDir = new File(outputDirectory, conclName);
			outDir.mkdirs();
			int i = 0;
			for (final Set<OWLAxiom> justification : justifications) {
				
				final String fileName = String.format("%03d.owl", ++i);
				final OWLOntology outOnt = manager.createOntology(
						justification,
						IRI.create("Justification_" + i + "_for_" + conclName));
				manager.saveOntology(outOnt,
						new FunctionalSyntaxDocumentFormat(),
						new FileOutputStream(new File(outDir, fileName)));
				
			}
			
		} catch (final OWLOntologyCreationException e) {
			throw new ExperimentException(e);
		} catch (final OWLOntologyStorageException e) {
			throw new ExperimentException(e);
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		}
		
	}

}
