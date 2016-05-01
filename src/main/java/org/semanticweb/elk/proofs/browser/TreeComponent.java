package org.semanticweb.elk.proofs.browser;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;

import javax.swing.Box;
import javax.swing.JComponent;

public class TreeComponent extends JComponent {
	private static final long serialVersionUID = -3006368290791929705L;

	private final Node node_;
	private final Config config_;
	
	private boolean showBranches_ = false;
	
	public TreeComponent(final Node node, final Config config) {
		
		this.node_ = node;
		this.config_ = config;
		
		final Collection<? extends Node> children = node.getChildren();
		
		setLayout(new GridBagLayout());
		final GridBagConstraints rootConstraints = new GridBagConstraints();
		rootConstraints.fill = GridBagConstraints.HORIZONTAL;
		rootConstraints.anchor = GridBagConstraints.PAGE_START;
		rootConstraints.weightx = 1.0;
//		rootConstraints.ipady = config.rootIpady;
		rootConstraints.insets = new Insets(10, 10, 10, 10);
		
		rootConstraints.gridx = 0;
		rootConstraints.gridy = 0;
		rootConstraints.gridwidth = children.size();
		final Box rootBox = Box.createHorizontalBox();
		rootBox.add(Box.createHorizontalGlue());
		rootBox.add(node.getComponent());
		rootBox.add(Box.createHorizontalGlue());
		add(rootBox, rootConstraints);
		
		node.getComponent().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				toggleShowBranches();
			}
		});
		
	}
	
	public TreeComponent(final Node node) {
		this(node, new Config());
	}
	
	public boolean toggleShowBranches() {
		
		final Collection<? extends Node> children = node_.getChildren();
		
		if (showBranches_) {
			showBranches_ = false;
			
			final Component[] components = getComponents();
			for (final Component component : components) {
				if (component instanceof TreeComponent) {
					remove(component);
				}
			}
			
		} else {
			showBranches_ = true;
			
			final GridBagConstraints childConstraints = new GridBagConstraints();
			childConstraints.fill = GridBagConstraints.HORIZONTAL;
			childConstraints.anchor = GridBagConstraints.PAGE_START;
			childConstraints.weightx = 1.0;
			childConstraints.gridy = 1;
			int maxPreferredHeight = 0;
			for (final Node child : children) {
				final Dimension preferredSize =
						child.getComponent().getPreferredSize();
				if (preferredSize.height > maxPreferredHeight) {
					maxPreferredHeight = preferredSize.height;
				}
			}
			
			int i = 0;
			for (final Node child : children) {
				childConstraints.gridx = i++;
				final Config childConfig = config_.clone();
				final Dimension preferredSize =
						child.getComponent().getPreferredSize();
				childConfig.rootIpady =
						(maxPreferredHeight - preferredSize.height) / 2;
				add(new TreeComponent(child, childConfig), childConstraints);
			}
			
		}
		revalidate();
		
		return showBranches_;
	}
	
	@Override
	protected void paintComponent(final Graphics g) {
		super.paintComponent(g);
		
		final Component[] components = getComponents();
		final Component rootComponent = components[0];
		
		final int rootMidX =
				rootComponent.getX() + (rootComponent.getWidth() / 2);
		final int rootMidY =
				rootComponent.getY() + (rootComponent.getHeight() / 2);
		
		if (showBranches_) {
			// The first component is the root, so we start from the second one.
			for (int i = 1; i < components.length; i++) {
				
				final Component branchComponent = components[i];// TODO: This is actually the Box !!!
				final Component childComponent =
						((Container) branchComponent).getComponent(0);
				final int childMidInBranchX = childComponent.getX()
						+ (childComponent.getWidth() / 2);
				final int childMidInBranchY = childComponent.getY()
						+ (childComponent.getHeight() / 2);
				
				g.drawLine(
						rootMidX,
						rootMidY,
						childMidInBranchX + branchComponent.getX(),
						childMidInBranchY + branchComponent.getY());
				
			}
		}
//		
//		// TODO DEBUG
//		final Color oldColor = g.getColor();
//		g.setColor(Color.BLUE);
//		final Dimension size = getSize();
//		g.drawRect(0, 0, size.width - 1, size.height - 1);
//		g.setColor(oldColor);
		
	}
	
	public static class Config implements Cloneable {
		
		public int orientation;// TODO :-P
		
		int rootIpady = 0;
		
		@Override
		public Config clone() {
			try {
				return (Config) super.clone();
			} catch (final CloneNotSupportedException e) {
				throw new RuntimeException(e);// TODO: really ?!?!?!
			}
		}
		
	}
	
}
