package org.semanticweb.elk.proofs.browser;

import java.awt.Color;
import java.awt.Component;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;

import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

import com.google.common.collect.HashMultimap;

public class InferenceSetTreeComponent<C, A> extends JTree {
	private static final long serialVersionUID = 8406872780618425810L;

	private final InferenceSet<C, A> inferenceSet_;
	private final C conclusion_;
	private final HashMultimap<Object, TreePath> visibleNodes_;

	public InferenceSetTreeComponent(final InferenceSet<C, A> inferenceSet,
			final C conclusion) {
		this.inferenceSet_ = inferenceSet;
		this.conclusion_ = conclusion;
		this.visibleNodes_ = HashMultimap.create();

		setModel(new TreeModelInferenceSetAdapter());

		setEditable(true);

		final TreeCellRenderer renderer = new TreeCellRenderer();
		renderer.setLeafIcon(null);
		renderer.setOpenIcon(null);
		renderer.setClosedIcon(null);
		setCellRenderer(renderer);

		resetVisibleNodes();

		// Need to know what will be visible before it gets displayed.
		addTreeWillExpandListener(new TreeWillExpandListener() {

			@Override
			public void treeWillExpand(final TreeExpansionEvent event)
					throws ExpandVetoException {

				final TreePath path = event.getPath();
				final Object parent = path.getLastPathComponent();
				final int count = getModel().getChildCount(parent);
				for (int i = 0; i < count; i++) {
					final Object child = getModel().getChild(parent, i);
					if (!(child instanceof Inference)) {
						visibleNodes_.put(child, path.pathByAddingChild(child));
					}
				}

			}

			@Override
			public void treeWillCollapse(final TreeExpansionEvent event)
					throws ExpandVetoException {

				final TreePath path = event.getPath();
				final Object parent = path.getLastPathComponent();
				final int count = getModel().getChildCount(parent);
				for (int i = 0; i < count; i++) {
					final Object child = getModel().getChild(parent, i);
					if (!(child instanceof Inference)) {
						visibleNodes_.remove(child,
								path.pathByAddingChild(child));
					}
				}

			}
		});

	}

	private void resetVisibleNodes() {
		visibleNodes_.clear();
		final Object root = getModel().getRoot();
		final TreePath rootPath = new TreePath(root);
		if (isRootVisible()) {
			visibleNodes_.put(root, rootPath);
		}

		final Enumeration<TreePath> expanded = getExpandedDescendants(rootPath);
		if (expanded != null) {
			while (expanded.hasMoreElements()) {
				final TreePath path = expanded.nextElement();
				final Object parent = path.getLastPathComponent();
				final int count = getModel().getChildCount(parent);
				for (int i = 0; i < count; i++) {
					final Object child = getModel().getChild(parent, i);
					if (!(child instanceof Inference)) {
						visibleNodes_.put(child, path.pathByAddingChild(child));
					}
				}
			}
		}
	}

	@Override
	public String convertValueToText(final Object value, final boolean selected,
			final boolean expanded, final boolean leaf, final int row,
			final boolean hasFocus) {

		if (value == null) {
			return "";
		}

		final String label = value.toString();
		if (label == null) {
			return "";
		}

		if (value instanceof Inference) {
			return "⊣";
		} else {
			return label;
		}
	}

	private class TreeModelInferenceSetAdapter implements TreeModel {

		@Override
		public Object getRoot() {
			return conclusion_;
		}

		@Override
		public Object getChild(final Object parent, final int index) {
			if (parent instanceof Inference) {
				final Inference<?, ?> inf = (Inference<?, ?>) parent;
				int i = 0;
				for (final Object premise : inf.getPremises()) {
					if (i == index) {
						return premise;
					}
					i++;
				}
				for (final Object axiom : inf.getJustification()) {
					if (i == index) {
						return axiom;
					}
					i++;
				}
			} else {
				try {
					/*
					 * Whether parent is a conclusion or an axiom can be
					 * determined only by trying and catching
					 * ClassCastException.
					 */
					@SuppressWarnings("unchecked")
					final Iterable<Inference<C, A>> inferences = inferenceSet_
							.getInferences((C) parent);
					int i = 0;
					for (final Inference<C, A> inf : inferences) {
						if (i == index) {
							return inf;
						}
						i++;
					}
				} catch (final ClassCastException e) {
					// parent is an axiom, so return null.
				}
			}
			return null;
		}

		@Override
		public int getChildCount(final Object parent) {
			if (parent instanceof Inference) {
				final Inference<?, ?> inf = (Inference<?, ?>) parent;
				return inf.getPremises().size() + inf.getJustification().size();
			} else {
				try {
					/*
					 * Whether parent is a conclusion or an axiom can be
					 * determined only by trying and catching
					 * ClassCastException.
					 */
					@SuppressWarnings("unchecked")
					final Iterator<Inference<C, A>> inferenceIterator = inferenceSet_
							.getInferences((C) parent).iterator();
					int i = 0;
					while (inferenceIterator.hasNext()) {
						inferenceIterator.next();
						i++;
					}
					return i;
				} catch (final ClassCastException e) {
					// parent is an axiom, so return 0.
					return 0;
				}
			}
		}

		@Override
		public boolean isLeaf(final Object node) {
			if (node instanceof Inference) {
				final Inference<?, ?> inf = (Inference<?, ?>) node;
				return inf.getPremises().isEmpty()
						&& inf.getJustification().isEmpty();
			} else {
				try {
					/*
					 * Whether node is a conclusion or an axiom can be
					 * determined only by trying and catching
					 * ClassCastException.
					 */
					@SuppressWarnings("unchecked")
					final Iterable<Inference<C, A>> inferences = inferenceSet_
							.getInferences((C) node);
					return !inferences.iterator().hasNext();
				} catch (final ClassCastException e) {
					// node is an axiom, so return true.
					return true;
				}
			}
		}

		@Override
		public int getIndexOfChild(final Object parent, final Object child) {
			if (parent == null || child == null) {
				return -1;
			}
			if (parent instanceof Inference) {
				final Inference<?, ?> inf = (Inference<?, ?>) parent;
				int i = 0;
				for (final Object premise : inf.getPremises()) {
					if (child.equals(premise)) {
						return i;
					}
					i++;
				}
				for (final Object axiom : inf.getJustification()) {
					if (child.equals(axiom)) {
						return i;
					}
					i++;
				}
			} else {
				try {
					/*
					 * Whether parent is a conclusion or an axiom can be
					 * determined only by trying and catching
					 * ClassCastException.
					 */
					@SuppressWarnings("unchecked")
					final Iterable<Inference<C, A>> inferences = inferenceSet_
							.getInferences((C) parent);
					int i = 0;
					for (final Inference<C, A> inf : inferences) {
						// if (child.equals(inf)) {
						// FIXME: equals does not work for inferences
						if (child.toString().equals(inf.toString())) {
							return i;
						}
						i++;
					}
				} catch (final ClassCastException e) {
					// parent is an axiom, so return -1.
				}
			}
			return -1;
		}

		@Override
		public void valueForPathChanged(TreePath path, Object newValue) {
			// The tree is immutable, so no change is possible.
		}

		@Override
		public void addTreeModelListener(TreeModelListener l) {
			// The tree is immutable, so listeners never fire.
		}

		@Override
		public void removeTreeModelListener(TreeModelListener l) {
			// The tree is immutable, so listeners never fire.
		}

	}

	private class TreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -711871019527222465L;

		@Override
		public Component getTreeCellRendererComponent(final JTree tree,
				final Object value, final boolean sel, final boolean expanded,
				final boolean leaf, final int row, final boolean hasFocus) {

			final Component component = super.getTreeCellRendererComponent(tree,
					value, sel, expanded, leaf, row, hasFocus);

			// If the value is displayed multiple times, highlight it
			final Set<TreePath> paths = visibleNodes_.get(value);
			if (paths.size() > 1) {
				setBackgroundNonSelectionColor(colorFromHash(value));
			} else {
				setBackgroundNonSelectionColor(null);
			}

			return component;
		}

	}

	private static Color colorFromHash(final Object obj) {
		return new Color(Color.HSBtoRGB(obj.hashCode() / 7919.0f, 0.5f, 0.9f));
	}

}
