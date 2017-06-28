package org.liveontologies.pinpointing.experiments;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

import org.liveontologies.pinpointing.Utils;
import org.liveontologies.proofs.TracingInferenceJustifier;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkObject;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElkJustificationExperiment extends
		ReasonerJustificationExperiment<Conclusion, ElkAxiom, Reasoner> {

	private static final Logger LOG = LoggerFactory
			.getLogger(ElkJustificationExperiment.class);

	@Override
	protected Reasoner loadAndClassifyOntology(final String ontologyFileName)
			throws ExperimentException {

		InputStream ontologyIS = null;

		try {

			ontologyIS = new FileInputStream(ontologyFileName);

			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory()
					.createReasoner(loader);

			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

			return reasoner;
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final ElkException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(ontologyIS);
		}

	}

	@Override
	protected Conclusion decodeQuery(final String query)
			throws ExperimentException {
		return CsvQueryDecoder.decode(query,
				new CsvQueryDecoder.Factory<Conclusion>() {

					@Override
					public Conclusion createQuery(final String subIri,
							final String supIri) {

						final ElkObject.Factory factory = getReasoner()
								.getElkFactory();
						final ElkAxiom query = factory.getSubClassOfAxiom(
								factory.getClass(new ElkFullIri(subIri)),
								factory.getClass(new ElkFullIri(supIri)));

						try {
							return Utils
									.getFirstDerivedConclusionForSubsumption(
											getReasoner(), query);
						} catch (final ElkException e) {
							throw new RuntimeException(e);
						}
					}

				});
	}

	@Override
	protected Proof<Conclusion> newProof(final Conclusion query)
			throws ExperimentException {
		try {

			return getReasoner().explainConclusion(query);

		} catch (final ElkException e) {
			throw new ExperimentException(e);
		}
	}

	@Override
	protected InferenceJustifier<Conclusion, ? extends Set<? extends ElkAxiom>> newJustifier()
			throws ExperimentException {
		return TracingInferenceJustifier.INSTANCE;
	}

}
