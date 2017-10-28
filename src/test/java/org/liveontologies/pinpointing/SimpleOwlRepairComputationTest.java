package org.liveontologies.pinpointing;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

import org.junit.runners.Parameterized.Parameters;
import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.semanticweb.owlapi.model.OWLAxiom;

public class SimpleOwlRepairComputationTest
		extends OwlJustificationComputationTest {

	@Parameters
	public static Collection<Object[]> parameters() throws URISyntaxException {
		return BaseJustificationComputationTest.getParameters(
				getRepairComputationFactories(), "test_input/simple",
				REPAIRS_DIR_NAME);
	}

	public SimpleOwlRepairComputationTest(
			final MinimalSubsetsFromProofs.Factory<OWLAxiom, Inference<OWLAxiom>, OWLAxiom> factory,
			final File ontoFile, final Map<File, File[]> entailFilesPerJustFile)
			throws ExperimentException {
		super(factory, ontoFile, entailFilesPerJustFile);
	}

}
