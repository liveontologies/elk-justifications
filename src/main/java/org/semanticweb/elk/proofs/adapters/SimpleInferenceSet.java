package org.semanticweb.elk.proofs.adapters;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

/**
 * A simple implementation of {@link InferenceSet} backed by an
 * {@link ArrayListMultimap}
 * 
 * @author Yevgeny Kazakov
 *
 * @param <C>
 *            the type of conclusion and premises used by the inferences
 * @param <A>
 *            the type of axioms used by the inferences
 */
public class SimpleInferenceSet<C, A> implements InferenceSet<C, A> {

	private final ListMultimap<C, Inference<C, A>> inferences_ = ArrayListMultimap
			.create();

	@Override
	public Iterable<Inference<C, A>> getInferences(C conclusion) {
		return inferences_.get(conclusion);
	}

	public void addInference(Inference<C, A> inf) {
		inferences_.put(inf.getConclusion(), inf);
	}

}
