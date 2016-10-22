package org.semanticweb.elk.justifications.asp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Index<T> {
	
	private final Counter counter;
	
	private final Map<T, Integer> index = new HashMap<>();
	
	public Index(final int firstIndex) {
		this.counter = new Counter(firstIndex);
	}
	
	public Index() {
		this(0);
	}
	
	public int get(final T arg) {
		Integer result = index.get(arg);
		if (result == null) {
			result = counter.next();
			index.put(arg, result);
		}
		return result;
	}
	
	public Map<T, Integer> getIndex() {
		return Collections.unmodifiableMap(index);
	}
	
}
