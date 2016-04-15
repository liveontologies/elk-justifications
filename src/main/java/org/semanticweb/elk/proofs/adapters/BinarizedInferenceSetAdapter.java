package org.semanticweb.elk.proofs.adapters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

/**
 * An inference set containing inferences with at most two premises, obtained
 * from original inference set by binarization. Premises and conclusions of the
 * binarized inferences are lists of premises and conclusions of the original
 * inferences. It is guaranteed that if one can derive a conclusion {@code C}
 * using a set of axioms in justificaiton of inferences, then using the same
 * justification one can derive the conlcusion {@code [C]}, that is, the
 * singleton list of [C].
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the original
 *            inferences
 * @param <A>
 *            the type of axioms used by the original and binarized inferences
 */
class BinarizedInferenceSetAdapter<C, A> implements InferenceSet<List<C>, A> {

	private final InferenceSet<C, A> original_;

	BinarizedInferenceSetAdapter(InferenceSet<C, A> original) {
		this.original_ = original;
	}

	@Override
	public Iterable<Inference<List<C>, A>> getInferences(List<C> conclusion) {
		switch (conclusion.size()) {
		case 0:
			return Collections.emptyList();
		case 1:
			C member = conclusion.get(0);
			return Iterables.transform(original_.getInferences(member),
					ToBinaryInference.<C, A> get());
		default:
			Inference<List<C>, A> inf = new BinaryListInference<C, A>(
					conclusion);
			return Collections.singleton(inf);
		}
	}

	/**
	 * An inference producing a list from the singleton list of the first
	 * element and the sublist of the remaining elements.
	 * 
	 * @author Yevgeny Kazakov
	 *
	 * @param <C>
	 * @param <A>
	 */
	private static class BinaryListInference<C, A>
			implements Inference<List<C>, A> {

		private final List<C> conclusion_;

		public BinaryListInference(List<C> conclusion) {
			if (conclusion.size() <= 1) {
				throw new IllegalArgumentException();
			}
			this.conclusion_ = conclusion;
		}

		@Override
		public List<C> getConclusion() {
			return conclusion_;
		}

		@Override
		public Collection<? extends List<C>> getPremises() {
			List<List<C>> result = new ArrayList<List<C>>(2);
			result.add(Collections.singletonList(conclusion_.get(0)));
			result.add(conclusion_.subList(1, conclusion_.size()));
			return result;
		}

		@Override
		public Set<? extends A> getJustification() {
			return Collections.emptySet();
		}

	}

	private static class ToBinaryInference<C, A>
			implements Function<Inference<C, A>, Inference<List<C>, A>> {

		private static final ToBinaryInference<?, ?> INSTANCE_ = new ToBinaryInference<Object, Object>();

		@Override
		public Inference<List<C>, A> apply(Inference<C, A> input) {
			return new BinaryInferenceAdapter<C, A>(input);
		}

		@SuppressWarnings("unchecked")
		static <C, A> Function<Inference<C, A>, Inference<List<C>, A>> get() {
			return (ToBinaryInference<C, A>) INSTANCE_;
		}

	}

	private static class BinaryInferenceAdapter<C, A>
			implements Inference<List<C>, A> {

		private final Inference<C, A> original_;

		BinaryInferenceAdapter(Inference<C, A> original) {
			this.original_ = original;
		}

		@Override
		public List<C> getConclusion() {
			return Collections.singletonList(original_.getConclusion());
		}

		@Override
		public Collection<? extends List<C>> getPremises() {
			Collection<? extends C> originalPremises = original_.getPremises();
			int originalPremiseCount = originalPremises.size();
			switch (originalPremiseCount) {
			case 0:
				return Collections.emptyList();
			case 1:
				return Collections.singleton(Collections
						.<C> singletonList(originalPremises.iterator().next()));
			default:
				List<C> firstPremise = null, secondPremise = new ArrayList<C>(
						originalPremiseCount - 1);
				boolean first = true;
				for (C premise : originalPremises) {
					if (first) {
						first = false;
						firstPremise = Collections.singletonList(premise);
					} else {
						secondPremise.add(premise);
					}
				}
				List<List<C>> result = new ArrayList<List<C>>(2);
				result.add(firstPremise);
				result.add(secondPremise);
				return result;
			}
		}

		@Override
		public Set<? extends A> getJustification() {
			return original_.getJustification();
		}

	}

}
