package org.semanticweb.elk.proofs.adapters;

import java.util.List;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.Inferences;

public class DirectSatEncodingInference implements Inference<Integer> {

	private final Integer conclusion_;
	private final List<? extends Integer> premises_;

	DirectSatEncodingInference(final Integer conclusion,
			final List<? extends Integer> premises) {
		this.conclusion_ = conclusion;
		this.premises_ = premises;
	}

	@Override
	public Integer getConclusion() {
		return conclusion_;
	}

	@Override
	public List<? extends Integer> getPremises() {
		return premises_;
	}

	@Override
	public String toString() {
		return Inferences.toString(this);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ (conclusion_ == null ? 0 : conclusion_.hashCode());
		result = prime * result
				+ (premises_ == null ? 0 : premises_.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}

		if (getClass() != obj.getClass()) {
			return false;
		}
		final DirectSatEncodingInference other = (DirectSatEncodingInference) obj;

		return (conclusion_ == null ? other.conclusion_ == null
				: conclusion_.equals(other.conclusion_))
				&& (premises_ == null ? other.premises_ == null
						: premises_.equals(other.premises_));
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

}
