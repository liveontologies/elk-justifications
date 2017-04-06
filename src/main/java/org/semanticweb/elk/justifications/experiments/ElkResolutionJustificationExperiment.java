package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkObject;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.TracingInferenceJustifier;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class ElkResolutionJustificationExperiment
		extends ResolutionJustificationExperiment<Conclusion, ElkAxiom> {

	private static final Logger LOG = LoggerFactory
			.getLogger(ElkResolutionJustificationExperiment.class);

	public static final String ONTOLOGY_OPT = "ontology";

	private Reasoner reasoner_;

	@Override
	protected void addArguments(final ArgumentParser parser) {
		parser.addArgument(ONTOLOGY_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("ontology file");
	}

	@Override
	protected void init(final Namespace options) throws ExperimentException {
		reasoner_ = loadAndClassifyOntology(options.<File> get(ONTOLOGY_OPT));
	}

	protected Reasoner loadAndClassifyOntology(final File ontologyFileName)
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

	public Reasoner getReasoner() {
		return reasoner_;
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
	protected InferenceSet<Conclusion> newInferenceSet(final Conclusion query)
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
