package org.semanticweb.elk.justifications.asp;

import org.semanticweb.elk.proofs.Inference;

public interface Encoder<C, A> {
	
	void encodeCommon(C goalConclusion);
	
	void encodeInference(Inference<C, A> inference);
	
	void encodeConclusion(C conclusion);
	
	void encodeAxiom(A axiom);
	
	void encodeSuffix();
	
}
