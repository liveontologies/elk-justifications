package org.semanticweb.elk.justifications;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLogicalAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

@RunWith(Parameterized.class)
public class RealWorldJustificationsFromProofsTest extends
		BaseJustificationsFromProofsTest {
	
	public static final String INPUT_FILE_DIR = "real_world";

	@Parameters
	public static Collection<Object[]> data() {
		
		final List<JustificationComputation.Factory<OWLAxiom, OWLAxiom>> computations =
				new ArrayList<JustificationComputation.Factory<OWLAxiom, OWLAxiom>>();
		computations.add(BottomUpJustificationComputation.<OWLAxiom, OWLAxiom>getFactory());
		computations.add(BinarizedJustificationComputation
				.getFactory(BottomUpJustificationComputation
						.<List<OWLAxiom>, OWLAxiom> getFactory()));
		computations.add(MinPremisesBottomUp.<OWLAxiom, OWLAxiom>getFactory());
//		computations.add(PruningJustificationComputation.<OWLAxiom, OWLAxiom>getFactory());
		
		final String[][] fileNames = new String[][] {
			{
				"go_cel.owl",
				"gene.query.random.owl",
				"gene.random.justifications"
			}, {
				"full-galen_cel.owl",
				"full.query.random.owl",
				"full.random.justifications"
			},
		};
		
		final List<Object[]> result = new ArrayList<Object[]>();
		for (final JustificationComputation.Factory<OWLAxiom, OWLAxiom> c : computations) {
			for (final String[] fns : fileNames) {
				final Object[] r = new Object[fns.length + 1];
				r[0] = c;
				System.arraycopy(fns, 0, r, 1, fns.length);
				result.add(r);
			}
		}
		return result;
	}

	private final String inputFileName;
	private final String queriesFileName;
	private final String expectedDirName;
	
	public RealWorldJustificationsFromProofsTest(
			final JustificationComputation.Factory<OWLAxiom, OWLAxiom> computationFactory,
			final String inputFileName, final String queriesFileName,
			final String expectedDirName) {
		super(computationFactory);
		this.inputFileName = inputFileName;
		this.queriesFileName = queriesFileName;
		this.expectedDirName = expectedDirName;
	}

	@Override
	protected OWLOntology getInputOntology() throws Exception {
		return owlManager_.loadOntologyFromOntologyDocument(getFileFromResource(
				INPUT_FILE_DIR, inputFileName));
	}

	@Override
	protected Iterable<OWLSubClassOfAxiom> getConclusions() throws Exception {
		OWLOntology ont = owlManager_.loadOntologyFromOntologyDocument(
				getFileFromResource(INPUT_FILE_DIR, queriesFileName));
		
		final File justDir = getFileFromResource(INPUT_FILE_DIR,
				expectedDirName);
		
		return Iterables.filter(ont.getAxioms(AxiomType.SUBCLASS_OF),
				new Predicate<OWLSubClassOfAxiom>() {
					@Override
					public boolean apply(final OWLSubClassOfAxiom axiom) {
						return new File(justDir, Utils.toFileName(axiom)).exists();
					}
				});
	}

	@Override
	protected Set<Set<OWLAxiom>> getExpectedJustifications(
			final int conclusionIndex, final OWLSubClassOfAxiom conclusion)
			throws Exception {
		
		final Set<Set<OWLAxiom>> expected = new HashSet<Set<OWLAxiom>>();
		final File expectedDir = new File(
				getFileFromResource(INPUT_FILE_DIR, expectedDirName),
				Utils.toFileName(conclusion));
		for (final File expectedFile : expectedDir.listFiles()) {
			final OWLOntology expectedOnt = owlManager_
					.loadOntologyFromOntologyDocument(expectedFile);
			
			final Set<OWLAxiom> just = Sets.newHashSet(Iterables.transform(
					expectedOnt.getLogicalAxioms(),
					new Function<OWLLogicalAxiom, OWLAxiom>() {
						@Override
						public OWLAxiom apply(final OWLLogicalAxiom axiom) {
							return axiom;
						}
					}));
			
			expected.add(just);
		}
		
		return expected;
	}
	
	// TODO: Move to Utils
	private static File getFileFromResource(final String resourceName,
			final String fileName) throws URISyntaxException {
		return new File(new File(
				RealWorldJustificationsFromProofsTest.class.getClassLoader()
				.getResource(resourceName).toURI()), fileName);
	}

}
