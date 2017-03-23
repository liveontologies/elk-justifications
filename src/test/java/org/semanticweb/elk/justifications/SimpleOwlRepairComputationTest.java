package org.semanticweb.elk.justifications;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

import org.junit.Assume;
import org.junit.runners.Parameterized.Parameters;
import org.liveontologies.puli.justifications.JustificationComputation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

public class SimpleOwlRepairComputationTest
		extends OwlJustificationComputationTest {

	@Parameters
	public static Collection<Object[]> parameters() throws URISyntaxException {
		return BaseJustificationComputationTest.getParameters(
				getRepairComputationFactories(), "test_input/simple",
				REPAIRS_DIR_NAME);
	}

	public SimpleOwlRepairComputationTest(
			final JustificationComputation.Factory<OWLAxiom, OWLAxiom> factory,
			final File ontoFile, final Map<File, File[]> entailFilesPerJustFile)
			throws OWLOntologyCreationException {
		super(factory, ontoFile, entailFilesPerJustFile);
	}

	@Override
	public void setUp() {
		super.setUp();
		Assume.assumeFalse("BottomUpOverAndOrGraphsForRepairs does not work.",
				getFactory().equals(
						BottomUpOverAndOrGraphsForRepairs.getFactory()));
	}
	
}
