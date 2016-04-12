package org.semanticweb.elk.justifications;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.OWLExpressionInferenceSetAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;

/**
 * Unit test for simple App.
 */
public abstract class BaseJustificationsFromProofsTest {

	protected OWLOntologyManager manager;
	protected OWLDataFactory factory;
	protected OWLOntology inputOntology;
	
	private final Class<? extends JustificationComputation> computationClass;
	
	public BaseJustificationsFromProofsTest(
			final Class<? extends JustificationComputation> computationClass) {
		this.computationClass = computationClass;
	}

	protected abstract OWLOntology getInputOntology() throws Exception;

	protected JustificationComputation getJustificationComputation()
					throws ReflectiveOperationException {
		
		final Constructor<? extends JustificationComputation> constructor =
				computationClass.getConstructor(InferenceSet.class);
		
		return constructor.newInstance(new OWLExpressionInferenceSetAdapter());
	}
	
	protected abstract Iterable<OWLSubClassOfAxiom> getConclusions()
			throws Exception;
	
	protected abstract Set<Set<OWLAxiom>> getExpectedJustifications(
			int conclusionIndex, OWLSubClassOfAxiom conclusion)
			throws Exception;
	
	@Before
	public void setUp() throws Exception {
		manager = OWLManager.createOWLOntologyManager();
		factory = manager.getOWLDataFactory();
		inputOntology = getInputOntology();
	}
	
	@Test
	public void testJustificationsFromProofs() throws Exception {
		
		final Iterable<OWLSubClassOfAxiom> conclusions = getConclusions();
		
		final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
		final ExplainingOWLReasoner reasoner =
				(ExplainingOWLReasoner) reasonerFactory.createReasoner(inputOntology);
		reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
		
		int conclusionIndex = 0;
		for (final OWLSubClassOfAxiom conclusion : conclusions) {
			
			Assert.assertTrue(
					reasoner.getSubClasses(conclusion.getSuperClass(), false)
					.containsEntity((OWLClass) conclusion.getSubClass()));
			
			final JustificationComputation computation =
					getJustificationComputation();
			
			final Collection<Set<OWLAxiom>> justifications =
					computation.computeJustifications(reasoner.getDerivedExpression(conclusion));
			
			final Collection<Set<OWLAxiom>> expected = getExpectedJustifications(
					conclusionIndex++, conclusion);
			
			Assert.assertEquals("number of justifications for\n"
					+ "conclusion: " + conclusion + "\n"
					+ "justComp: " + computation.getClass().getSimpleName() + "\n",
					expected.size(), justifications.size());
			Assert.assertEquals("conclusion: " + conclusion + "\n"
					+ "justComp: " + computation.getClass().getSimpleName() + "\n",
					expected, justifications);
		}
		
	}
	
}
