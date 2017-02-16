package org.semanticweb.elk.justifications.andorgraph;

public interface OrNode<A> extends Node<A> {

	public static interface Visitor<A, O> {
		O visit(OrNode<A> node);
	}

}
