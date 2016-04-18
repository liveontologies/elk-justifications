package org.semanticweb.elk.justifications;

import java.util.Map;

public interface HasStatistics {

	String[] getStatNames();

	Map<String, Object> getStatistics();

	void logStatistics();

	void resetStatistics();

}
