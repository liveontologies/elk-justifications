package org.semanticweb.elk.justifications;

import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.liveontologies.proof.util.ProofNode;
import org.liveontologies.proof.util.ProofNodes;
import org.semanticweb.elk.owlapi.ElkProver;
import org.semanticweb.elk.owlapi.ElkProverFactory;
import org.semanticweb.elk.proofs.adapters.OWLExpressionInferenceSetAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;

/**
 * Unit test for simple App.
 */
public abstract class BaseJustificationsFromProofsTest {

	protected OWLOntologyManager owlManager_;
	protected OWLDataFactory owlFactory;
	protected OWLOntology owlOntology;
	
	private final JustificationComputation.Factory<ProofNode<OWLAxiom>, OWLAxiom> computationFactory_;
	
	public BaseJustificationsFromProofsTest(
			JustificationComputation.Factory<ProofNode<OWLAxiom>, OWLAxiom> computationFactory) {
		this.computationFactory_ = computationFactory;
	}

	protected abstract OWLOntology getInputOntology() throws Exception;

	protected JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom> getJustificationComputation() {
		
		return computationFactory_.create(new OWLExpressionInferenceSetAdapter(owlOntology), DummyMonitor.INSTANCE);
	}
	
	protected abstract Iterable<OWLSubClassOfAxiom> getConclusions()
			throws Exception;
	
	protected abstract Set<Set<OWLAxiom>> getExpectedJustifications(
			int conclusionIndex, OWLSubClassOfAxiom conclusion)
			throws Exception;
	
	@Before
	public void setUp() throws Exception {
		owlManager_ = OWLManager.createOWLOntologyManager();
		owlFactory = owlManager_.getOWLDataFactory();
		owlOntology = getInputOntology();
	}
	
	@Test
	public void testJustificationsFromProofs() throws Exception {
		
		final Iterable<OWLSubClassOfAxiom> conclusions = getConclusions();
		
		final ElkProverFactory proverFactory = new ElkProverFactory();
		final ElkProver reasoner = proverFactory.createReasoner(owlOntology);
		reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
		
		int conclusionIndex = 0;
		for (final OWLSubClassOfAxiom conclusion : conclusions) {
			
			Assert.assertTrue(
					reasoner.getSubClasses(conclusion.getSuperClass(), false)
					.containsEntity((OWLClass) conclusion.getSubClass()));
			
			final JustificationComputation<ProofNode<OWLAxiom>, OWLAxiom> computation =
					getJustificationComputation();
			
			final ProofNode<OWLAxiom> proofNode = ProofNodes
					.create(reasoner.getProof(conclusion), conclusion);
			final Set<Set<OWLAxiom>> justifications = new HashSet<Set<OWLAxiom>>(
					computation.computeJustifications(proofNode));
			
			final Set<Set<OWLAxiom>> expected = getExpectedJustifications(
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
