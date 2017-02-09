package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.liveontologies.puli.ProofNode;
import org.liveontologies.puli.ProofNodes;
import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.owlapi.ElkProver;
import org.semanticweb.elk.owlapi.ElkProverFactory;
import org.semanticweb.elk.proofs.Inference;
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
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

public class OwlapiExperiment extends Experiment {

	public static final String STAT_NAME_AXIOMS = "OwlapiExperiment.nAxiomsInAllProofs";
	public static final String STAT_NAME_INFERENCES = "OwlapiExperiment.nInferencesInAllProofs";
	public static final String STAT_NAME_CONCLUSIONS = "OwlapiExperiment.nConclusionsInAllProofs";
	
	private static final Logger LOG = LoggerFactory.getLogger(
			OwlapiExperiment.class);

	private final File outputDirectory_;
	private final OWLOntologyManager manager_;
	private final ElkProver reasoner_;
	private final List<OWLSubClassOfAxiom> conclusions_;
	
	private AtomicReference<Collection<? extends Set<OWLAxiom>>> justifications_ =
			new AtomicReference<Collection<? extends Set<OWLAxiom>>>();
	private AtomicInteger inputIndex_ = new AtomicInteger(0);
	private AtomicReference<OWLSubClassOfAxiom> conclusion_ =
			new AtomicReference<OWLSubClassOfAxiom>();
	private AtomicReference<JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom>> computation_ =
			new AtomicReference<JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom>>();
	private AtomicReference<Map<String, Object>> stats_ =
			new AtomicReference<Map<String, Object>>();
	
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
	public Record run(final Monitor monitor)
			throws ExperimentException {
		try {
			final OWLSubClassOfAxiom conclusion = conclusions_.get(
					inputIndex_.getAndIncrement());
			final JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom> computation = BottomUpJustificationComputation
					.<ProofNode<OWLAxiom>, OWLAxiom> getFactory()
					.create(new OWLExpressionInferenceSetAdapter(reasoner_.getRootOntology()), monitor);
					
//			long time = System.currentTimeMillis();
			long time = System.nanoTime();
			final ProofNode<OWLAxiom> proofNode = ProofNodes
					.create(reasoner_.getProof(conclusion), conclusion);
			final Collection<? extends Set<OWLAxiom>> justifications =
					computation.computeJustifications(proofNode);
//			time = System.currentTimeMillis() - time;
			time = System.nanoTime() - time;
			conclusion_.set(conclusion);
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
			
			final ProofNode<OWLAxiom> expression = ProofNodes
					.create(reasoner_.getProof(conclusion), conclusion);
			final OWLExpressionInferenceSetAdapter inferenceSet =
					new OWLExpressionInferenceSetAdapter(reasoner_.getRootOntology());
			
			final Set<OWLAxiom> axiomExprs =
					new HashSet<OWLAxiom>();
			final Set<ProofNode<OWLAxiom>> lemmaExprs =
					new HashSet<ProofNode<OWLAxiom>>();
			final Set<Inference<ProofNode<OWLAxiom>, OWLAxiom>> inferences =
					new HashSet<Inference<ProofNode<OWLAxiom>, OWLAxiom>>();
			
			Utils.traverseProofs(expression, inferenceSet,
					new Function<Inference<ProofNode<OWLAxiom>, OWLAxiom>, Void>() {
						@Override
						public Void apply(
								final Inference<ProofNode<OWLAxiom>, OWLAxiom> inf) {
							inferences.add(inf);
							return null;
						}
					},
					new Function<ProofNode<OWLAxiom>, Void>(){
						@Override
						public Void apply(final ProofNode<OWLAxiom> expr) {
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
		final JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom> computation =
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
		final JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.logStatistics();
		}
	}

	@Override
	public void resetStatistics() {
		stats_.set(null);
		final JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.resetStatistics();
		}
	}

}
