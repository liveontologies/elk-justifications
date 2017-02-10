package org.semanticweb.elk.proofs;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.Delegator;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.JustifiedInference;

public class DummyJustifiedInference<C, A> extends Delegator<Inference<C>>
		implements JustifiedInference<C, A> {

	public DummyJustifiedInference(final Inference<C> delegate) {
		super(delegate);
	}

	@Override
	public String getName() {
		return getDelegate().getName();
	}

	@Override
	public C getConclusion() {
		return getDelegate().getConclusion();
	}

	@Override
	public List<? extends C> getPremises() {
		return getDelegate().getPremises();
	}

	@Override
	public Set<? extends A> getJustification() {
		return Collections.emptySet();
	}

}
