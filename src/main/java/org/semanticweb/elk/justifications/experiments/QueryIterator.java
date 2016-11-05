package org.semanticweb.elk.justifications.experiments;

public abstract class QueryIterator<Q> {

	protected final QueryFactory<Q> factory_;
	
	public QueryIterator(final QueryFactory<Q> factory) {
		this.factory_ = factory;
	}

	public abstract boolean hasNext();
	
	public abstract Q next() throws ExperimentException;
	
	public void dispose() {
		// Empty.
	};
	
}
