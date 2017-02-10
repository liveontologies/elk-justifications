package org.semanticweb.elk.proofs.adapters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.Delegator;
import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.proofs.InferencePrinter;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;

/**
 * An inference set containing inferences with at most two premises, obtained
 * from original inference set by binarization. Premises and conclusions of the
 * binarized inferences are lists of premises and conclusions of the original
 * inferences. It is guaranteed that if one can derive a conclusion {@code C}
 * using a set of axioms in justificaiton of inferences, then using the same
 * justification one can derive the conlcusion {@code [C]}, that is, the
 * singleton list of {@code [C]}.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the original
 *            inferences
 * @param <A>
 *            the type of axioms used by the original and binarized inferences
 */
class BinarizedInferenceSetAdapter<C, A> implements
		GenericInferenceSet<List<C>, JustifiedInference<List<C>, A>> {

	private final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> original_;

	BinarizedInferenceSetAdapter(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> original) {
		this.original_ = original;
	}

	@Override
	public Collection<? extends JustifiedInference<List<C>, A>> getInferences(
			final List<C> conclusion) {
		switch (conclusion.size()) {
		case 0:
			return Collections.emptyList();
		case 1:
			C member = conclusion.get(0);
			return Collections2.transform(original_.getInferences(member),
					ToBinaryInference.<C, A> get());
		default:
			JustifiedInference<List<C>, A> inf = new BinaryListInference<C, A>(
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
	private static class BinaryListInference<C, A> extends Delegator<List<C>>
			implements JustifiedInference<List<C>, A> {

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
		public Set<? extends A> getJustification() {
			return Collections.emptySet();
		}

		@Override
		public String toString() {
			return InferencePrinter.toString(this);
		}

		@Override
		public String getName() {
			return getClass().getSimpleName();
		}

	}

	private static class ToBinaryInference<C, A> implements
			Function<JustifiedInference<C, A>, JustifiedInference<List<C>, A>> {

		private static final ToBinaryInference<?, ?> INSTANCE_ = new ToBinaryInference<Object, Object>();

		@Override
		public JustifiedInference<List<C>, A> apply(
				JustifiedInference<C, A> input) {
			return new BinaryInferenceAdapter<C, A>(input);
		}

		@SuppressWarnings("unchecked")
		static <C, A> Function<JustifiedInference<C, A>, JustifiedInference<List<C>, A>> get() {
			return (ToBinaryInference<C, A>) INSTANCE_;
		}

	}

	private static class BinaryInferenceAdapter<C, A>
			extends Delegator<JustifiedInference<C, A>>
			implements JustifiedInference<List<C>, A> {

		BinaryInferenceAdapter(final JustifiedInference<C, A> original) {
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
		public Set<? extends A> getJustification() {
			return getDelegate().getJustification();
		}

		@Override
		public String toString() {
			return InferencePrinter.toString(this);
		}

		@Override
		public String getName() {
			return getDelegate().getName();
		}

	}

}
