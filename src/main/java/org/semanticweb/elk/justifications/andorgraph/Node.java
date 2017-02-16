package org.semanticweb.elk.justifications.andorgraph;

import java.util.Collection;

public interface Node<A> {

	Collection<? extends Node<A>> getParents();

	A getInitial();

	<O> O accept(Visitor<A, O> visitor);

	public static interface Visitor<A, O>
			extends AndNode.Visitor<A, O>, OrNode.Visitor<A, O> {
		// Combined interface.
	}

}
