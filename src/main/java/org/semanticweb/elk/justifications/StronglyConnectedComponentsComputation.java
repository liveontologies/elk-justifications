package org.semanticweb.elk.justifications;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Computes strongly connected components of conclusions induced by the graph of
 * inferences for the given root conclusion: the conclusion of the inferences is
 * reachable from all premises of the inferences. The implementation uses the
 * standard linear time <a href=
 * "https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm"
 * >Tarjan's strongly connected component algorithm<a> . To prevent stack
 * overflow, recursive calls (usually used in the standard formulations) are
 * avoided by means of custom stacks.
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of premises and conclusions of inferences over which to
 *            compute the components
 */
public class StronglyConnectedComponentsComputation<C> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(StronglyConnectedComponentsComputation.class);

	/**
	 * the inference set which induces the graph
	 */
	private InferenceSet<C, ?> inferences_;

	/**
	 * conclusions on the current path, assume to alternate with elements of
	 * {@link #inferenceStack_}
	 */
	private final Deque<ConclusionRecord<C>> conclusionStack_ = new LinkedList<ConclusionRecord<C>>();

	/**
	 * inferences on the current path, assume to alternate with elements of
	 * {@link #conclusionStack_}
	 */
	private final Deque<InferenceRecord<C>> inferenceStack_ = new LinkedList<InferenceRecord<C>>();

	/**
	 * accumulates the current component candidates
	 */
	private final Deque<C> stack_ = new LinkedList<C>();

	/**
	 * components of visited elements not in {@link #stack_} in topological
	 * order: child components come before parent components reachable from them
	 * by means of inferences
	 */
	private final List<List<C>> components_ = new ArrayList<List<C>>();

	/**
	 * the number to be assigned to the next visited element, increases with
	 * every assignment
	 */
	private int id_ = 0;

	/**
	 * serves two purposes: (1) for elements in {@link #stack_} represents the
	 * order in which elements are created so that the smallest element in
	 * {@link #stack_} can be found; (2) for created elements not on
	 * {@link #stack_} represents the component ID of the element, i.e., the
	 * index of the list in {@link #components_} in which the element occurs
	 */
	private final Map<C, Integer> index_ = new HashMap<C, Integer>();

	/**
	 * assigns to elements on #stack_ the minimal identifier of the reachable
	 * element on on #stack_; used to identify cycles
	 */
	private final Map<C, Integer> lowlink_ = new HashMap<C, Integer>();

	public StronglyConnectedComponentsComputation(InferenceSet<C, ?> root,
			C conclusion) {
		this.inferences_ = root;
		toDo(conclusion);
		process();
	}

	/**
	 * Computes strongly connected components of conclusions induced by the
	 * graph of inferences for the given root conclusion: the conclusion of the
	 * inferences is reachable from all premises of the inferences.
	 * 
	 * @param inferences
	 * @param root
	 * @return the {@link StronglyConnectedComponentDecomposition} in which the
	 *         components are listed in the inference order: conclusions of
	 *         inferences appear in the same or letter components than the
	 *         premises of the inferences; root appears in the last component
	 */
	public static <C> StronglyConnectedComponentDecomposition<C> computeComponents(
			InferenceSet<C, ?> inferences, C root) {
		StronglyConnectedComponentsComputation<C> computation = new StronglyConnectedComponentsComputation<>(
				inferences, root);
		return new StronglyConnectedComponentDecomposition<>(
				computation.components_, computation.index_);
	}

	private void toDo(C conclusion) {
		index_.put(conclusion, id_);
		lowlink_.put(conclusion, id_);
		id_++;
		stack_.push(conclusion);
		conclusionStack_.push(new ConclusionRecord<C>(conclusion, this));
		LOGGER_.trace("{}: conclusion pushed", conclusion);
	}

	private void toDo(Inference<C, ?> inf) {
		inferenceStack_.push(new InferenceRecord<>(inf));
		LOGGER_.trace("{}: inference pushed", inf);
	}

	private void process() {
		for (;;) {
			ConclusionRecord<C> conclRec = conclusionStack_.peek();
			if (conclRec == null) {
				return;
			}
			if (conclRec.inferenceIterator_.hasNext()) {
				// process the next inference
				toDo(conclRec.inferenceIterator_.next());
			} else {
				// conclusion is processed
				conclusionStack_.pop();
				LOGGER_.trace("{}: conclusion popped", conclRec.conclusion_);
				if (lowlink_.get(conclRec.conclusion_)
						.equals(index_.get(conclRec.conclusion_))) {
					// the smallest element of the component found, collect it
					List<C> component = new ArrayList<C>();
					int componentId = components_.size();
					for (;;) {
						C member = stack_.pop();
						component.add(member);
						lowlink_.remove(member);
						index_.put(member, componentId);
						if (member == conclRec.conclusion_) {
							// component is fully collected
							break;
						}
					}
					components_.add(component);
					LOGGER_.trace("component #{}: {}", componentId, component);
				}
			}
			InferenceRecord<C> infRec = inferenceStack_.peek();
			if (infRec == null) {
				return;
			}
			for (;;) {
				if (infRec.premiseIterator_.hasNext()) {
					C premise = infRec.premiseIterator_.next();
					if (index_.get(premise) == null) {
						// premise not yet processed
						toDo(premise);
						break;
					}
				} else {
					infRec = inferenceStack_.pop();
					LOGGER_.trace("{}: inference popped", infRec.inference_);
					// update conclusion id to the earliest of those reachable
					// on stack
					C conclusion = infRec.inference_.getConclusion();
					int conclusionLowLink = lowlink_.get(conclusion);
					for (C premise : infRec.inference_.getPremises()) {
						Integer premiseLowLink = lowlink_.get(premise);
						if (premiseLowLink != null
								&& premiseLowLink < conclusionLowLink) {
							// the premise is on the stack and reaches an
							// earlier element on the stack
							conclusionLowLink = premiseLowLink;
						}
					}
					lowlink_.put(conclusion, conclusionLowLink);
					break;
				}
			}
		}

	}

	private static class ConclusionRecord<C> {

		private final C conclusion_;

		private final Iterator<? extends Inference<C, ?>> inferenceIterator_;

		ConclusionRecord(C conclusion,
				StronglyConnectedComponentsComputation<C> computation) {
			this.conclusion_ = conclusion;
			this.inferenceIterator_ = computation.inferences_
					.getInferences(conclusion).iterator();
		}

	}

	private static class InferenceRecord<C> {

		Inference<C, ?> inference_;

		private final Iterator<? extends C> premiseIterator_;

		InferenceRecord(Inference<C, ?> inference) {
			this.inference_ = inference;
			this.premiseIterator_ = inference.getPremises().iterator();
		}

	}

}
