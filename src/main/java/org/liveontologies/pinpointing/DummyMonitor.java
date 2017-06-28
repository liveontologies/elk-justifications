package org.liveontologies.pinpointing;

import org.liveontologies.puli.pinpointing.InterruptMonitor;

public class DummyMonitor implements InterruptMonitor {

	public static final DummyMonitor INSTANCE = new DummyMonitor();
	
	@Override
	public boolean isInterrupted() {
		return false;
	}

}
