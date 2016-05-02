package org.semanticweb.elk.proofs.browser;

import java.util.Iterator;

import javax.swing.JTree;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

public class InferenceSetTreeComponent<C, A> extends JTree {
	private static final long serialVersionUID = 8406872780618425810L;

	private final InferenceSet<C, A> inferenceSet_;
	private final C conclusion_;

	public InferenceSetTreeComponent(final InferenceSet<C, A> inferenceSet,
			final C conclusion) {
		this.inferenceSet_ = inferenceSet;
		this.conclusion_ = conclusion;

		setModel(new TreeModelInferenceSetAdapter());

		setEditable(true);
		
		DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
		renderer.setLeafIcon(null);
		renderer.setOpenIcon(null);
		renderer.setClosedIcon(null);
		setCellRenderer(renderer);
		
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
					return !inferenceSet_.getInferences((C) node).iterator()
							.hasNext();
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
					int i = 0;
					for (final Inference<C, A> inf : inferenceSet_
							.getInferences((C) parent)) {
//						if (child.equals(inf)) {
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

}
