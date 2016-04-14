package org.semanticweb.elk.justifications.experiments;

public class Record {
	
	public final long time;
	public final int nJust;
	
	public Record(final long time, final int nJust) {
		this.time = time;
		this.nJust = nJust;
	}
	
	@Override
	public String toString() {
		return getClass().getSimpleName() + "(time=" + time + ",nJust=" + nJust
				+ ")";
	}
	
}
