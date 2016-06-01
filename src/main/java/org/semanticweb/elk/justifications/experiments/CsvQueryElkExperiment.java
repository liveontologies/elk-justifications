package org.semanticweb.elk.justifications.experiments;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.semanticweb.elk.exceptions.ElkException;
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

public class CsvQueryElkExperiment extends
		BaseExperiment<Conclusion, ElkAxiom, ElkSubClassOfAxiom, ClassConclusion, Reasoner> {

	private static final Logger LOG = LoggerFactory
			.getLogger(CsvQueryElkExperiment.class);

	private final ElkObject.Factory factory_;

	public CsvQueryElkExperiment(final String[] args)
			throws ExperimentException {
		super(args);

		factory_ = new ElkObjectBaseFactory();

	}

	@Override
	protected Reasoner loadAndClassifyOntology(final String ontologyFileName)
			throws ExperimentException {

		InputStream ontologyIS = null;

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
					(System.currentTimeMillis() - start) / 1000.0);

			return reasoner;
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
				} catch (final IOException e) {
				}
			}
		}

	}

	@Override
	protected QueryIterator<ElkSubClassOfAxiom> newQueryIterator(
			final String queryFileName) throws ExperimentException {
		return new CsvQueryIterator<ElkSubClassOfAxiom>(
				new QueryFactory<ElkSubClassOfAxiom>() {
					@Override
					public ElkSubClassOfAxiom createQuery(final String subIri,
							final String supIri) {
						return factory_.getSubClassOfAxiom(
								factory_.getClass(new ElkFullIri(subIri)),
								factory_.getClass(new ElkFullIri(supIri)));
					}
				}, queryFileName);
	}

	@Override
	protected ClassConclusion getGoalConclusion(final Reasoner reasoner,
			final ElkSubClassOfAxiom query) throws ExperimentException {
		try {

			return Utils.getFirstDerivedConclusionForSubsumption(reasoner,
					query);

		} catch (final ElkException e) {
			throw new ExperimentException(e);
		}
	}

	@Override
	protected InferenceSet<Conclusion, ElkAxiom> newInferenceSet(
			final Reasoner reasoner, final ClassConclusion goal)
					throws ExperimentException {
		try {

			return new TracingInferenceSetInferenceSetAdapter(
					reasoner.explainConclusion(goal));

		} catch (final ElkException e) {
			throw new ExperimentException(e);
		}
	}

}
