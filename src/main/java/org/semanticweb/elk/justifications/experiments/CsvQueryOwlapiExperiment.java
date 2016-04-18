package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.proofs.Inference;
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

import com.google.common.base.Function;

public class CsvQueryOwlapiExperiment extends Experiment {

	public static final String STAT_NAME_AXIOMS = "CsvQueryOwlapiExperiment.nAxiomsInAllProofs";
	public static final String STAT_NAME_INFERENCES = "CsvQueryOwlapiExperiment.nInferencesInAllProofs";
	public static final String STAT_NAME_CONCLUSIONS = "CsvQueryOwlapiExperiment.nConclusionsInAllProofs";
	
	private static final Logger LOG = LoggerFactory.getLogger(
			CsvQueryOwlapiExperiment.class);

	private final File outputDirectory_;
	private final OWLOntologyManager manager_;
	private final ExplainingOWLReasoner reasoner_;
	private final OWLDataFactory factory_;
	private final String conclusionsFileName_;
	
	private BufferedReader conclusionReader_ = null;
	private final Queue<OWLSubClassOfAxiom> conclusionsToDo_ =
			new ConcurrentLinkedQueue<OWLSubClassOfAxiom>();
	private AtomicReference<Collection<Set<OWLAxiom>>> justifications_ =
			new AtomicReference<Collection<Set<OWLAxiom>>>();
	private AtomicReference<OWLSubClassOfAxiom> conclusion_ =
			new AtomicReference<OWLSubClassOfAxiom>();
	private AtomicReference<JustificationComputation<OWLExpression, OWLAxiom>> computation_ =
			new AtomicReference<JustificationComputation<OWLExpression, OWLAxiom>>();
	private AtomicReference<Map<String, Object>> stats_ =
			new AtomicReference<Map<String, Object>>();
	
	public CsvQueryOwlapiExperiment(final String[] args) throws ExperimentException {
		super(args);
		
		if (args.length < 2) {
			throw new ExperimentException("Insufficient arguments!");
		}
		
		final String ontologyFileName = args[0];
		conclusionsFileName_ = args[1];
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
		factory_ = manager_.getOWLDataFactory();
		
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
	
	@Override
	public void init() throws ExperimentException {
		conclusion_.set(null);
		
		try {
			if (conclusionReader_ != null) {
				conclusionReader_.close();
			}
			conclusionReader_ =
					new BufferedReader(new FileReader(conclusionsFileName_));
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final IOException e) {
			throw new ExperimentException(e);
		}
	}

	private void enqueueNextConclusion() throws IOException {
		
		final String line = conclusionReader_.readLine();
		if (line == null) {
			return;
		}
		
		final String[] columns = line.split(",");
		if (columns.length < 2) {
			return;
		}
		
		final String subIri = strip(columns[0]);
		final String supIri = strip(columns[1]);
		
		final OWLSubClassOfAxiom conclusion = factory_.getOWLSubClassOfAxiom(
				factory_.getOWLClass(IRI.create(subIri)),
				factory_.getOWLClass(IRI.create(supIri)));
		
		conclusionsToDo_.add(conclusion);
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

	@Override
	public boolean hasNext() {
		if (!conclusionsToDo_.isEmpty()) {
			return true;
		}
		try {
			enqueueNextConclusion();
			return !conclusionsToDo_.isEmpty();
		} catch (final IOException e) {
			LOG.error("Input error! ", e);
			return false;
		}
	}

	@Override
	public Record run(final Monitor monitor)
			throws ExperimentException {
		try {
			
			OWLSubClassOfAxiom conclusion = conclusionsToDo_.poll();
			if (conclusion == null) {
				enqueueNextConclusion();
				conclusion = conclusionsToDo_.poll();
				if (conclusion == null) {
					throw new ExperimentException("No more queries!");
				}
			}
			conclusion_.set(conclusion);
			
			final JustificationComputation<OWLExpression, OWLAxiom> computation = BottomUpJustificationComputation
					.<OWLExpression, OWLAxiom> getFactory()
					.create(new OWLExpressionInferenceSetAdapter(), monitor);
					
			long time = System.currentTimeMillis();
			final Collection<Set<OWLAxiom>> justifications =
					computation.computeJustifications(
								reasoner_.getDerivedExpression(conclusion));
			time = System.currentTimeMillis() - time;
			justifications_.set(justifications);
			computation_.set(computation);
			return new Record(time, justifications.size());
		} catch (final UnsupportedEntailmentTypeException e) {
			throw new ExperimentException(e);
		} catch (final ProofGenerationException e) {
			throw new ExperimentException(e);
		} catch (final IOException e) {
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
			
			final OWLAxiomExpression expression = reasoner_
					.getDerivedExpression(conclusion);
			final OWLExpressionInferenceSetAdapter inferenceSet =
					new OWLExpressionInferenceSetAdapter();
			
			final Set<OWLAxiom> axiomExprs =
					new HashSet<OWLAxiom>();
			final Set<OWLExpression> lemmaExprs =
					new HashSet<OWLExpression>();
			final Set<Inference<OWLExpression, OWLAxiom>> inferences =
					new HashSet<Inference<OWLExpression, OWLAxiom>>();
			
			Utils.traverseProofs(expression, inferenceSet,
					new Function<Inference<OWLExpression, OWLAxiom>, Void>() {
						@Override
						public Void apply(
								final Inference<OWLExpression, OWLAxiom> inf) {
							inferences.add(inf);
							return null;
						}
					},
					new Function<OWLExpression, Void>(){
						@Override
						public Void apply(final OWLExpression expr) {
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
		} catch (final ProofGenerationException e) {
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
		final JustificationComputation<OWLExpression, OWLAxiom> computation =
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
		final JustificationComputation<OWLExpression, OWLAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.logStatistics();
		}
	}

	@Override
	public void resetStatistics() {
		stats_.set(null);
		final JustificationComputation<OWLExpression, OWLAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.resetStatistics();
		}
	}

}
