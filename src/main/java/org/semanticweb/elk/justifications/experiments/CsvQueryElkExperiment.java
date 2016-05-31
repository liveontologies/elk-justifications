package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkObject;
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

import com.google.common.base.Function;

public class CsvQueryElkExperiment extends Experiment {

	public static final String STAT_NAME_AXIOMS = "CsvQueryElkExperiment.nAxiomsInAllProofs";
	public static final String STAT_NAME_INFERENCES = "CsvQueryElkExperiment.nInferencesInAllProofs";
	public static final String STAT_NAME_CONCLUSIONS = "CsvQueryElkExperiment.nConclusionsInAllProofs";
	
	private static final Logger LOG = LoggerFactory.getLogger(
			CsvQueryElkExperiment.class);

	private final File outputDirectory_;
	private final Reasoner reasoner_;
	private final ElkObject.Factory factory_;
	private final String conclusionsFileName_;
	
	private BufferedReader conclusionReader_ = null;
	private final Queue<ElkSubClassOfAxiom> conclusionsToDo_ =
			new ConcurrentLinkedQueue<ElkSubClassOfAxiom>();
	private AtomicReference<Collection<? extends Set<ElkAxiom>>> justifications_ =
			new AtomicReference<Collection<? extends Set<ElkAxiom>>>();
	private AtomicReference<ElkSubClassOfAxiom> conclusion_ =
			new AtomicReference<ElkSubClassOfAxiom>();
	private AtomicReference<JustificationComputation<Conclusion, ElkAxiom>> computation_ =
			new AtomicReference<JustificationComputation<Conclusion,ElkAxiom>>();
	private AtomicReference<Map<String, Object>> stats_ =
			new AtomicReference<Map<String, Object>>();

	public CsvQueryElkExperiment(final String[] args) throws ExperimentException {
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
		
		factory_ = new ElkObjectBaseFactory();
		
		InputStream ontologyIS = null;
		
		try {
			
			ontologyIS = new FileInputStream(ontologyFileName);
			
			final AxiomLoader ontologyLoader = new Owl2StreamLoader(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			reasoner_ = new ReasonerFactory().createReasoner(
					ontologyLoader, new RestartingStageExecutor());
			
			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner_.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final ElkInconsistentOntologyException e) {
			throw new ExperimentException(e);
		} catch (final ElkException e) {
			throw new ExperimentException(e);
		} finally {
			if (ontologyIS != null) {
				try {
					ontologyIS.close();
				} catch (final IOException e) {}
			}
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
		
		final ElkSubClassOfAxiom conclusion = factory_.getSubClassOfAxiom(
				factory_.getClass(new ElkFullIri(subIri)),
				factory_.getClass(new ElkFullIri(supIri)));
		
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
			
			ElkSubClassOfAxiom conclusion = conclusionsToDo_.poll();
			if (conclusion == null) {
				enqueueNextConclusion();
				conclusion = conclusionsToDo_.poll();
				if (conclusion == null) {
					throw new ExperimentException("No more queries!");
				}
			}
			conclusion_.set(conclusion);
			
//			long time = System.currentTimeMillis();
			long time = System.nanoTime();
			final ClassConclusion expression = Utils
					.getFirstDerivedConclusionForSubsumption(reasoner_,
							conclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner_.explainConclusion(expression));
			final JustificationComputation<Conclusion, ElkAxiom> computation =
					BottomUpJustificationComputation
					.<Conclusion, ElkAxiom> getFactory()
					.create(inferenceSet, monitor);
			final Collection<? extends Set<ElkAxiom>> justifications =
					computation.computeJustifications(expression);
//			time = System.currentTimeMillis() - time;
			time = System.nanoTime() - time;
			
			justifications_.set(justifications);
			computation_.set(computation);
			
//			return new Record(time, justifications.size());
			return new Record(time/1000000.0, justifications.size());
			
		} catch (final ElkException e) {
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
		
		final ElkSubClassOfAxiom conclusion = conclusion_.get();
		
		try {
			
			final ClassConclusion expression = Utils
					.getFirstDerivedConclusionForSubsumption(reasoner_,
							conclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner_.explainConclusion(expression));
			
			final Set<ElkAxiom> axiomExprs =
					new HashSet<ElkAxiom>();
			final Set<Conclusion> lemmaExprs =
					new HashSet<Conclusion>();
			final Set<Inference<Conclusion, ElkAxiom>> inferences =
					new HashSet<Inference<Conclusion, ElkAxiom>>();
			
			Utils.traverseProofs(expression, inferenceSet,
					new Function<Inference<Conclusion, ElkAxiom>, Void>() {
				@Override
				public Void apply(
						final Inference<Conclusion, ElkAxiom> inf) {
							inferences.add(inf);
							return null;
						}
					},
					new Function<Conclusion, Void>(){
						@Override
						public Void apply(final Conclusion expr) {
							lemmaExprs.add(expr);
							return null;
						}
					},
					new Function<ElkAxiom, Void>(){
						@Override
						public Void apply(final ElkAxiom axiom) {
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
			
		} catch (final ElkException e) {
			throw new ExperimentException(e);
		}
		
//		if (outputDirectory_ == null) {
//			return;
//		}
//		
//		try {
//			
//			final String conclName = Utils.toFileName(conclusion_.get());
//			final File outDir = new File(outputDirectory_, conclName);
//			outDir.mkdirs();
//			int i = 0;
//			for (final Set<ElkAxiom> justification : justifications_.get()) {
//				
//				final String fileName = String.format("%03d.owl", ++i);
//				final OWLOntology outOnt = manager_.createOntology(
//						justification,
//						IRI.create("Justification_" + i + "_for_" + conclName));
//				manager_.saveOntology(outOnt,
//						new FunctionalSyntaxDocumentFormat(),
//						new FileOutputStream(new File(outDir, fileName)));
//				
//			}
//			
//		} catch (final OWLOntologyCreationException e) {
//			throw new ExperimentException(e);
//		} catch (final OWLOntologyStorageException e) {
//			throw new ExperimentException(e);
//		} catch (final FileNotFoundException e) {
//			throw new ExperimentException(e);
//		}
		
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
		final JustificationComputation<Conclusion, ElkAxiom> computation =
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
		final JustificationComputation<Conclusion, ElkAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.logStatistics();
		}
	}

	@Override
	public void resetStatistics() {
		stats_.set(null);
		final JustificationComputation<Conclusion, ElkAxiom> computation =
				computation_.get();
		if (computation != null) {
			computation.resetStatistics();
		}
	}

}
