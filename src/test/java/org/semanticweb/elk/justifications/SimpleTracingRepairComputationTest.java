package org.semanticweb.elk.justifications;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;

import org.junit.runners.Parameterized.Parameters;
import org.liveontologies.puli.justifications.MinimalSubsetsFromProofs;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.reasoner.tracing.Conclusion;

public class SimpleTracingRepairComputationTest
		extends TracingJustificationComputationTest {

	@Parameters
	public static Collection<Object[]> parameters() throws URISyntaxException {
		return BaseJustificationComputationTest.getParameters(
				getRepairComputationFactories(), "test_input/simple",
				REPAIRS_DIR_NAME);
	}

	public SimpleTracingRepairComputationTest(
			final MinimalSubsetsFromProofs.Factory<Conclusion, ElkAxiom> factory,
			final File ontoFile, final Map<File, File[]> entailFilesPerJustFile)
			throws Exception {
		super(factory, ontoFile, entailFilesPerJustFile);
	}

}
