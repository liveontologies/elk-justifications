package org.semanticweb.elk.justifications;

import org.liveontologies.puli.justifications.InterruptMonitor;

public class DummyMonitor implements InterruptMonitor {

	public static final DummyMonitor INSTANCE = new DummyMonitor();
	
	@Override
	public boolean isInterrupted() {
		return false;
	}

}
