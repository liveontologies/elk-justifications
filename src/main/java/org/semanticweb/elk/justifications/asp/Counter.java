package org.semanticweb.elk.justifications.asp;

public class Counter {
	
	private int counter;
	
	public Counter() {
		this(0);
	}
	
	public Counter(final int first) {
		this.counter = first;
	}
	
	public int next() {
		return counter++;
	}
	
}
