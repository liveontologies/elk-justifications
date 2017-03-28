package org.semanticweb.elk.justifications.andorgraph;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.liveontologies.puli.Delegator;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterators;

public class AndOrGraphs {

	public static <A> Node<A> getDual(final Node<A> node) {
		return AndOrGraphs.<A> getDualize().apply(node);
	}

	private static <A> Collection<? extends Node<A>> getDualParents(
			final Node<A> node) {
		return Collections2.transform(node.getParents(),
				AndOrGraphs.<A> getDualize());
	}

	private static final Function<?, ?> DUALIZE_ = new Function<Node<Object>, Node<Object>>() {

		@Override
		public Node<Object> apply(final Node<Object> input) {
			return input.accept(new Node.Visitor<Object, Node<Object>>() {

				@Override
				public Node<Object> visit(final AndNode<Object> node) {
					return new DualAndNode<Object>(node);
				}

				@Override
				public Node<Object> visit(final OrNode<Object> node) {
					return new DualOrNode<Object>(node);
				}

			});
		}

	};

	@SuppressWarnings("unchecked")
	private static <A> Function<Node<A>, Node<A>> getDualize() {
		return (Function<Node<A>, Node<A>>) DUALIZE_;
	}

	private static class DualAndNode<A> extends Delegator<AndNode<A>>
			implements OrNode<A> {

		public DualAndNode(final AndNode<A> delegate) {
			super(delegate);
		}

		@Override
		public Collection<? extends Node<A>> getParents() {
			return getDualParents(getDelegate());
		}

		@Override
		public A getInitial() {
			return getDelegate().getInitial();
		}

		@Override
		public <O> O accept(final Node.Visitor<A, O> visitor) {
			return visitor.visit(this);
		}

	}

	private static class DualOrNode<A> extends Delegator<OrNode<A>>
			implements AndNode<A> {

		public DualOrNode(final OrNode<A> delegate) {
			super(delegate);
		}

		@Override
		public Collection<? extends Node<A>> getParents() {
			return getDualParents(getDelegate());
		}

		@Override
		public A getInitial() {
			return getDelegate().getInitial();
		}

		@Override
		public <O> O accept(final Node.Visitor<A, O> visitor) {
			return visitor.visit(this);
		}

	}

	public static <C, A> Node<A> getAndOrGraphForJustifications(
			final C conclusion, final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
		return new OrNodeConclusionAdapter<>(conclusion, inferenceSet,
				justifier);
	}

	private static class AndNodeInferenceAdapter<C, A>
			extends Delegator<Inference<C>> implements AndNode<A> {

		private final InferenceSet<C> inferenceSet_;

		private final InferenceJustifier<C, ? extends Set<? extends A>> justifier_;

		public AndNodeInferenceAdapter(final Inference<C> inference,
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
			super(inference);
			this.inferenceSet_ = inferenceSet;
			this.justifier_ = justifier;
		}

		@Override
		public Collection<? extends Node<A>> getParents() {

			final Collection<Node<A>> premises = Collections2.transform(
					getDelegate().getPremises(), new Function<C, Node<A>>() {

						@Override
						public Node<A> apply(final C premise) {
							return new OrNodeConclusionAdapter<>(premise,
									inferenceSet_, justifier_);
						}

					});

			final Collection<Node<A>> axioms = Collections2.transform(
					justifier_.getJustification(getDelegate()),
					new Function<A, Node<A>>() {

						@Override
						public Node<A> apply(final A axiom) {
							return new OrNodeAxiomAdapter<>(axiom);
						}

					});

			return new AbstractCollection<Node<A>>() {

				@Override
				public Iterator<Node<A>> iterator() {
					return Iterators.concat(premises.iterator(),
							axioms.iterator());
				}

				@Override
				public int size() {
					return premises.size() + axioms.size();
				}

			};
		}

		@Override
		public A getInitial() {
			return null;
		}

		@Override
		public <O> O accept(final Node.Visitor<A, O> visitor) {
			return visitor.visit(this);
		}

	}

	private static class OrNodeConclusionAdapter<C, A> extends Delegator<C>
			implements OrNode<A> {

		private final InferenceSet<C> inferenceSet_;

		private final InferenceJustifier<C, ? extends Set<? extends A>> justifier_;

		public OrNodeConclusionAdapter(final C conclusion,
				final InferenceSet<C> inferenceSet,
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
			super(conclusion);
			this.inferenceSet_ = inferenceSet;
			this.justifier_ = justifier;
		}

		@Override
		public Collection<? extends Node<A>> getParents() {
			return Collections2.transform(
					inferenceSet_.getInferences(getDelegate()),
					new Function<Inference<C>, Node<A>>() {

						@Override
						public Node<A> apply(final Inference<C> inference) {
							return new AndNodeInferenceAdapter<>(inference,
									inferenceSet_, justifier_);
						}

					});
		}

		@Override
		public A getInitial() {
			return null;
		}

		@Override
		public <O> O accept(final Node.Visitor<A, O> visitor) {
			return visitor.visit(this);
		}

	}

	private static class OrNodeAxiomAdapter<C, A> extends Delegator<A>
			implements OrNode<A> {

		public OrNodeAxiomAdapter(final A axiom) {
			super(axiom);
		}

		@Override
		public Collection<? extends Node<A>> getParents() {
			return Collections.emptyList();
		}

		@Override
		public A getInitial() {
			return getDelegate();
		}

		@Override
		public <O> O accept(final Node.Visitor<A, O> visitor) {
			return visitor.visit(this);
		}

	}

}
