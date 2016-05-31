package org.semanticweb.elk.proofs.adapters;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferencePrinter;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.DepthLimitInferenceSetAdapter.AxiomWrap;
import org.semanticweb.elk.proofs.adapters.DepthLimitInferenceSetAdapter.ConclusionWrap;
import org.semanticweb.elk.proofs.adapters.DepthLimitInferenceSetAdapter.Wrap;
import org.semanticweb.elk.util.hashing.HashGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

public class CompressingInferenceSetAdapter<C, A>
		implements InferenceSet<C, A> {

	private static final Logger LOG = LoggerFactory
			.getLogger(CompressingInferenceSetAdapter.class);

	private final InferenceSet<C, A> original_;
	private final Set<C> compressedConclusions_;
	private final JustificationComputation<C, Wrap<C, A>> computation_;

	public CompressingInferenceSetAdapter(final InferenceSet<C, A> original,
			final C root,
			final JustificationComputation.Factory<C, Wrap<C, A>> factory,
			final Monitor monitor, final int depthLimit) {
		this.original_ = original;

		// Find the place to compress

		// collect conclusions
		final Set<C> conclusions = new HashSet<C>();
		Utils.traverseProofs(root, original,
				Functions.<Inference<C, A>> identity(),
				new Function<C, Void>() {
					@Override
					public Void apply(final C conclusion) {
						conclusions.add(conclusion);
						return null;
					}
				}, Functions.<A> identity());

		LOG.debug("number of conclusions: {}", conclusions.size());

		/*
		 * select depth limited sub-inference-set with most conclusions derived
		 * by many inferences and most leaves.
		 */

		int maxNConclManyInfs = 0;
		int maxNLeaves = 0;
		C maxConclusion = null;
		Set<C> maxConclsInDepthLimit = null;
		InferenceSet<C, Wrap<C, A>> maxLimited = null;

		for (final C conclusion : conclusions) {
			LOG.trace("measuring conclusion: {}", conclusion);

			LOG.trace("limiting depth");
			final InferenceSet<C, Wrap<C, A>> limited = InferenceSets
					.limitDepth(original, depthLimit, conclusion);

			LOG.trace("collecting leaves");
			final Set<Inference<C, Wrap<C, A>>> infs = new HashSet<>();
			final Set<Wrap<C, A>> leaves = new HashSet<Wrap<C, A>>();
			final Set<C> conclsInDepthLimit = new HashSet<C>();
			final List<Integer> sumInfsPerConcl = Arrays.asList(0);
			final int infCountThreshold = 5;// TODO: make this a parameter !!!
			final List<Integer> nConclManyInfs = Arrays.asList(0);
			Utils.traverseProofs(conclusion, limited,
					new Function<Inference<C, Wrap<C, A>>, Void>() {
						@Override
						public Void apply(final Inference<C, Wrap<C, A>> inf) {
							infs.add(inf);
							return null;
						}
					}, new Function<C, Void>() {
						@Override
						public Void apply(final C concl) {
							conclsInDepthLimit.add(concl);
							int infCount = 0;
							for (@SuppressWarnings("unused")
							final Inference<C, Wrap<C, A>> inf : limited
									.getInferences(concl)) {
								infCount++;
							}
							sumInfsPerConcl.set(0,
									sumInfsPerConcl.get(0) + infCount);
							if (infCount >= infCountThreshold) {
								nConclManyInfs.set(0,
										nConclManyInfs.get(0) + 1);
							}
							return null;
						}
					}, new Function<Wrap<C, A>, Void>() {
						@Override
						public Void apply(final Wrap<C, A> axiom) {
							leaves.add(axiom);
							return null;
						}
					});
			LOG.trace("{} = number of leaves", leaves.size());
			LOG.trace("{} = number of conclusions within the depth limit",
					conclsInDepthLimit.size());
			LOG.trace("{} = number of inferences within the depth limit",
					infs.size());
			LOG.trace("{} = average number of inferences of a conclusion",
					((double) sumInfsPerConcl.get(0))
							/ conclsInDepthLimit.size());
			LOG.trace("{} = number of conclusions with at least {} inferences",
					nConclManyInfs.get(0), infCountThreshold);

			if (nConclManyInfs.get(0) > maxNConclManyInfs) {
				maxNConclManyInfs = nConclManyInfs.get(0);
				maxNLeaves = leaves.size();
				maxConclusion = conclusion;
				maxConclsInDepthLimit = conclsInDepthLimit;
				maxLimited = limited;
			} else if (nConclManyInfs.get(0) < maxNConclManyInfs) {
				continue;
			}
			if (leaves.size() > maxNLeaves) {
				maxNLeaves = leaves.size();
				maxConclusion = conclusion;
				maxConclsInDepthLimit = conclsInDepthLimit;
				maxLimited = limited;
			}

		}

		LOG.debug(
				"found conclusion with {} partial conclusions with many inferences and {} leaves: {}",
				maxNConclManyInfs, maxNLeaves, maxConclusion);

		if (maxConclusion == null || maxNConclManyInfs < 3
				|| maxNLeaves < 100) {// TODO .: magic numbers
			// not worth compressing
			LOG.debug("not worth compressing");
			compressedConclusions_ = Collections.emptySet();
			this.computation_ = null;
		} else {
			// Compress it
			LOG.debug("Compressing");
			this.compressedConclusions_ = maxConclsInDepthLimit;
			this.computation_ = factory.create(maxLimited, monitor);
		}

	}

	@Override
	public Iterable<Inference<C, A>> getInferences(final C conclusion) {

		// If conclusion is NOT in the compressed part, use the original
		if (!compressedConclusions_.contains(conclusion)) {
			LOG.debug("not compressed: {}", conclusion);
			return original_.getInferences(conclusion);
		}

		// Compress it
		LOG.debug("compressing: {}", conclusion);
		final Collection<? extends Set<Wrap<C, A>>> symbolicJusts = computation_
				.computeJustifications(conclusion);

		return Iterables.transform(symbolicJusts,
				new Function<Set<Wrap<C, A>>, Inference<C, A>>() {

					@Override
					public Inference<C, A> apply(
							final Set<Wrap<C, A>> symbolicJust) {
						return new CompressedInference<C, A>(conclusion,
								symbolicJust);
					}

				});

	}

	private static class CompressedInference<C, A> implements Inference<C, A> {

		final C conclusion_;
		final Set<Wrap<C, A>> symbolicJust_;

		private Set<A> justification_ = null;

		public CompressedInference(final C conclusion,
				final Set<Wrap<C, A>> symbolicJust) {
			this.conclusion_ = conclusion;
			this.symbolicJust_ = symbolicJust;
		}

		@Override
		public C getConclusion() {
			return conclusion_;
		}

		@Override
		public Collection<? extends C> getPremises() {
			final Collection<Wrap<C, A>> premises = Collections2
					.filter(symbolicJust_, new Predicate<Wrap<C, A>>() {
						@Override
						public boolean apply(final Wrap<C, A> wrap) {
							return wrap instanceof ConclusionWrap;
						}
					});
			return Collections2.transform(premises,
					new Function<Wrap<C, A>, C>() {
						@Override
						public C apply(final Wrap<C, A> wrap) {
							return ((ConclusionWrap<C, A>) wrap).conclusion;
						}
					});
		}

		@Override
		public Set<? extends A> getJustification() {
			if (justification_ == null) {
				justification_ = new HashSet<>();
				for (final Wrap<C, A> wrap : symbolicJust_) {
					if (wrap instanceof AxiomWrap) {
						justification_.add(((AxiomWrap<C, A>) wrap).axiom);
					}
				}
			}
			return justification_;
		}

		@Override
		public int hashCode() {
			return HashGenerator.combinedHashCode(getClass(), conclusion_,
					symbolicJust_);
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}

			if (getClass() != obj.getClass()) {
				return false;
			}

			final CompressedInference<?, ?> other = (CompressedInference<?, ?>) obj;
			return conclusion_ == null ? other.conclusion_ == null
					: conclusion_.equals(other.conclusion_)
							&& symbolicJust_ == null
									? other.symbolicJust_ == null
									: symbolicJust_.equals(other.symbolicJust_);
		}

		@Override
		public String toString() {
			return InferencePrinter.toString(this);
		}

	}

}
