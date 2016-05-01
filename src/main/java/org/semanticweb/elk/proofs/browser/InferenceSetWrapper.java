package org.semanticweb.elk.proofs.browser;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextArea;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

public class InferenceSetWrapper<C, A> implements Node {

	private final InferenceSet<C, A> inferenceSet_;
	private final C conclusion_;
	
	private final JComponent component_;
	
	public InferenceSetWrapper(final InferenceSet<C, A> inferenceSet,
			final C conclusion) {
		this.inferenceSet_ = inferenceSet;
		this.conclusion_ = conclusion;
		
		final String s = conclusion.toString();
		final String[] lines = s.split("(?<=[⊑=⊓])");
		final StringBuilder text = new StringBuilder(lines[0]);
		for (int i = 1; i < lines.length; i++) {
			text.append("\n").append(lines[i]);
		}
		
		final JTextArea textArea = new JTextArea(text.toString(),
				lines.length, 0);
		textArea.setEditable(false);
		final Dimension size = textArea.getMaximumSize();
		size.width = 0;
		for (final String line : lines) {
			final int width = textArea.getFontMetrics(
					textArea.getFont()).stringWidth(line);
			if (width > size.width) {
				size.width = width;
			}
		}
		textArea.setMaximumSize(size);
		
		this.component_ = textArea;
	}

	@Override
	public Collection<? extends Node> getChildren() {
		
		final ArrayList<InferenceWrapper> children =
				new ArrayList<InferenceWrapper>();
		for (final Inference<C, A> inf
				: inferenceSet_.getInferences(conclusion_)) {
			children.add(new InferenceWrapper(inf));
		}
		
		return children;
	}

	@Override
	public Component getComponent() {
		return component_;
	}

	public class InferenceWrapper implements Node {

		private final Inference<C, A> inference_;
		
		private final JComponent component_;
		
		InferenceWrapper(final Inference<C, A> inference) {
			this.inference_ = inference;
			
			this.component_ = new JButton("inf");
		}

		@Override
		public Collection<? extends Node> getChildren() {
			
			final Collection<? extends C> premises = inference_.getPremises();
			final Set<? extends A> axioms = inference_.getJustification();
			
			final ArrayList<Node> children =
					new ArrayList<Node>(premises.size() + axioms.size());
			for (final C premise : premises) {
				children.add(new InferenceSetWrapper<C, A>(inferenceSet_, premise));
			}
			for (final A axiom : axioms) {
				children.add(new JustificationWrapper(axiom));
			}
			
			return children;
		}

		@Override
		public Component getComponent() {
			return component_;
		}
		
	}
	
	public class JustificationWrapper implements Node {

		private final A justification_;
		
		private final JComponent component_;
		
		public JustificationWrapper(final A justification) {
			this.justification_ = justification;
			
			final String s = justification.toString();
			final String[] lines = s.split("\\s+");
			final StringBuilder text = new StringBuilder(lines[0]);
			for (int i = 1; i < lines.length; i++) {
				text.append("\n").append(lines[i]);
			}
			
			final JTextArea textArea = new JTextArea(text.toString(),
					lines.length, 0);
			textArea.setEditable(false);
			final Dimension size = textArea.getMaximumSize();
			size.width = 0;
			for (final String line : lines) {
				final int width = textArea.getFontMetrics(
						textArea.getFont()).stringWidth(line);
				if (width > size.width) {
					size.width = width;
				}
			}
			textArea.setMaximumSize(size);
			
			this.component_ = textArea;
		}

		@Override
		public Collection<? extends Node> getChildren() {
			return Collections.emptyList();
		}

		@Override
		public Component getComponent() {
			return component_;
		}
		
	}
	
}
