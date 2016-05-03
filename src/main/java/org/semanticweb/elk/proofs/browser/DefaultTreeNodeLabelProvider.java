package org.semanticweb.elk.proofs.browser;

import javax.swing.tree.TreePath;

public class DefaultTreeNodeLabelProvider implements TreeNodeLabelProvider {

	public static final DefaultTreeNodeLabelProvider INSTANCE = new DefaultTreeNodeLabelProvider();

	protected DefaultTreeNodeLabelProvider() {
		// Empty.
	}

	@Override
	public String getLabel(final Object obj, final TreePath path) {
		return obj.toString();
	}

}
