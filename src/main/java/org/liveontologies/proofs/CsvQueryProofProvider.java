package org.liveontologies.proofs;

import org.liveontologies.pinpointing.experiments.CsvQueryDecoder;
import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.statistics.NestedStats;

public class CsvQueryProofProvider<Q, C, I extends Inference<? extends C>, A>
		implements ProofProvider<String, C, I, A> {

	private final CsvQueryDecoder.Factory<Q> decoder_;
	private final ProofProvider<Q, C, I, A> proofProvider_;

	public CsvQueryProofProvider(final CsvQueryDecoder.Factory<Q> decoder,
			final ProofProvider<Q, C, I, A> proofProvider) {
		this.decoder_ = decoder;
		this.proofProvider_ = proofProvider;
	}

	@NestedStats(name = "proof")
	public ProofProvider<Q, C, I, A> getProofProvider() {
		return proofProvider_;
	}

	@Override
	public JustificationCompleteProof<C, I, A> getProof(final String query)
			throws ExperimentException {
		return proofProvider_.getProof(CsvQueryDecoder.decode(query, decoder_));
	}

	@Override
	public void dispose() {
		proofProvider_.dispose();
	}

}
