package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.Set;

import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.OWLExpressionInferenceSetAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
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
import org.semanticweb.owlapitools.proofs.expressions.OWLAxiomExpression;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CsvQueryOwlapiExperiment extends BaseExperiment<OWLExpression, OWLAxiom, OWLSubClassOfAxiom, OWLAxiomExpression, ExplainingOWLReasoner> {
	
	private static final Logger LOG = LoggerFactory.getLogger(
			CsvQueryOwlapiExperiment.class);

	private OWLOntologyManager manager_ = null;
	private OWLDataFactory factory_ = null;
	
	public CsvQueryOwlapiExperiment(final String[] args) throws ExperimentException {
		super(args);
	}

	private OWLOntologyManager getManager() {
		if (manager_ == null) {
			manager_ = OWLManager.createOWLOntologyManager();
		}
		return manager_;
	}

	private OWLDataFactory getFactory() {
		if (factory_ == null) {
			factory_ = getManager().getOWLDataFactory();
		}
		return factory_;
	}
	
	@Override
	protected ExplainingOWLReasoner loadAndClassifyOntology(
			final String ontologyFileName) throws ExperimentException {
		
		try {
			
			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = getManager().loadOntologyFromOntologyDocument(
					new File(ontologyFileName));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Loaded ontology: {}", ont.getOntologyID());
			
			final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			final ExplainingOWLReasoner reasoner = (ExplainingOWLReasoner) reasonerFactory.createReasoner(ont);
			
			LOG.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			return reasoner;
		} catch (final OWLOntologyCreationException e) {
			throw new ExperimentException(e);
		}
		
	}

	@Override
	protected QueryIterator<OWLSubClassOfAxiom> newQueryIterator(
			final String queryFileName) throws ExperimentException {
		return new CsvQueryIterator<OWLSubClassOfAxiom>(
				new QueryFactory<OWLSubClassOfAxiom>() {
					@Override
					public OWLSubClassOfAxiom createQuery(final String subIri,
							final String supIri) {
						return getFactory().getOWLSubClassOfAxiom(
								getFactory().getOWLClass(IRI.create(subIri)),
								getFactory().getOWLClass(IRI.create(supIri)));
					}
				}, queryFileName);
	}

	@Override
	protected OWLAxiomExpression getGoalConclusion(
			final ExplainingOWLReasoner reasoner, final OWLSubClassOfAxiom query)
					throws ExperimentException {
		try {
			
			return reasoner.getDerivedExpression(query);
			
		} catch (final UnsupportedEntailmentTypeException e) {
			throw new ExperimentException(e);
		} catch (final ProofGenerationException e) {
			throw new ExperimentException(e);
		}
	}

	@Override
	protected InferenceSet<OWLExpression, OWLAxiom> newInferenceSet(
			final ExplainingOWLReasoner reasoner, final OWLAxiomExpression goal)
					throws ExperimentException {
		return new OWLExpressionInferenceSetAdapter();
	}

	@Override
	protected void saveJustifications(final OWLSubClassOfAxiom query,
			final Collection<? extends Set<OWLAxiom>> justifications,
					final File outputDirectory) throws ExperimentException {
		
		try {
			
			final String conclName = Utils.toFileName(query);
			final File outDir = new File(outputDirectory, conclName);
			outDir.mkdirs();
			int i = 0;
			for (final Set<OWLAxiom> justification : justifications) {
				
				final String fileName = String.format("%03d.owl", ++i);
				final OWLOntology outOnt = getManager().createOntology(
						justification,
						IRI.create("Justification_" + i + "_for_" + conclName));
				getManager().saveOntology(outOnt,
						new FunctionalSyntaxDocumentFormat(),
						new FileOutputStream(new File(outDir, fileName)));
				
			}
			
		} catch (final OWLOntologyCreationException e) {
			throw new ExperimentException(e);
		} catch (final OWLOntologyStorageException e) {
			throw new ExperimentException(e);
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final UnsupportedEntailmentTypeException e) {
			throw new ExperimentException(e);
		}
		
	}
	
}
