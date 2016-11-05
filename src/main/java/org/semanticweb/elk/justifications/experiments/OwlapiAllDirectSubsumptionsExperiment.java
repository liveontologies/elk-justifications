package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.liveontologies.owlapi.proof.OWLProofNode;
import org.liveontologies.owlapi.proof.OWLProver;
import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.owlapi.ElkProverFactory;
import org.semanticweb.elk.proofs.Inference;
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
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

public class OwlapiAllDirectSubsumptionsExperiment extends Experiment {

	public static final String STAT_NAME_AXIOMS = "OwlapiAllDirectSubsumptionsExperiment.nAxiomsInAllProofs";
	public static final String STAT_NAME_INFERENCES = "OwlapiAllDirectSubsumptionsExperiment.nInferencesInAllProofs";
	public static final String STAT_NAME_CONCLUSIONS = "OwlapiAllDirectSubsumptionsExperiment.nConclusionsInAllProofs";
	
	private static final Logger LOG = LoggerFactory.getLogger(
			OwlapiAllDirectSubsumptionsExperiment.class);

	private final File outputDirectory_;
	private final OWLOntologyManager manager_;
	private final OWLDataFactory factory;
	private final OWLProver reasoner_;
	
	private AtomicReference<Collection<? extends Set<OWLAxiom>>> justifications_ =
			new AtomicReference<Collection<? extends Set<OWLAxiom>>>();
	private AtomicReference<OWLSubClassOfAxiom> conclusion_ =
			new AtomicReference<OWLSubClassOfAxiom>();
	
	private final Set<Node<OWLClass>> done_ = Collections.newSetFromMap(
			new ConcurrentHashMap<Node<OWLClass>, Boolean>());
	private final Queue<Node<OWLClass>> toDo_ =
			new ConcurrentLinkedQueue<Node<OWLClass>>();
	private final Queue<OWLSubClassOfAxiom> conclusionsToDo_ =
			new ConcurrentLinkedQueue<OWLSubClassOfAxiom>();
	private AtomicReference<JustificationComputation<OWLProofNode, OWLAxiom>> computation_ =
			new AtomicReference<JustificationComputation<OWLProofNode, OWLAxiom>>();
	private AtomicReference<Map<String, Object>> stats_ =
			new AtomicReference<Map<String, Object>>();
	
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
			
			final ElkProverFactory proverFactory = new ElkProverFactory();
			reasoner_ = proverFactory.createReasoner(ont);
			
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
	public Record run(final Monitor monitor)
			throws ExperimentException {
		
		OWLSubClassOfAxiom conclusion = conclusionsToDo_.poll();
		if (conclusion == null) {
			populateConclusions();
			conclusion = conclusionsToDo_.poll();
			if (conclusion == null) {
				throw new ExperimentException("No more queries!");
			}
		}
		conclusion_.set(conclusion);
		
		final JustificationComputation<OWLProofNode, OWLAxiom> computation = BottomUpJustificationComputation
				.<OWLProofNode, OWLAxiom> getFactory()
				.create(new OWLExpressionInferenceSetAdapter(reasoner_.getRootOntology()), monitor);
		
		try {
			
//			long time = System.currentTimeMillis();
			long time = System.nanoTime();
			final Collection<? extends Set<OWLAxiom>> justifications =
					computation.computeJustifications(
								reasoner_.getProof(conclusion));
//			time = System.currentTimeMillis() - time;
			time = System.nanoTime() - time;
			
			justifications_.set(justifications);
			computation_.set(computation);
			
//			return new Record(time, justifications.size());
			return new Record(time/1000000.0, justifications.size());
			
		} catch (final UnsupportedEntailmentTypeException e) {
			throw new ExperimentException(e);
		}
		
	}

	@Override
	public String getInputName() throws ExperimentException {
		return conclusion_.get()==null ? "null" : conclusion_.get().toString();
	}

	@Override
	public void processResult() throws ExperimentException {
		
		try {
			
			final OWLSubClassOfAxiom conclusion = conclusion_.get();
			
			final OWLProofNode expression = reasoner_
					.getProof(conclusion);
			final OWLExpressionInferenceSetAdapter inferenceSet =
					new OWLExpressionInferenceSetAdapter(reasoner_.getRootOntology());
			
			final Set<OWLAxiom> axiomExprs =
					new HashSet<OWLAxiom>();
			final Set<OWLProofNode> lemmaExprs =
					new HashSet<OWLProofNode>();
			final Set<Inference<OWLProofNode, OWLAxiom>> inferences =
					new HashSet<Inference<OWLProofNode, OWLAxiom>>();
			
			Utils.traverseProofs(expression, inferenceSet,
					new Function<Inference<OWLProofNode, OWLAxiom>, Void>() {
						@Override
						public Void apply(
								final Inference<OWLProofNode, OWLAxiom> inf) {
							inferences.add(inf);
							return null;
						}
					},
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
			
			final Map<String, Object> stats = new HashMap<String, Object>();
			stats.put(STAT_NAME_AXIOMS, axiomExprs.size());
			stats.put(STAT_NAME_CONCLUSIONS, lemmaExprs.size());
			stats.put(STAT_NAME_INFERENCES, inferences.size());
			stats_.set(stats);
			
			if (outputDirectory_ == null) {
				return;
			}
			
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
		} catch (final UnsupportedEntailmentTypeException e) {
			throw new ExperimentException(e);
		}
		
	}

	@Override
	public String[] getStatNames() {
		final String[] statNames = new String[] {
				STAT_NAME_AXIOMS,
				STAT_NAME_CONCLUSIONS,
				STAT_NAME_INFERENCES,
			};
		final String[] otherStatNames =
				BottomUpJustificationComputation.getFactory().getStatNames();
		final String[] ret = Arrays.copyOf(statNames,
				statNames.length + otherStatNames.length);
		System.arraycopy(otherStatNames, 0, ret, statNames.length,
				otherStatNames.length);
		return ret;
	}

	@Override
	public Map<String, Object> getStatistics() {
		Map<String, Object> stats = stats_.get();
		if (stats == null) {
			stats = new HashMap<String, Object>();
		}
		final JustificationComputation<OWLProofNode, OWLAxiom> computation =
				computation_.get();
		if (computation != null) {
			stats.putAll(computation.getStatistics());
		}
		return stats;
	}

	@Override
	public void logStatistics() {
		final Map<String, Object> stats = stats_.get();
		if (stats != null && LOG.isDebugEnabled()) {
			LOG.debug("{}: number of axioms in all proofs",
					stats.get(STAT_NAME_AXIOMS));
			LOG.debug("{}: number of conclusions in all proofs",
					stats.get(STAT_NAME_CONCLUSIONS));
			LOG.debug("{}: number of inferences in all proofs",
					stats.get(STAT_NAME_INFERENCES));
		}
		final JustificationComputation<OWLProofNode, OWLAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.logStatistics();
		}
	}

	@Override
	public void resetStatistics() {
		stats_.set(null);
		final JustificationComputation<OWLProofNode, OWLAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.resetStatistics();
		}
	}

}
