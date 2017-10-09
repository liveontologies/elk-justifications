package org.liveontologies.pinpointing;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.liveontologies.owlapi.proof.OWLProver;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceJustifiers;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.semanticweb.elk.owlapi.ElkProverFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public abstract class OwlJustificationComputationTest extends
		BaseJustificationComputationTest<OWLAxiom, Inference<OWLAxiom>, OWLAxiom> {

	private static final OWLOntologyManager OWL_MANAGER_ = OWLManager
			.createOWLOntologyManager();

	private final OWLProver prover_;

	public OwlJustificationComputationTest(
			final MinimalSubsetsFromProofs.Factory<OWLAxiom, Inference<OWLAxiom>, OWLAxiom> factory,
			final File ontoFile, final Map<File, File[]> entailFilesPerJustFile)
			throws OWLOntologyCreationException {
		super(factory, ontoFile, entailFilesPerJustFile);

		this.prover_ = new ElkProverFactory().createReasoner(
				OWL_MANAGER_.loadOntologyFromOntologyDocument(ontoFile));

	}

	@Override
	public Set<? extends Set<? extends OWLAxiom>> getActualJustifications(
			final File entailFile) throws OWLOntologyCreationException {

		final OWLAxiom entailment = OWL_MANAGER_
				.loadOntologyFromOntologyDocument(entailFile).getLogicalAxioms()
				.iterator().next();

		final Proof<? extends Inference<OWLAxiom>> proof = prover_
				.getProof(entailment);
		final InferenceJustifier<Inference<OWLAxiom>, ? extends Set<? extends OWLAxiom>> justifier = InferenceJustifiers
				.justifyAssertedInferences();

		final MinimalSubsetCollector<OWLAxiom, Inference<OWLAxiom>, OWLAxiom> collector = new MinimalSubsetCollector<>(
				getFactory(), proof, justifier);

		return new HashSet<>(collector.collect(entailment));
	}

	@Override
	public Set<? extends Set<? extends OWLAxiom>> getExpectedJustifications(
			final File[] justFiles) throws OWLOntologyCreationException {
		final Set<Set<? extends OWLAxiom>> expectedJusts = new HashSet<>();
		for (final File justFile : justFiles) {
			final OWLOntology just = OWL_MANAGER_
					.loadOntologyFromOntologyDocument(justFile);
			expectedJusts.add(just.getLogicalAxioms());
		}
		return expectedJusts;
	}

	@Override
	public void dispose() {
		super.dispose();
		prover_.dispose();
		final Collection<OWLOntology> ontologies = new ArrayList<>(
				OWL_MANAGER_.getOntologies());
		for (final OWLOntology ontology : ontologies) {
			OWL_MANAGER_.removeOntology(ontology);
		}
	}

}
