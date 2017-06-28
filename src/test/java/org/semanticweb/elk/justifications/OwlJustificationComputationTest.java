package org.semanticweb.elk.justifications;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.liveontologies.owlapi.proof.OWLProver;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.Proofs;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.semanticweb.elk.owlapi.ElkProverFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;

public abstract class OwlJustificationComputationTest
		extends BaseJustificationComputationTest<OWLAxiom, OWLAxiom> {

	private static final OWLOntologyManager OWL_MANAGER_ = OWLManager
			.createOWLOntologyManager();

	private final OWLProver prover_;

	public OwlJustificationComputationTest(
			final MinimalSubsetsFromProofs.Factory<OWLAxiom, OWLAxiom> factory,
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

		final Proof<OWLAxiom> proof = Proofs
				.addAssertedInferences(prover_.getProof(entailment),
						prover_.getRootOntology().getAxioms(Imports.EXCLUDED));
		final InferenceJustifier<OWLAxiom, ? extends Set<? extends OWLAxiom>> justifier = Proofs
				.justifyAssertedInferences();

		final MinimalSubsetCollector<OWLAxiom, OWLAxiom> collector = new MinimalSubsetCollector<>(
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
