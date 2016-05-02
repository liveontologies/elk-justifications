package org.semanticweb.elk.proofs.adapters;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

public class DepthLimitInferenceSetAdapter<C, A>
		implements InferenceSet<C, DepthLimitInferenceSetAdapter.Wrap<C, A>> {

	private final InferenceSet<C, A> original_;
	private final int maxDepth_;

	private final Map<C, Integer> conclusionDepth_;

	DepthLimitInferenceSetAdapter(final InferenceSet<C, A> original,
			final int maxDepth, final C root) {
		this.original_ = original;
		this.maxDepth_ = maxDepth;

		this.conclusionDepth_ = new HashMap<C, Integer>();
		/*
		 * TODO: This may be more efficient as DFS instead of BFS. Just the
		 * queue needs to be replaced with stack and the current depth may be
		 * remembered in a local variable instead of always read from
		 * conclusionDepth_.
		 */
		conclusionDepth_.put(root, 0);
		final LinkedList<C> toDo = new LinkedList<C>();
		toDo.add(root);

		for (;;) {
			final C current = toDo.poll();
			if (current == null) {
				break;
			}

			final int currentDepth = conclusionDepth_.get(current);

			if (currentDepth >= maxDepth - 1) {
				continue;
			}

			for (final Inference<C, A> inf : original.getInferences(current)) {

				for (final C premise : inf.getPremises()) {
					final Integer oldPremiseDepth =
							conclusionDepth_.get(premise);
					final int newPremiseDepth = currentDepth + 1;
					if (oldPremiseDepth == null) {
						conclusionDepth_.put(premise, newPremiseDepth);
						toDo.add(premise);
					} else {
						if (newPremiseDepth < oldPremiseDepth) {
							conclusionDepth_.put(premise, newPremiseDepth);
						}
					}
				}

			}

		}

	}

	@Override
	public Iterable<Inference<C, Wrap<C, A>>> getInferences(
			final C conclusion) {

		final Integer depth = conclusionDepth_.get(conclusion);

		if (depth == null) {
			// This conclusion is deeper than maxDepth_.
			return Collections.emptyList();
		}

		final Iterator<Inference<C, A>> originalIterator = original_
				.getInferences(conclusion).iterator();

		if (depth >= maxDepth_ - 1) {
			// All premises of inferences of this conclusion are axioms.

			return new Iterable<Inference<C, Wrap<C, A>>>() {
				@Override
				public Iterator<Inference<C, Wrap<C, A>>> iterator() {
					return new Iterator<Inference<C, Wrap<C, A>>>() {

						@Override
						public boolean hasNext() {
							return originalIterator.hasNext();
						}

						@Override
						public Inference<C, Wrap<C, A>> next() {
							return new InferenceConclusionWrap<C, A>(
									originalIterator.next());
						}

						@Override
						public void remove() {
							originalIterator.remove();
						}

					};
				}
			};

		} else {// depth < maxDepth_ - 1
			// The returned inferences should be the same as the originals.

			return new Iterable<Inference<C, Wrap<C, A>>>() {
				@Override
				public Iterator<Inference<C, Wrap<C, A>>> iterator() {
					return new Iterator<Inference<C, Wrap<C, A>>>() {

						@Override
						public boolean hasNext() {
							return originalIterator.hasNext();
						}

						@Override
						public Inference<C, Wrap<C, A>> next() {
							return new InferenceAxiomWrap<C, A>(
									originalIterator.next());
						}

						@Override
						public void remove() {
							originalIterator.remove();
						}

					};
				}
			};

		}

	}

	public static interface Wrap<C, A> {
		// Empty.
	}

	public static class ConclusionWrap<C, A> implements Wrap<C, A> {
		
		public final C conclusion;

		public ConclusionWrap(final C conclusion) {
			this.conclusion = conclusion;
		}

		@Override
		public String toString() {
			return conclusion.toString();
		}

		@Override
		public int hashCode() {
			return conclusion.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (obj instanceof ConclusionWrap) {
				final ConclusionWrap<?, ?> wrap = (ConclusionWrap<?, ?>) obj;
				return conclusion == null ? wrap.conclusion == null : conclusion.equals(wrap.conclusion);
			}
			return conclusion == null ? false : conclusion.equals(obj);
		}
		
	}

	public static class AxiomWrap<C, A> implements Wrap<C, A> {
		
		public final A axiom;

		public AxiomWrap(final A axiom) {
			this.axiom = axiom;
		}

		@Override
		public String toString() {
			return axiom.toString();
		}

		@Override
		public int hashCode() {
			return axiom.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (obj instanceof AxiomWrap) {
				final AxiomWrap<?, ?> wrap = (AxiomWrap<?, ?>) obj;
				return axiom == null ? wrap.axiom == null : axiom.equals(wrap.axiom);
			}
			return axiom == null ? false : axiom.equals(obj);
		}
		
	}

	public static abstract class InferenceWrap<C, A> implements Inference<C, Wrap<C, A>> {

		protected final Inference<C, A> inference;

		public InferenceWrap(final Inference<C, A> inference) {
			this.inference = inference;
		}

		@Override
		public C getConclusion() {
			return inference.getConclusion();
		}

		@Override
		public String toString() {
			return inference.toString();
		}

		@Override
		public int hashCode() {
			return inference.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (obj instanceof InferenceWrap) {
				final InferenceWrap<?, ?> wrap = (InferenceWrap<?, ?>) obj;
				return inference == null ? wrap.inference == null : inference.equals(wrap.inference);
			}
			return false;
		}
		
	}
	
	/**
	 * Inference adapter that returns the premises of the original inference in
	 * its justification. It has no premises if its own.
	 * 
	 * @author Peter Skocovsky
	 *
	 * @param <C>
	 * @param <A>
	 */
	public static class InferenceConclusionWrap<C, A> extends InferenceWrap<C, A> {

		public InferenceConclusionWrap(final Inference<C, A> inference) {
			super(inference);
		}

		@Override
		public Collection<? extends C> getPremises() {
			return Collections.emptyList();
		}

		@Override
		public Set<? extends Wrap<C, A>> getJustification() {
			final Set<Wrap<C, A>> justification = new HashSet<Wrap<C, A>>();
			for (final C premise : inference.getPremises()) {
				justification.add(new ConclusionWrap<C, A>(premise));
			}
			for (final A just : inference.getJustification()) {
				justification.add(new AxiomWrap<C, A>(just));
			}
			return justification;
		}

	}

	/**
	 * Inference adapter that wraps the justification of the original.
	 * 
	 * @author Peter Skocovsky
	 *
	 * @param <C>
	 * @param <A>
	 */
	public static class InferenceAxiomWrap<C, A> extends InferenceWrap<C, A> {

		public InferenceAxiomWrap(final Inference<C, A> inference) {
			super(inference);
		}

		@Override
		public Collection<? extends C> getPremises() {
			return inference.getPremises();
		}

		@Override
		public Set<? extends Wrap<C, A>> getJustification() {
			final Set<Wrap<C, A>> justification = new HashSet<Wrap<C, A>>();
			for (final A just : inference.getJustification()) {
				justification.add(new AxiomWrap<C, A>(just));
			}
			return justification;
		}

	}

}
