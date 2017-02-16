package org.semanticweb.elk.justifications;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

import org.junit.runners.Parameterized.Parameters;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

public class SimpleOwlJustificationComputationTest
		extends OwlJustificationComputationTest {

	@Parameters
	public static Collection<Object[]> parameters() throws URISyntaxException {
		return BaseJustificationComputationTest
				.getParameters(getComputationFactories(), "test_input/simple");
	}

	public SimpleOwlJustificationComputationTest(
			final JustificationComputation.Factory<OWLAxiom, OWLAxiom> factory,
			final File ontoFile, final Map<File, File[]> entailFilesPerJustFile)
			throws OWLOntologyCreationException {
		super(factory, ontoFile, entailFilesPerJustFile);
	}

}
