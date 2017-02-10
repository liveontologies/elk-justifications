package org.semanticweb.elk.justifications;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class PruningJustificationComputation<C, A>
		extends CancellableJustificationComputation<C, A> {

	private static final Factory<?, ?> FACTORY_ = new Factory<Object, Object>();

	@SuppressWarnings("unchecked")
	public static <C, A> JustificationComputation.Factory<C, A> getFactory() {
		return (Factory<C, A>) FACTORY_;
	}

	/**
	 * a map from premises to inferences for relevant conclusions
	 */
	private final Multimap<C, JustifiedInference<C, A>> inferencesByPremises_ = ArrayListMultimap
			.create();

	/**
	 * a set of inferences currently blocked in the recursive traversal
	 */
	private final Set<JustifiedInference<C, A>> blocked_ = new HashSet<>();

	/**
	 * a set of conclusions that was chosen for the recursive traversal before
	 */
	private final Set<C> chosenBefore_ = new HashSet<>();

	private PruningJustificationComputation(final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferences,
			final Monitor monitor) {
		super(inferences, monitor);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C conclusion, final int sizeLimit) {
		return computeJustifications(conclusion);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C conclusion) {

		/*
		 * Remove inferences using conclusion, call incremental bottom-up, add
		 * the inferences back and remove inferences deriving conclusion.
		 * 
		 * Do this recursively, until the inference set is small enough!
		 */

		collectInferencesByPremises(conclusion);

		final IncrementalBottomUp<C, A> incremental = new IncrementalBottomUp<>(
				monitor_);

		/* 
		 * following recursion implemented with stack
		 * 
		 * @formatter:off
		 * 
		 * 	void procedure(final C current) {
		 * 		if (terminate) {
		 * 			propagate(current)
		 * 		} else {
		 * 			next = chooseNext(current)
		 * 			blocked = blockFrom(next)
		 * 			procedure(next)
		 * 			unblock(blocked)
		 * 			blocked = blockTo(next)
		 * 			procedure(current)
		 * 			unblock(blocked)
		 * 		}
		 * 	}
		 * 
		 * @formatter:on
		 */

		final int maxDepth = 3;// TODO: make this a parameter !!!
		/*
		 * FIXME: With large maxDepth it happens that there are no next
		 * conclusions to choose! Make clever termination condition!
		 */

		final Stack<RecursionContext> stack = new Stack<>();
		int depth = 0;
		C current = conclusion;
		blocked_.addAll(blockInferencesFrom(current));
		chosenBefore_.add(current);
		while (current != null || !stack.isEmpty()) {

			if (current != null) {
				// coming from up down
				depth++;

				if (depth >= maxDepth) {
					// base case

					// propagate
					incremental.propagate(
							collectReachableInferences(current, blocked_));

					// return
					current = null;

				} else {
					// recursive case

					// choose next
					final C next = chooseNext(current);
					chosenBefore_.add(next);

					// block from
					final Set<JustifiedInference<C, A>> blockedFrom = blockInferencesFrom(
							next);
					blocked_.addAll(blockedFrom);

					// "from" recursion on next
					stack.push(new RecursionContext(RecursionContext.PC_FROM,
							current, next, blockedFrom));
					current = next;

				}

			} else {
				// going from down up
				depth--;

				final RecursionContext context = stack.pop();
				switch (context.programCounter) {
				case RecursionContext.PC_FROM:

					// unblock from
					blocked_.removeAll(context.blocked);

					// block to
					final Set<JustifiedInference<C, A>> blockedTo = blockInferencesTo(
							context.next);
					blocked_.addAll(blockedTo);

					// "to" recursion on next
					stack.push(new RecursionContext(RecursionContext.PC_TO,
							context.current, context.next, blockedTo));
					current = context.current;

					break;
				case RecursionContext.PC_TO:

					// unblock to
					blocked_.removeAll(context.blocked);

					// return
					current = null;

					break;
				}

			}

		}

		return incremental.getResult().get(conclusion);
	}

	private class RecursionContext {

		public static final int PC_FROM = 1;
		public static final int PC_TO = 2;

		public final int programCounter;
		public final C current;
		public final C next;
		public final Set<JustifiedInference<C, A>> blocked;

		public RecursionContext(final int programCounter, final C current,
				final C next, final Set<JustifiedInference<C, A>> blocked) {
			this.programCounter = programCounter;
			this.current = current;
			this.next = next;
			this.blocked = blocked;
		}

	}

	private C chooseNext(final C current) {

		/*
		 * reachable conclusion with most inferences that use it as a premise,
		 * are reachable and are not blocked
		 */
		final Set<JustifiedInference<C, A>> reachable = collectReachableInferences(
				current, blocked_);

		C result = null;
		int measure = Integer.MIN_VALUE;
		for (final C conclusion : inferencesByPremises_.keySet()) {

			if (chosenBefore_.contains(conclusion)) {
				continue;
			}

			/*
			 * If a conclusion is used as a premise of some reachable inference,
			 * it is reachable.
			 */

			int nReachableInfs = 0;
			for (final JustifiedInference<C, A> inf : inferencesByPremises_
					.get(conclusion)) {
				if (reachable.contains(inf)) {
					nReachableInfs++;
				}
			}
			if (nReachableInfs > measure) {
				measure = nReachableInfs;
				result = conclusion;
			}

		}

		return result;
	}

	private void collectInferencesByPremises(final C conclusion) {

		final Queue<C> toDo = new LinkedList<C>();
		final Set<C> done = new HashSet<C>();

		toDo.add(conclusion);
		done.add(conclusion);

		C concl;
		while ((concl = toDo.poll()) != null) {

			for (final JustifiedInference<C, A> inf : getInferences(concl)) {
				for (final C premise : inf.getPremises()) {
					inferencesByPremises_.put(premise, inf);
					if (done.add(premise)) {
						toDo.add(premise);
					}
				}
			}

		}

	}

	private Set<JustifiedInference<C, A>> blockInferencesFrom(final C premise) {

		final Set<JustifiedInference<C, A>> blocked = new HashSet<JustifiedInference<C, A>>();

		final Queue<C> toDo = new LinkedList<C>();
		final Set<C> done = new HashSet<C>();

		toDo.add(premise);
		done.add(premise);

		while (true) {
			final C prem = toDo.poll();
			if (prem == null) {
				break;
			}

			for (final JustifiedInference<C, A> inf : inferencesByPremises_.get(prem)) {

				blocked.add(inf);

				final C concl = inf.getConclusion();
				boolean conclusionDerived = false;
				for (final JustifiedInference<C, A> i : getInferences(concl)) {
					if (!blocked.contains(i)) {
						conclusionDerived = true;
						break;
					}
				}
				if (!conclusionDerived) {
					if (done.add(concl)) {
						toDo.add(concl);
					}
				}

			}

		}

		return blocked;
	}

	private Set<JustifiedInference<C, A>> blockInferencesTo(final C conclusion) {

		final Set<JustifiedInference<C, A>> blocked = new HashSet<JustifiedInference<C, A>>();

		for (final JustifiedInference<C, A> inf : getInferences(conclusion)) {
			blocked.add(inf);
		}

		return blocked;
	}

	private Set<JustifiedInference<C, A>> collectReachableInferences(final C conclusion,
			final Set<JustifiedInference<C, A>> blocked) {

		final Set<JustifiedInference<C, A>> reachable = new HashSet<JustifiedInference<C, A>>();

		final Queue<C> toDo = new LinkedList<C>();
		final Set<C> done = new HashSet<C>();

		toDo.add(conclusion);
		done.add(conclusion);

		while (true) {
			final C concl = toDo.poll();
			if (concl == null) {
				break;
			}

			for (final JustifiedInference<C, A> inf : getInferences(concl)) {
				if (!blocked.contains(inf)) {

					reachable.add(inf);

					for (final C premise : inf.getPremises()) {
						if (done.add(premise)) {
							toDo.add(premise);
						}
					}
				}
			}

		}

		return reachable;
	}

	@Override
	public String[] getStatNames() {
		// TODO Auto-generated method stub
		return new String[0];
	}

	@Override
	public Map<String, Object> getStatistics() {
		// TODO Auto-generated method stub
		return Collections.emptyMap();
	}

	private static class Factory<C, A>
			implements JustificationComputation.Factory<C, A> {

		@Override
		public JustificationComputation<C, A> create(
				final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet, final Monitor monitor) {
			return new PruningJustificationComputation<>(inferenceSet, monitor);
		}

		@Override
		public String[] getStatNames() {
			// TODO Auto-generated method stub
			return new String[0];
		}

	}

}
