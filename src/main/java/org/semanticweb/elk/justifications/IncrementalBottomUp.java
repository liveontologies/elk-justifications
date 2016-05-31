package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

/**
 * Computes justifications bottom-up over inferences supplied in
 * {@link #propagate(Set)}. Previously computed justifications are stored and
 * used next time {@link #propagate(Set)} is called.
 * 
 * <pre>
 * TODO: HasStatistics
 * TODO: minimize premise justifications
 * TODO: minimize w.r.t. goal conclusion
 * </pre>
 * 
 * @author Peter Skocovsky
 *
 * @param <C>
 * @param <A>
 */
public class IncrementalBottomUp<C, A> {

	private static final Logger LOG_ = LoggerFactory
			.getLogger(IncrementalBottomUp.class);

	/**
	 * A map from conclusions to inferences that derive them. Whether an
	 * inference is indexed is checked against this map, so it should not
	 * contain duplicated key-value pairs.
	 */
	private final SetMultimap<C, Inference<C, A>> inferencesByConclusions_ = HashMultimap
			.create();

	/**
	 * A map from premises to inferences that use them.
	 */
	private final Multimap<C, Inference<C, A>> inferencesByPremises_ = ArrayListMultimap
			.create();

	/**
	 * A map from conclusions to their justifications.
	 */
	private final ListMultimap<C, Justification<C, A>> justifications_ = ArrayListMultimap
			.create();

	/**
	 * Newly computed justifications to be propagated.
	 */
	private final Queue<Justification<C, A>> toDoJustifications_ = new PriorityQueue<Justification<C, A>>();

	protected final Monitor monitor_;

	public IncrementalBottomUp(final Monitor monitor) {
		this.monitor_ = monitor;
	}

	// Statistics
	private int countJustificationCandidates_ = 0;

	public ListMultimap<C, Justification<C, A>> propagate(
			final Set<Inference<C, A>> update) {

		initialize(update);

		process();

		return justifications_;
	}

	private void initialize(final Set<Inference<C, A>> update) {

		// remove inferences that are not in the update

		Iterator<Inference<C, A>> infIt = inferencesByConclusions_.values()
				.iterator();
		while (infIt.hasNext()) {
			final Inference<C, A> inf = infIt.next();
			if (!update.contains(inf)) {
				LOG_.trace("{}: removed inference", inf);
				infIt.remove();
			}
		}

		infIt = inferencesByPremises_.values().iterator();
		while (infIt.hasNext()) {
			final Inference<C, A> inf = infIt.next();
			if (!update.contains(inf)) {
				LOG_.trace("{}: removed inference", inf);
				infIt.remove();
			}
		}

		// add and queue up inferences from the update that are not indexed

		for (final Inference<C, A> inf : update) {
			if (inferencesByConclusions_.put(inf.getConclusion(), inf)) {
				LOG_.trace("{}: added inference", inf);

				List<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
				conclusionJusts.add(createJustification(inf.getConclusion(),
						inf.getJustification()));

				for (final C premise : inf.getPremises()) {
					inferencesByPremises_.put(premise, inf);

					// propagate existing justifications for premises
					conclusionJusts = Utils.join(conclusionJusts,
							justifications_.get(premise));
				}

				for (final Justification<C, A> just : conclusionJusts) {
					toDoJustifications_.add(just);
					countJustificationCandidates_++;
				}

			}
		}

	}

	/**
	 * process new justifications until the fixpoint
	 */
	private void process() {

		Justification<C, A> just;
		while ((just = toDoJustifications_.poll()) != null) {
			if (monitor_.isCancelled()) {
				return;
			}

			final C conclusion = just.getConclusion();

			if (!Utils.merge(just, justifications_.get(conclusion))) {
				continue;
			}
			// just is minimal in conclusion justifications
			LOG_.trace("new {}", just);

			if (just.isEmpty()) {
				// all justifications are computed,
				// the inferences are not needed anymore
				for (final Inference<C, A> inf : inferencesByConclusions_
						.get(conclusion)) {
					for (final C premise : inf.getPremises()) {
						inferencesByPremises_.remove(premise, inf);
					}
				}
				inferencesByConclusions_.removeAll(conclusion);
			}

			/*
			 * propagating justification over inferences
			 */
			for (final Inference<C, A> inf : inferencesByPremises_
					.get(conclusion)) {

				Collection<Justification<C, A>> conclusionJusts = new ArrayList<Justification<C, A>>();
				Justification<C, A> conclusionJust = just
						.copyTo(inf.getConclusion())
						.addElements(inf.getJustification());
				conclusionJusts.add(conclusionJust);
				for (final C premise : inf.getPremises()) {
					if (!premise.equals(conclusion)) {
						conclusionJusts = Utils.join(conclusionJusts,
								justifications_.get(premise));
					}
				}

				for (final Justification<C, A> conclJust : conclusionJusts) {
					toDoJustifications_.add(conclJust);
					countJustificationCandidates_++;
				}

			}

		}

	}

	@SafeVarargs
	private static <C, A> Justification<C, A> createJustification(C conclusion,
			Collection<? extends A>... collections) {
		return new BloomSet<C, A>(conclusion, collections);
	}

	public ListMultimap<C, Justification<C, A>> getResult() {
		return justifications_;
	}
	
}
