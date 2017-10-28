package org.liveontologies.proofs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import org.liveontologies.pinpointing.Utils;
import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.statistics.NestedStats;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.InternalJustifier;
import org.semanticweb.elk.proofs.InternalProof;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElkProofProvider implements
		ProofProvider<ElkAxiom, Object, Inference<Object>, ElkAxiom> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(ElkProofProvider.class);

	private final Reasoner reasoner_;

	public ElkProofProvider(final File ontologyFile)
			throws ExperimentException {

		InputStream ontologyIS = null;

		try {

			ontologyIS = new FileInputStream(ontologyFile);

			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			reasoner_ = new ReasonerFactory().createReasoner(loader);

			LOGGER_.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner_.getTaxonomy();
			LOGGER_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final ElkException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(ontologyIS);
		}

	}

	@NestedStats(name = "elk")
	public Reasoner getReasoner() {
		return reasoner_;
	}

	@Override
	public JustificationCompleteProof<Object, Inference<Object>, ElkAxiom> getProof(
			final ElkAxiom query) throws ExperimentException {

		try {
			final InternalProof proof = new InternalProof(reasoner_, query);
			final InternalJustifier justifier = new InternalJustifier();

			return new BaseJustificationCompleteProof<Object, Inference<Object>, ElkAxiom>(
					query, proof, justifier);
		} catch (final ElkException e) {
			throw new ExperimentException(e);
		}
	}

	@Override
	public void dispose() {
		for (;;) {
			try {
				if (!reasoner_.shutdown())
					throw new RuntimeException("Failed to shut down ELK!");
				break;
			} catch (InterruptedException e) {
				continue;
			}
		}
	}

}
