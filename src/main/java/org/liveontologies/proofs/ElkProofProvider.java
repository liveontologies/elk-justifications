package org.liveontologies.proofs;

import java.io.File;
import java.util.Set;

import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.statistics.NestedStats;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owlapi.ElkReasoner;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.proofs.InternalJustifier;
import org.semanticweb.elk.proofs.InternalProof;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElkProofProvider implements
		ProofProvider<ElkAxiom, Object, Inference<Object>, ElkAxiom> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(ElkProofProvider.class);

	private final ElkReasoner reasoner_;

	public ElkProofProvider(final File ontologyFile,
			final OWLOntologyManager manager) throws ExperimentException {

		try {

			LOGGER_.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager
					.loadOntologyFromOntologyDocument(ontologyFile);
			LOGGER_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);
			LOGGER_.info("Loaded ontology: {}", ont.getOntologyID());

			reasoner_ = new ElkReasonerFactory().createReasoner(ont);

			LOGGER_.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner_.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOGGER_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

		} catch (final OWLOntologyCreationException e) {
			throw new ExperimentException(e);
		}

	}

	@NestedStats(name = "elk")
	public Reasoner getReasoner() {
		return reasoner_.getInternalReasoner();
	}

	@Override
	public JustificationCompleteProof<Object, Inference<Object>, ElkAxiom> getProof(
			final ElkAxiom query) throws ExperimentException {

		final InternalJustifier justifier = new InternalJustifier();

		return new JustificationCompleteProof<Object, Inference<Object>, ElkAxiom>() {

			@Override
			public Object getQuery() {
				return query;
			}

			@Override
			public Proof<? extends Inference<Object>> getProof()
					throws ExperimentException {
				try {
					return new InternalProof(getReasoner(), query);
				} catch (final ElkException e) {
					throw new ExperimentException(e);
				}
			}

			@Override
			public InferenceJustifier<? super Inference<Object>, ? extends Set<? extends ElkAxiom>> getJustifier() {
				return justifier;
			}

		};

	}

	@Override
	public void dispose() {
		reasoner_.dispose();
	}

}
