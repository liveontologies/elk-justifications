package org.semanticweb.elk.justifications;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.runners.Parameterized.Parameters;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.reasoner.tracing.Conclusion;

public class SimpleTracingJustificationComputationTest
		extends TracingJustificationComputationTest {

	@Parameters
	public static Collection<Object[]> parameters() throws URISyntaxException {

		final List<JustificationComputation.Factory<?, ?>> computations = new ArrayList<JustificationComputation.Factory<?, ?>>();
		computations.add(BottomUpJustificationComputation.getFactory());
		computations.add(BinarizedJustificationComputation
				.getFactory(BottomUpJustificationComputation
						.<List<Object>, Object> getFactory()));
		computations.add(MinPremisesBottomUp.getFactory());
		// computations.add(PruningJustificationComputation.getFactory());

		return BaseJustificationComputationTest.getParameters(computations,
				"test_input/simple");
	}

	public SimpleTracingJustificationComputationTest(
			final JustificationComputation.Factory<Conclusion, ElkAxiom> factory,
			final File ontoFile, final Map<File, File[]> entailFilesPerJustFile)
			throws Exception {
		super(factory, ontoFile, entailFilesPerJustFile);
	}

}
