package org.liveontologies.proofs;

import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.puli.Inference;

public interface ProofProvider<Q, C, I extends Inference<? extends C>, A> {

	JustificationCompleteProof<C, I, A> getProof(Q query)
			throws ExperimentException;

	void dispose();

}
