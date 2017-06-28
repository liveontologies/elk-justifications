package org.liveontologies.proofs.browser;

import javax.swing.tree.TreePath;

public interface TreeNodeLabelProvider {

	/**
	 * Returns label for the tree node <code>obj</code>.
	 * 
	 * @param obj
	 *            The tree node whose label should be returned.
	 * @param path
	 *            The path to the node if available, <code>null</code> if not.
	 * @return label for the tree node <code>obj</code>.
	 */
	String getLabel(Object obj, TreePath path);

}
