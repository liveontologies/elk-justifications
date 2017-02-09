package org.semanticweb.elk.proofs.adapters;

import java.util.Collections;

import org.liveontologies.puli.ProofNode;
import org.liveontologies.puli.ProofStep;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/**
 * An {@link InferenceSet} for inferences over {@link OWLExpression}s. The
 * inferences for such conclusions are taken by adapting
 * {@link OWLExpression#getInferences()} without justifications. In addition, if
 * a conclusion is an asserted {@link OWLAxiomExpression}, there is an inference
 * producing this conclusion from no premises and a singleton justification
 * corresponding to the {@link OWLAxiom} that it represents.
 * 
 * @see OWLExpression#getInferences()
 * @see OWLInference#getPremises()
 * @see OWLAxiomExpression#isAsserted()
 * @see OWLAxiomExpression#getAxiom()
 * 
 * @author Yevgeny Kazakov
 */
public class OWLExpressionInferenceSetAdapter
		implements InferenceSet<ProofNode<OWLAxiom>, OWLAxiom> {

	private final static Function<ProofStep<OWLAxiom>, Inference<ProofNode<OWLAxiom>, OWLAxiom>> FUNCTION_ = new OWLInferenceToInferenceFunction();

	private final OWLOntology ontology_;

	public OWLExpressionInferenceSetAdapter(OWLOntology ontology) {
		this.ontology_ = ontology;
	}

	@Override
	public Iterable<Inference<ProofNode<OWLAxiom>, OWLAxiom>> getInferences(
			ProofNode<OWLAxiom> conclusion) {

		Iterable<Inference<ProofNode<OWLAxiom>, OWLAxiom>> result = Iterables
				.transform(conclusion.getInferences(), FUNCTION_);

		if (ontology_.containsAxiom(conclusion.getMember())) {
			result = Iterables.concat(result, Collections.singleton(
					new OWLAxiomExpressionInferenceAdapter(conclusion)));
		}
		return result;
	}

}
