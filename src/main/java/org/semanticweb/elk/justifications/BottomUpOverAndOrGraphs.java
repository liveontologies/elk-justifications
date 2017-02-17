package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.andorgraph.AndNode;
import org.semanticweb.elk.justifications.andorgraph.Node;
import org.semanticweb.elk.justifications.andorgraph.OrNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

public class BottomUpOverAndOrGraphs<A>
		implements JustificationComputation<Node<A>, A> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(BottomUpOverAndOrGraphs.class);

	private final Monitor monitor_;

	/**
	 * A map from nodes to their justifications.
	 */
	private final ListMultimap<Node<A>, Justification<Node<A>, A>> justifications_ = ArrayListMultimap
			.create();

	/**
	 * Nodes for which the computation has been initialized.
	 */
	private final Set<Node<A>> initialized_ = new HashSet<Node<A>>();

	/**
	 * Justifications blocked from propagation because they are not needed for
	 * computing justifications for the goal.
	 */
	private final ListMultimap<Node<A>, Justification<Node<A>, A>> blockedJustifications_ = ArrayListMultimap
			.create();

	/**
	 * Newly computed justifications to be propagated.
	 */
	private final Queue<Justification<Node<A>, A>> toDoJustifications_ = new PriorityQueue<Justification<Node<A>, A>>();

	/**
	 * A map from relevant nodes to their children.
	 */
	private final Multimap<Node<A>, Node<A>> children_ = HashMultimap.create();

	/**
	 * The maximal sizes of justifications for conclusions computed so far.
	 */
	private final Map<Node<A>, Integer> sizeLimits_ = new HashMap<Node<A>, Integer>();

	public BottomUpOverAndOrGraphs(final Monitor monitor) {
		this.monitor_ = monitor;
	}

	@Override
	public GenericInferenceSet<Node<A>, ? extends JustifiedInference<Node<A>, A>> getInferenceSet() {
		// TODO .: This should be removed from the interface!
		return null;
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final Node<A> conclusion) {
		return computeJustifications(conclusion, Integer.MAX_VALUE);
	}

	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final Node<A> conclusion, final int sizeLimit) {
		BloomSet.resetStatistics();
		return new JustificationEnumerator(conclusion, sizeLimit).getResult();
	}

	private int countInitializedNodes_ = 0, countJustificationCandidates_ = 0;

	private int getSizeLimit(final Node<A> conclusion) {
		final Integer result = sizeLimits_.get(conclusion);
		if (result == null) {
			return 0;
		}
		// else
		return result;
	}

	private Collection<? extends Node<A>> getParents(final Node<A> node) {
		return node.getParents();
	}

	private A getInitial(final Node<A> node) {
		return node.getInitial();
	}

	@SafeVarargs
	private static <A> Justification<Node<A>, A> createJustification(
			final Node<A> conclusion,
			final Collection<? extends A>... collections) {
		return new BloomSet<Node<A>, A>(conclusion, collections);
	}

	private class JustificationEnumerator {

		private final Node<A> goal_;

		private final int sizeLimit_;

		/**
		 * The nodes that are relevant for the computation, i.e., those that are
		 * reachable from {@link #goal_}.
		 */
		private final Set<Node<A>> relevant_ = new HashSet<Node<A>>();

		/**
		 * Temporary queue used to compute {@link #relevant_}.
		 */
		private final Queue<Node<A>> toDo_ = new LinkedList<Node<A>>();

		private final List<? extends Set<A>> result_;

		public JustificationEnumerator(final Node<A> goal,
				final int sizeLimit) {
			this.goal_ = goal;
			this.sizeLimit_ = sizeLimit;
			this.result_ = justifications_.get(goal);
			toDo(goal);
			initialize();
		}

		private Collection<? extends Set<A>> getResult() {
			process();
			if (sizeLimit_ > getSizeLimit(goal_)) {
				sizeLimits_.put(goal_, sizeLimit_);
			}
			if (result_.isEmpty()) {
				return result_;
			}
			// else filter out oversized justifications
			int index = result_.size() - 1;
			while (result_.get(index).size() > sizeLimit_) {
				index--;
			}
			return result_.subList(0, index + 1);
		}

		private void toDo(final Node<A> node) {
			if (relevant_.add(node)) {
				toDo_.add(node);
			}
		}

		/**
		 * Traverse the graph to find relevant nodes, construct reversed node
		 * relation and fill the queue of justifications to be propagated
		 * reusing previously computed justifications.
		 */
		private void initialize() {

			Node<A> node;
			while ((node = toDo_.poll()) != null) {
				if (getSizeLimit(node) >= sizeLimit_) {
					// Relevant justifications already computed.
					continue;
				}
				boolean hasParent = false;
				for (final Node<A> parent : getParents(node)) {
					hasParent = true;
					LOGGER_.trace("{}: new node", parent);
					children_.put(parent, node);
					toDo(parent);
				}
				if (initialized_.add(node)) {
					countInitializedNodes_++;
					LOGGER_.trace("{}: initializing justifiations computation",
							node);
					// Initialize the justifications.
					final A initial = getInitial(node);
					if (initial != null) {
						final Justification<Node<A>, A> just = createJustification(
								node, Collections.singleton(initial));
						produce(just);
					} else if (!hasParent) {
						// Leaf (tautology).
						node.accept(new Node.Visitor<A, Void>() {

							@Override
							public Void visit(final AndNode<A> node) {
								// Empty conjunction; result is empty.
								final Justification<Node<A>, A> just = createJustification(
										node, Collections.<A> emptySet());
								produce(just);
								return null;
							}

							@Override
							public Void visit(final OrNode<A> node) {
								// Empty disjunction; there is no result.
								return null;
							}

						});
					}
				} else {
					// It has already been initialized.
					final List<Justification<Node<A>, A>> blocked = blockedJustifications_
							.get(node);
					for (final Justification<Node<A>, A> just : blocked) {
						LOGGER_.trace("unblocked {}", just);
						produce(just);
					}
					blocked.clear();
				}
			}

		}

		/**
		 * Process new justifications until the fixpoint.
		 */
		private void process() {
			Justification<Node<A>, A> just;
			int currentSize = 0;
			while ((just = toDoJustifications_.poll()) != null) {
				if (monitor_.isCancelled()) {
					return;
				}

				final int size = just.size();
				if (size != currentSize) {
					currentSize = size;
					if (currentSize > sizeLimit_) {
						// stop
						LOGGER_.trace(
								"there are justifications of size larger than {}",
								sizeLimit_);
						toDoJustifications_.add(just);
						return;
					}
					LOGGER_.debug("enumerating justifications of size {}...",
							currentSize);
				}

				final Node<A> node = just.getConclusion();
				if (!Utils.merge(just, justifications_.get(node))) {
					continue;
				}
				// else, just is minimal in node justifications
				LOGGER_.trace("new {}", just);

				if (just.isEmpty()) {
					/*
					 * All justifications are computed, the children are not
					 * needed anymore.
					 */
					for (final Node<A> parent : getParents(node)) {
						children_.remove(parent, node);
					}
				}

				// Propagating justification to children.
				for (final Node<A> child : children_.get(node)) {

					final Justification<Node<A>, A> childJust = just
							.copyTo(child);

					child.accept(new Node.Visitor<A, Void>() {

						@Override
						public Void visit(final AndNode<A> andNode) {
							// Distribute.
							Collection<Justification<Node<A>, A>> childJusts = new ArrayList<Justification<Node<A>, A>>();
							childJusts.add(childJust);
							for (final Node<A> parent : getParents(child)) {
								if (!parent.equals(node)) {
									childJusts = Utils.join(childJusts,
											justifications_.get(parent));
								}
							}
							for (final Justification<Node<A>, A> childJust : childJusts) {
								produce(childJust);
							}
							return null;
						}

						@Override
						public Void visit(final OrNode<A> orNode) {
							// Union; just produce.
							produce(childJust);
							return null;
						}

					});

				}

			}

		}

		private void produce(final Justification<Node<A>, A> just) {

			final Node<A> node = just.getConclusion();
			if (!relevant_.contains(node)) {
				blockedJustifications_.put(node, just);
				LOGGER_.trace("blocked {}", just);
				return;
			}
			// else
			if (!Utils.isMinimal(just, result_)) {
				blockedJustifications_.put(node, just);
				LOGGER_.trace("blocked {}", just);
				return;
			}
			// else
			countJustificationCandidates_++;
			toDoJustifications_.add(just);
		}

	}

	@Override
	public String[] getStatNames() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, Object> getStatistics() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void logStatistics() {
		// TODO Auto-generated method stub

	}

	@Override
	public void resetStatistics() {
		// TODO Auto-generated method stub

	}

}
