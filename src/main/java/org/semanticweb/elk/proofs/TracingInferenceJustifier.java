package org.semanticweb.elk.proofs;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.visitors.DummyElkAxiomVisitor;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.ConclusionBaseFactory;
import org.semanticweb.elk.reasoner.tracing.DummyConclusionVisitor;
import org.semanticweb.elk.reasoner.tracing.TracingInference;
import org.semanticweb.elk.reasoner.tracing.TracingInferencePremiseVisitor;

public class TracingInferenceJustifier
		implements InferenceJustifier<Conclusion, Set<? extends ElkAxiom>> {

	public static final TracingInferenceJustifier INSTANCE = new TracingInferenceJustifier();

	private static final Conclusion.Factory CONCLUSION_FACTORY_ = new ConclusionBaseFactory();

	private static final Conclusion.Visitor<Void> DUMMY_CONCLUSION_VISITOR_ = new DummyConclusionVisitor<Void>();

	private TracingInferenceJustifier() {
		// Forbid instantiation.
	}

	@Override
	public Set<? extends ElkAxiom> getJustification(
			final Inference<Conclusion> inference) {
		if (!(inference instanceof TracingInference)) {
			return Collections.emptySet();
		}
		// else
		final TracingInference tracingInference = (TracingInference) inference;
		final Set<ElkAxiom> result = new HashSet<ElkAxiom>();
		new ElkAxiomCollector(result);
		tracingInference.accept(new TracingInferencePremiseVisitor<>(
				CONCLUSION_FACTORY_, DUMMY_CONCLUSION_VISITOR_,
				new ElkAxiomCollector(result)));
		return result;
	}

	private static class ElkAxiomCollector extends DummyElkAxiomVisitor<Void> {

		private final Collection<ElkAxiom> axioms_;

		public ElkAxiomCollector(final Collection<ElkAxiom> axioms) {
			this.axioms_ = axioms;
		}

		@Override
		protected Void defaultLogicalVisit(ElkAxiom axiom) {
			axioms_.add(axiom);
			return null;
		}

	}

}
