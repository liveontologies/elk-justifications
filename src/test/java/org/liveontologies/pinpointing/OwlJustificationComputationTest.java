package org.liveontologies.pinpointing;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.proofs.OwlProofProvider;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

public abstract class OwlJustificationComputationTest extends
		BaseJustificationComputationTest<OWLAxiom, OWLAxiom, Inference<OWLAxiom>, OWLAxiom> {

	private static final OWLOntologyManager OWL_MANAGER_ = OWLManager
			.createOWLOntologyManager();

	public OwlJustificationComputationTest(
			final MinimalSubsetsFromProofs.Factory<OWLAxiom, Inference<OWLAxiom>, OWLAxiom> factory,
			final File ontoFile, final Map<File, File[]> entailFilesPerJustFile)
			throws ExperimentException {
		super(new OwlProofProvider(ontoFile, OWL_MANAGER_), factory, ontoFile,
				entailFilesPerJustFile);
	}

	@Override
	protected OWLAxiom getQuery(final File entailFile) throws Exception {
		return OWL_MANAGER_.loadOntologyFromOntologyDocument(entailFile)
				.getLogicalAxioms().iterator().next();
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
		final Collection<OWLOntology> ontologies = new ArrayList<>(
				OWL_MANAGER_.getOntologies());
		for (final OWLOntology ontology : ontologies) {
			OWL_MANAGER_.removeOntology(ontology);
		}
	}

}
