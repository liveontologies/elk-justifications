package org.semanticweb.elk.proofs.adapters;

import java.util.Collections;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapitools.proofs.OWLInference;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLAxiomExpression;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;

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
		implements InferenceSet<OWLExpression, OWLAxiom> {

	private final static Function<OWLInference, Inference<OWLExpression, OWLAxiom>> FUNCTION_ = new OWLInferenceToInferenceFunction();

	@Override
	public Iterable<Inference<OWLExpression, OWLAxiom>> getInferences(
			OWLExpression conclusion) {

		Iterable<Inference<OWLExpression, OWLAxiom>> result;

		try {
			result = Iterables.transform(conclusion.getInferences(), FUNCTION_);
		} catch (ProofGenerationException e) {
			throw new RuntimeException(e);
		}

		if (conclusion instanceof OWLAxiomExpression) {
			OWLAxiomExpression expression = (OWLAxiomExpression) conclusion;
			if (!expression.isAsserted()) {
				return result;
			}
			// else
			result = Iterables.concat(result, Collections.singleton(
					new OWLAxiomExpressionInferenceAdapter(expression)));
		}
		return result;
	}

}
