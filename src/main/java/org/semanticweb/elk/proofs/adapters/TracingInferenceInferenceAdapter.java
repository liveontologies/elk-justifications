package org.semanticweb.elk.proofs.adapters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.visitors.DummyElkAxiomVisitor;
import org.semanticweb.elk.owl.visitors.ElkAxiomVisitor;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.ConclusionBaseFactory;
import org.semanticweb.elk.reasoner.tracing.DummyConclusionVisitor;
import org.semanticweb.elk.reasoner.tracing.TracingInference;
import org.semanticweb.elk.reasoner.tracing.TracingInferenceConclusionVisitor;
import org.semanticweb.elk.reasoner.tracing.TracingInferencePremiseVisitor;

/**
 * An adapter from a {@link TracingInference} to an {@link Inference} that has
 * the corresponding conclusion, premises, and justifications.
 * 
 * @author Yevgeny Kazakov
 */
class TracingInferenceInferenceAdapter
		implements Inference<Conclusion, ElkAxiom> {

	private final static Conclusion.Factory CONCLUSION_FACTORY_ = new ConclusionBaseFactory();

	private final static Conclusion.Visitor<Void> DUMMY_CONCLUSION_VISITOR_ = new DummyConclusionVisitor<Void>();

	private final static ElkAxiomVisitor<Void> DUMMY_ELK_AXIOM_VISITOR_ = new DummyElkAxiomVisitor<Void>();

	private final static TracingInference.Visitor<Conclusion> CONCLUSION_GETTER_ = new ConclusionGetter();

	private final TracingInference inference_;

	TracingInferenceInferenceAdapter(TracingInference inference) {
		this.inference_ = inference;
	}

	@Override
	public Conclusion getConclusion() {
		return inference_.accept(CONCLUSION_GETTER_);
	}

	@Override
	public Collection<? extends Conclusion> getPremises() {
		List<Conclusion> result = new ArrayList<Conclusion>();
		inference_.accept(new PremisesGetter(result));
		return result;
	}

	@Override
	public Set<? extends ElkAxiom> getJustification() {
		Set<ElkAxiom> result = new HashSet<ElkAxiom>();
		inference_.accept(new JustificationGetter(result));
		return result;
	}

	static class ConclusionCollector extends DummyConclusionVisitor<Void> {

		private final Collection<Conclusion> conclusions_;

		ConclusionCollector(Collection<Conclusion> conclusions) {
			this.conclusions_ = conclusions;
		}

		@Override
		protected Void defaultVisit(Conclusion conclusion) {
			conclusions_.add(conclusion);
			return null;
		}

	}

	static class ElkAxiomCollector extends DummyElkAxiomVisitor<Void> {

		private final Collection<ElkAxiom> axioms_;

		ElkAxiomCollector(Collection<ElkAxiom> axioms) {
			this.axioms_ = axioms;
		}

		@Override
		/**
		 * Invoked to visit every logical axiom
		 */
		protected Void defaultLogicalVisit(ElkAxiom axiom) {
			axioms_.add(axiom);
			return null;
		}

	}

	static class TrivialConclusionVisitor
			extends DummyConclusionVisitor<Conclusion> {

		@Override
		protected Conclusion defaultVisit(Conclusion conclusion) {
			return conclusion;
		}
	}

	static class ConclusionGetter
			extends TracingInferenceConclusionVisitor<Conclusion> {

		ConclusionGetter() {
			super(CONCLUSION_FACTORY_, new TrivialConclusionVisitor());
		}
	}

	static class PremisesGetter extends TracingInferencePremiseVisitor<Void> {

		public PremisesGetter(Collection<Conclusion> premises) {
			super(CONCLUSION_FACTORY_, new ConclusionCollector(premises),
					DUMMY_ELK_AXIOM_VISITOR_);
		}

	}

	static class JustificationGetter
			extends TracingInferencePremiseVisitor<Void> {

		public JustificationGetter(Collection<ElkAxiom> axioms) {
			super(CONCLUSION_FACTORY_, DUMMY_CONCLUSION_VISITOR_,
					new ElkAxiomCollector(axioms));
		}

	}

}
