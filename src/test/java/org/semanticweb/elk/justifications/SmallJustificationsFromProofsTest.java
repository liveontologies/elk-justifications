package org.semanticweb.elk.justifications;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;

@RunWith(Parameterized.class)
public class SmallJustificationsFromProofsTest extends
		BaseJustificationsFromProofsTest {
	
	public static final String INPUT_FILE_DIR = "simple";
	public static final String CONCLUSION_ANNOTATION_FRAGMENT = "conclusion";
	
	private static final String SUB_GROUP = "sub";
	private static final String SUPER_GROUP = "super";
	private static final Pattern SUB_CLASS_AXIOM_REG = Pattern.compile(
			"[^S]*SubClassOf\\s*\\(\\s*" +
					"<(?<" + SUB_GROUP + ">[^>]+)>\\s+" +
					"<(?<" + SUPER_GROUP + ">[^>]+)>\\s*" +
			"\\).*");

	@Parameters
	public static Collection<Object[]> data() {
		
		final List<JustificationComputation.Factory<OWLExpression, OWLAxiom>> computations =
				new ArrayList<JustificationComputation.Factory<OWLExpression, OWLAxiom>>();
		computations.add(BottomUpJustificationComputation.<OWLExpression, OWLAxiom>getFactory());
		computations.add(BinarizedJustificationComputation
				.getFactory(BottomUpJustificationComputation
						.<List<OWLExpression>, OWLAxiom> getFactory()));
		
		final String[] fileNames = new String[] {
				"ExistCycle",
				"Exponential",
				"Sequence",
				"Tautologies",
				"Transitive",
				"TransitivityByChain",
			};
		
		final List<Object[]> result = new ArrayList<Object[]>();
		for (final JustificationComputation.Factory<OWLExpression, OWLAxiom> c : computations) {
			for (final String fileName : fileNames) {
				result.add(new Object[] {c, fileName});
			}
		}
		return result;
	}
	
	private final String inputFileName;
	private final String expectedDirName;
	
	public SmallJustificationsFromProofsTest(
			final JustificationComputation.Factory<OWLExpression, OWLAxiom> computationFactory,
			final String fileName) {
		super(computationFactory);
		this.inputFileName = fileName + ".owl";
		this.expectedDirName = fileName + ".expected";
	}
	
	@Override
	protected OWLOntology getInputOntology()
			throws URISyntaxException, OWLOntologyCreationException {
		
		final URI inputDirURI = SmallJustificationsFromProofsTest.class
				.getClassLoader().getResource(INPUT_FILE_DIR).toURI();
		
		return owlManager_.loadOntologyFromOntologyDocument(
				new File(new File(inputDirURI), inputFileName));
	}
	
	@Override
	protected Iterable<OWLSubClassOfAxiom> getConclusions() {
		
		for (final OWLAnnotation annotation : owlOntology.getAnnotations()) {
			if (CONCLUSION_ANNOTATION_FRAGMENT.equals(
					annotation.getProperty().getIRI().getFragment())) {
				final Matcher m = SUB_CLASS_AXIOM_REG.matcher(
						annotation.getValue().toString());
				if (m.matches()) {
					return Collections.singletonList(owlFactory.getOWLSubClassOfAxiom(
							owlFactory.getOWLClass(IRI.create(m.group(SUB_GROUP))),
							owlFactory.getOWLClass(IRI.create(m.group(SUPER_GROUP)))));
				}
			}
		}
		
		throw new IllegalArgumentException("The test input ontology does not specify any expected conclusion!");
	}

	@Override
	protected Set<Set<OWLAxiom>> getExpectedJustifications(
			final int conclusionIndex, final OWLSubClassOfAxiom conclusion)
			throws URISyntaxException, OWLOntologyCreationException {
		
		final Set<Set<OWLAxiom>> expected = new HashSet<Set<OWLAxiom>>();
		final File expectedDir = new File(new File(JustificationsFromProofs.class
				.getClassLoader().getResource(INPUT_FILE_DIR).toURI()), expectedDirName);
		for (final File expectedFile : expectedDir.listFiles()) {
			final OWLOntology expectedOnt = owlManager_
					.loadOntologyFromOntologyDocument(expectedFile);
			expected.add(expectedOnt.getAxioms());
		}
		
		return expected;
	}
	
}
