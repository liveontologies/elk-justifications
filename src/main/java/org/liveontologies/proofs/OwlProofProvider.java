package org.liveontologies.proofs;

import java.io.File;
import java.util.Set;

import org.liveontologies.owlapi.proof.OWLProver;
import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceJustifiers;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.statistics.NestedStats;
import org.semanticweb.elk.owlapi.ElkProverFactory;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OwlProofProvider implements
		ProofProvider<OWLAxiom, OWLAxiom, Inference<OWLAxiom>, OWLAxiom> {

	private static final Logger LOG = LoggerFactory
			.getLogger(OwlProofProvider.class);

	private final OWLProver reasoner_;

	public OwlProofProvider(final File ontologyFile,
			final OWLOntologyManager manager) throws ExperimentException {

		try {

			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager
					.loadOntologyFromOntologyDocument(ontologyFile);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);
			LOG.info("Loaded ontology: {}", ont.getOntologyID());

			reasoner_ = new ElkProverFactory().createReasoner(ont);

			LOG.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner_.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

		} catch (final OWLOntologyCreationException e) {
			throw new ExperimentException(e);
		}

	}

	@NestedStats(name = "elk")
	public OWLProver getReasoner() {
		return reasoner_;
	}

	@Override
	public JustificationCompleteProof<OWLAxiom, Inference<OWLAxiom>, OWLAxiom> getProof(
			final OWLAxiom query) throws ExperimentException {

		final InferenceJustifier<Inference<OWLAxiom>, ? extends Set<? extends OWLAxiom>> justifier = InferenceJustifiers
				.justifyAssertedInferences();

		return new JustificationCompleteProof<OWLAxiom, Inference<OWLAxiom>, OWLAxiom>() {

			@Override
			public OWLAxiom getQuery() {
				return query;
			}

			@Override
			public Proof<? extends Inference<OWLAxiom>> getProof() {
				return reasoner_.getProof(query);
			}

			@Override
			public InferenceJustifier<? super Inference<OWLAxiom>, ? extends Set<? extends OWLAxiom>> getJustifier() {
				return justifier;
			}

		};

	}

	@Override
	public void dispose() {
		reasoner_.dispose();
	}

}