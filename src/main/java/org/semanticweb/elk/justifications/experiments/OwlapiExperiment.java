package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
	
	private static final Logger LOG = LoggerFactory.getLogger(
			OwlapiExperiment.class);

	private final File outputDirectory_;
	private final OWLOntologyManager manager_;
	private final ExplainingOWLReasoner reasoner_;
	private final List<OWLSubClassOfAxiom> conclusions_;
	
	private AtomicReference<Collection<Set<OWLAxiom>>> justifications_ =
			new AtomicReference<Collection<Set<OWLAxiom>>>();
	
	public OwlapiExperiment(final String[] args) throws ExperimentException {
		super(args);
		
		if (args.length < 2) {
			throw new ExperimentException("Insufficient arguments!");
		}
		
		final String ontologyFileName = args[0];
		final String conclusionsFileName = args[1];
		if (args.length >= 3) {
			outputDirectory_ = new File(args[2]);
			if (!Utils.cleanDir(outputDirectory_)) {
				LOG.error("Could not prepare the output directory!");
				System.exit(2);
			}
		} else {
			outputDirectory_ = null;
		}
		
		manager_ = OWLManager.createOWLOntologyManager();
		
		try {
			
			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager_.loadOntologyFromOntologyDocument(
					new File(ontologyFileName));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Loaded ontology: {}", ont.getOntologyID());
			
			LOG.info("Loading conclusions ...");
			start = System.currentTimeMillis();
			final OWLOntology conclusionsOnt =
					manager_.loadOntologyFromOntologyDocument(
							new File(conclusionsFileName));
			conclusions_ = new ArrayList<OWLSubClassOfAxiom>(
					conclusionsOnt.getAxioms(AxiomType.SUBCLASS_OF));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Number of conclusions: {}", conclusions_.size());
			
			final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			reasoner_ =
					(ExplainingOWLReasoner) reasonerFactory.createReasoner(ont);
			
			LOG.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner_.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
		} catch (final OWLOntologyCreationException e) {
			throw new ExperimentException(e);
		}
		
	}

	private AtomicInteger inputIndex_ = new AtomicInteger(0);
	private AtomicReference<OWLSubClassOfAxiom> conclusion_ =
			new AtomicReference<OWLSubClassOfAxiom>();
	
	@Override
	public void init() throws ExperimentException {
		inputIndex_.set(0);
		conclusion_.set(null);
	}

	@Override
	public boolean hasNext() {
		return inputIndex_.get() < conclusions_.size();
	}

	@Override
	public Record run() throws ExperimentException, InterruptedException {
		try {
			final OWLSubClassOfAxiom conclusion = conclusions_.get(
					inputIndex_.getAndIncrement());
			final JustificationComputation<OWLExpression, OWLAxiom> computation =
					new BottomUpJustificationComputation<OWLExpression, OWLAxiom>(
								new OWLExpressionInferenceSetAdapter());
			long time = System.currentTimeMillis();
			final Collection<Set<OWLAxiom>> justifications =
					computation.computeJustifications(
								reasoner_.getDerivedExpression(conclusion));
			time = System.currentTimeMillis() - time;
			conclusion_.set(conclusion);
			justifications_.set(justifications);
			return new Record(time, justifications.size());
		} catch (final UnsupportedEntailmentTypeException e) {
			throw new ExperimentException(e);
		} catch (final ProofGenerationException e) {
			throw new ExperimentException(e);
		}
	}

	@Override
	public String getInputName() throws ExperimentException {
		return conclusion_.get()==null ? "null" : conclusion_.get().toString();
	}

	@Override
	public void processResult() throws ExperimentException {
		
		if (outputDirectory_ == null) {
			return;
		}
		
		try {
			
			final String conclName = Utils.toFileName(conclusion_.get());
			final File outDir = new File(outputDirectory_, conclName);
			outDir.mkdirs();
			int i = 0;
			for (final Set<OWLAxiom> justification : justifications_.get()) {
				
				final String fileName = String.format("%03d.owl", ++i);
				final OWLOntology outOnt = manager_.createOntology(
						justification,
						IRI.create("Justification_" + i + "_for_" + conclName));
				manager_.saveOntology(outOnt,
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
