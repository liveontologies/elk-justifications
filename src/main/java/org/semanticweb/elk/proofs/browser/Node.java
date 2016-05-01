package org.semanticweb.elk.proofs.browser;

import java.awt.Component;
import java.util.Collection;

public interface Node {
	
	Collection<? extends Node> getChildren();
	
	Component getComponent();
	
}
