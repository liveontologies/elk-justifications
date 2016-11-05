package org.semanticweb.elk.proofs.adapters;

import java.util.Collections;

import org.liveontologies.owlapi.proof.OWLProofNode;
import org.liveontologies.owlapi.proof.OWLProofStep;
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
		implements InferenceSet<OWLProofNode, OWLAxiom> {

	private final static Function<OWLProofStep, Inference<OWLProofNode, OWLAxiom>> FUNCTION_ = new OWLInferenceToInferenceFunction();

	private final OWLOntology ontology_;

	public OWLExpressionInferenceSetAdapter(OWLOntology ontology) {
		this.ontology_ = ontology;
	}

	@Override
	public Iterable<Inference<OWLProofNode, OWLAxiom>> getInferences(
			OWLProofNode conclusion) {

		Iterable<Inference<OWLProofNode, OWLAxiom>> result = Iterables
				.transform(conclusion.getInferences(), FUNCTION_);

		if (ontology_.containsAxiom(conclusion.getMember())) {
			result = Iterables.concat(result, Collections.singleton(
					new OWLAxiomExpressionInferenceAdapter(conclusion)));
		}
		return result;
	}

}
