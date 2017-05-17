package org.semanticweb.elk.proofs.adapters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.Delegator;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.Inferences;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

/**
 * A proof containing inferences with at most two premises, obtained from
 * original proof by binarization. Premises and conclusions of the binarized
 * inferences are lists of premises and conclusions of the original inferences.
 * It is guaranteed that if one can derive a conclusion {@code C} using a set of
 * axioms in justificaiton of inferences, then using the same justification one
 * can derive the conlcusion {@code [C]}, that is, the singleton list of
 * {@code [C]}.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the original
 *            inferences
 */
class BinarizedProofAdapter<C> implements Proof<List<C>> {

	private final Proof<C> original_;

	BinarizedProofAdapter(final Proof<C> original) {
		this.original_ = original;
	}

	@Override
	public Collection<? extends Inference<List<C>>> getInferences(
			final List<C> conclusion) {
		switch (conclusion.size()) {
		case 0:
			return Collections.emptyList();
		case 1:
			C member = conclusion.get(0);
			return Collections2.transform(original_.getInferences(member),
					ToBinaryInference.<C> get());
		default:
			Inference<List<C>> inf = new BinaryListInference<C>(conclusion);
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
	 */
	private static class BinaryListInference<C> extends Delegator<List<C>>
			implements Inference<List<C>> {

		public BinaryListInference(final List<C> conclusion) {
			super(conclusion);
			if (conclusion.size() <= 1) {
				throw new IllegalArgumentException();
			}
		}

		@Override
		public List<C> getConclusion() {
			return getDelegate();
		}

		@Override
		public List<? extends List<C>> getPremises() {
			List<List<C>> result = new ArrayList<List<C>>(2);
			result.add(Collections.singletonList(getDelegate().get(0)));
			result.add(getDelegate().subList(1, getDelegate().size()));
			return result;
		}

		@Override
		public String toString() {
			return Inferences.toString(this);
		}

		@Override
		public String getName() {
			return getClass().getSimpleName();
		}

	}

	private static class ToBinaryInference<C>
			implements Function<Inference<C>, Inference<List<C>>> {

		private static final ToBinaryInference<?> INSTANCE_ = new ToBinaryInference<Object>();

		@Override
		public Inference<List<C>> apply(final Inference<C> input) {
			return new BinaryInferenceAdapter<C>(input);
		}

		@SuppressWarnings("unchecked")
		static <C> Function<Inference<C>, Inference<List<C>>> get() {
			return (ToBinaryInference<C>) INSTANCE_;
		}

	}

	private static class BinaryInferenceAdapter<C>
			extends Delegator<Inference<C>> implements Inference<List<C>> {

		BinaryInferenceAdapter(final Inference<C> original) {
			super(original);
		}

		@Override
		public List<C> getConclusion() {
			return Collections.singletonList(getDelegate().getConclusion());
		}

		@Override
		public List<? extends List<C>> getPremises() {
			List<? extends C> originalPremises = getDelegate().getPremises();
			int originalPremiseCount = originalPremises.size();
			switch (originalPremiseCount) {
			case 0:
				return Collections.emptyList();
			case 1:
				return Collections.singletonList(
						Collections.<C> singletonList(originalPremises.get(0)));
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
		public String toString() {
			return Inferences.toString(this);
		}

		@Override
		public String getName() {
			return getDelegate().getName();
		}

	}

	static class Justifier<C, A>
			implements InferenceJustifier<List<C>, Set<? extends A>> {

		private final InferenceJustifier<C, ? extends Set<? extends A>> original_;

		Justifier(
				final InferenceJustifier<C, ? extends Set<? extends A>> justifier) {
			this.original_ = justifier;
		}

		@Override
		public Set<? extends A> getJustification(
				final Inference<List<C>> inference) {
			if (!(inference instanceof BinaryInferenceAdapter)) {
				return Collections.emptySet();
			}
			// else
			final BinaryInferenceAdapter<C> binaryInference = (BinaryInferenceAdapter<C>) inference;
			return original_.getJustification(binaryInference.getDelegate());
		}

	}

}
