package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.proofs.adapters.OWLExpressionInferenceSetAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OwlapiAllDirectSubsumptionsExperiment extends Experiment {
	
	private static final Logger LOG = LoggerFactory.getLogger(
			OwlapiAllDirectSubsumptionsExperiment.class);

	private final File outputDirectory_;
	private final OWLOntologyManager manager_;
	private final OWLDataFactory factory;
	private final ExplainingOWLReasoner reasoner_;
	
	private AtomicReference<Collection<Set<OWLAxiom>>> justifications_ =
			new AtomicReference<Collection<Set<OWLAxiom>>>();
	private AtomicReference<OWLSubClassOfAxiom> conclusion_ =
			new AtomicReference<OWLSubClassOfAxiom>();
	
	private final Set<Node<OWLClass>> done_ = Collections.newSetFromMap(
			new ConcurrentHashMap<Node<OWLClass>, Boolean>());
	private final Queue<Node<OWLClass>> toDo_ =
			new ConcurrentLinkedQueue<Node<OWLClass>>();
	private final Queue<OWLSubClassOfAxiom> conclusionsToDo_ =
			new ConcurrentLinkedQueue<OWLSubClassOfAxiom>();
	
	public OwlapiAllDirectSubsumptionsExperiment(final String[] args)
			throws ExperimentException {
		super(args);
		
		if (args.length < 1) {
			throw new ExperimentException("Insufficient arguments!");
		}
		
		final String ontologyFileName = args[0];
		if (args.length >= 2) {
			outputDirectory_ = new File(args[1]);
			if (!Utils.cleanDir(outputDirectory_)) {
				LOG.error("Could not prepare the output directory!");
				System.exit(2);
			}
		} else {
			outputDirectory_ = null;
		}
		
		manager_ = OWLManager.createOWLOntologyManager();
		factory = manager_.getOWLDataFactory();
		
		try {
			
			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager_.loadOntologyFromOntologyDocument(
					new File(ontologyFileName));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Loaded ontology: {}", ont.getOntologyID());
			
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
	
	private void populateConclusions() {
		
		while (conclusionsToDo_.isEmpty()) {
			
			final Node<OWLClass> node = toDo_.poll();
			if (node == null) {
				return;
			}
			if (done_.add(node)) {
				
				if (!node.isBottomNode()) {
					
					// Put the equivalences into the conclusion queue.
					final ArrayList<OWLClass> entities =
							new ArrayList<OWLClass>(node.getEntities());
					for (int i = 0; i < entities.size() - 1; i++) {
						for (int j = 1; j < entities.size(); j++) {
							final OWLClass first = entities.get(i);
							final OWLClass second = entities.get(j);
							
							if (first.equals(second)) {
								continue;
							}
							
							if (!second.equals(factory.getOWLThing())
									&& !first.equals(factory.getOWLNothing())) {
								conclusionsToDo_.add(factory
										.getOWLSubClassOfAxiom(first, second));
							}

							if (!first.equals(factory.getOWLThing())
									&& !second.equals(factory.getOWLNothing())) {
								conclusionsToDo_.add(factory
										.getOWLSubClassOfAxiom(second, first));
							}
							
						}
					}
					
					// Put the subclasses into the conclusion queue.
					for (final OWLClass sup : entities) {
						if (sup.equals(factory.getOWLThing())) {
							continue;
						}
						for (final Node<OWLClass> subNode : reasoner_.getSubClasses(
								node.getRepresentativeElement(), true)) {
							if (subNode.isBottomNode()) {
								continue;
							}
							for (final OWLClass sub : subNode) {
								conclusionsToDo_.add(factory
										.getOWLSubClassOfAxiom(sub, sup));
							}
						}
					}
					
				}
				
				// Queue up the subnodes.
				for (final Node<OWLClass> subNode : reasoner_.getSubClasses(
						node.getRepresentativeElement(), true)) {
					toDo_.add(subNode);
				}
				
			}
			
		}
		
	}
	
	@Override
	public void init() throws ExperimentException {
		
		done_.clear();
		toDo_.clear();
		conclusionsToDo_.clear();
		
		toDo_.add(reasoner_.getTopClassNode());
		
		conclusion_.set(null);
		justifications_.set(null);
	}

	@Override
	public boolean hasNext() {
		if (!conclusionsToDo_.isEmpty()) {
			return true;
		}
		populateConclusions();
		return !conclusionsToDo_.isEmpty();
	}

	@Override
	public Record run() throws ExperimentException, InterruptedException {
		
		OWLSubClassOfAxiom conclusion = conclusionsToDo_.poll();
		if (conclusion == null) {
			populateConclusions();
			conclusion = conclusionsToDo_.poll();
			if (conclusion == null) {
				throw new ExperimentException("No more queries!");
			}
		}
		
		final BottomUpJustificationComputation<OWLExpression, OWLAxiom> computation =
				new BottomUpJustificationComputation<OWLExpression, OWLAxiom>(
							new OWLExpressionInferenceSetAdapter());
		
		try {
			
			long time = System.currentTimeMillis();
			final Collection<Set<OWLAxiom>> justifications =
					computation.computeJustifications(
								reasoner_.getDerivedExpression(conclusion));
			time = System.currentTimeMillis() - time;
			
			conclusion_.set(conclusion);
			justifications_.set(justifications);
			computation.logStatistics();
			
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
