package org.semanticweb.elk.justifications;

public class DummyMonitor implements Monitor {

	public static final DummyMonitor INSTANCE = new DummyMonitor();
	
	@Override
	public boolean isCancelled() {
		return false;
	}

}
