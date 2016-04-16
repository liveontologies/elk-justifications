package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
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

public class CsvQueryElkExperiment extends Experiment {
	
	private static final Logger LOG = LoggerFactory.getLogger(
			CsvQueryElkExperiment.class);

	private final File outputDirectory_;
	private final Reasoner reasoner_;
	private final ElkObject.Factory factory_;
	private final String conclusionsFileName_;
	
	private BufferedReader conclusionReader_ = null;
	private final Queue<ElkSubClassOfAxiom> conclusionsToDo_ =
			new ConcurrentLinkedQueue<ElkSubClassOfAxiom>();
	private AtomicReference<Collection<Set<ElkAxiom>>> justifications_ =
			new AtomicReference<Collection<Set<ElkAxiom>>>();
	private AtomicReference<ElkSubClassOfAxiom> conclusion_ =
			new AtomicReference<ElkSubClassOfAxiom>();

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
			
			long time = System.currentTimeMillis();
			final ClassConclusion expression =
					reasoner_.getConclusion(conclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner_.explainConclusion(expression));
			final JustificationComputation<Conclusion, ElkAxiom> computation =
					BottomUpJustificationComputation
					.<Conclusion, ElkAxiom> getFactory()
					.create(inferenceSet, monitor);
			final Collection<Set<ElkAxiom>> justifications =
					computation.computeJustifications(expression);
			time = System.currentTimeMillis() - time;
			
			justifications_.set(justifications);
			computation.logStatistics();
			
			return new Record(time, justifications.size());
			
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

}
