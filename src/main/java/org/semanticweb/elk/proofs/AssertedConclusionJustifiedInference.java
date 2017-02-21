package org.semanticweb.elk.proofs;

import java.util.Collections;
import java.util.Set;

import org.liveontologies.puli.AssertedConclusionInference;
import org.liveontologies.puli.JustifiedInference;

public abstract class AssertedConclusionJustifiedInference<C, A> extends
		AssertedConclusionInference<C> implements JustifiedInference<C, A> {

	public AssertedConclusionJustifiedInference(final C conclusion) {
		super(conclusion);
	}

	@Override
	public Set<? extends A> getJustification() {
		return Collections.singleton(conclusionToAxiom(getConclusion()));
	}

	protected abstract A conclusionToAxiom(C conclusion);

	@Override
	public String toString() {
		return InferencePrinter.toString(this);
	}

	public static class Projection<C>
			extends AssertedConclusionJustifiedInference<C, C> {

		public Projection(final C conclusion) {
			super(conclusion);
		}

		@Override
		protected C conclusionToAxiom(final C conclusion) {
			return conclusion;
		}

	}

}
