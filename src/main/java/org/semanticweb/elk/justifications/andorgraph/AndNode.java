package org.semanticweb.elk.justifications.andorgraph;

public interface AndNode<A> extends Node<A> {

	public static interface Visitor<A, O> {
		O visit(AndNode<A> node);
	}

}
