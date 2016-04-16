package org.semanticweb.elk.proofs.adapters;

import java.util.Collection;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferencePrinter;

public class DirectSatEncodingInference implements Inference<Integer, Integer> {

	private final Integer conclusion_;
	private final Collection<? extends Integer> premises_;
	private final Set<? extends Integer> justifications_;
	
	DirectSatEncodingInference(final Integer conclusion,
			final Collection<? extends Integer> premises,
			final Set<? extends Integer> justifications) {
		this.conclusion_ = conclusion;
		this.premises_ = premises;
		this.justifications_ = justifications;
	}

	@Override
	public Integer getConclusion() {
		return conclusion_;
	}

	@Override
	public Collection<? extends Integer> getPremises() {
		return premises_;
	}

	@Override
	public Set<? extends Integer> getJustification() {
		return justifications_;
	}
	
	@Override
	public String toString() {
		return InferencePrinter.toString(this);
	}

}
