package org.semanticweb.elk.proofs.browser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.tree.TreePath;

import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.DummyMonitor;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.TracingInferenceSetInferenceSetAdapter;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.saturation.conclusions.model.ClassConclusion;
import org.semanticweb.elk.reasoner.stages.RestartingStageExecutor;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProofBrowser {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(ProofBrowser.class);
	
	public static void main(final String[] args) {
		
		if (args.length < 3) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final int startingSizeLimit = Math.max(1, Integer.parseInt(args[0]));
		final String ontologyFileName = args[1];
		final String subFullIri = args[2];
		final String supFullIri = args[3];
		
		final ElkObjectBaseFactory factory = new ElkObjectBaseFactory();
		
		InputStream ontologyIS = null;
		
		try {
			
			ontologyIS = new FileInputStream(ontologyFileName);
			
			final AxiomLoader ontologyLoader = new Owl2StreamLoader(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory().createReasoner(
					ontologyLoader, new RestartingStageExecutor());
			
			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
					factory.getClass(new ElkFullIri(subFullIri)),
					factory.getClass(new ElkFullIri(supFullIri)));
			
			final ClassConclusion expression =
					reasoner.getConclusion(conclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner.explainConclusion(expression));

			final JustificationComputation<Conclusion, ElkAxiom> computation =
					BottomUpJustificationComputation
					.<Conclusion, ElkAxiom> getFactory()
					.create(inferenceSet, DummyMonitor.INSTANCE);
			
			for (int size = startingSizeLimit; size <= Integer.MAX_VALUE; size++) {
				
				final int sizeLimit = size;
				
				final Collection<? extends Set<ElkAxiom>> justs =
						computation.computeJustifications(expression, sizeLimit);
				
				final TreeNodeLabelProvider decorator = new TreeNodeLabelProvider() {
					@Override
					public String getLabel(final Object obj,
							final TreePath path) {
						
						if (obj instanceof Conclusion) {
							final Conclusion c = (Conclusion) obj;
							final Collection<? extends Set<ElkAxiom>> js =
									computation.computeJustifications(c, sizeLimit);
							return "[" + js.size() + "] ";
						} else if (obj instanceof Inference) {
							final Inference<?, ?> inf = (Inference<?, ?>) obj;
							int product = 1;
							for (final Object premise : inf.getPremises()) {
								final Collection<? extends Set<ElkAxiom>> js =
										computation.computeJustifications((Conclusion) premise, sizeLimit);
								product *= js.size();
							}
							return "<" + product + "> ";
						}
						
						return "";
					}
				};
				
				final TreeNodeLabelProvider toolTipProvider = new TreeNodeLabelProvider() {
					@Override
					public String getLabel(final Object obj,
							final TreePath path) {
						
						if (path == null || path.getPathCount() < 2
								|| !(obj instanceof Conclusion)) {
							return null;
						}
						final Conclusion premise = (Conclusion) obj;
						final Object o = path.getPathComponent(path.getPathCount() - 2);
						if (!(o instanceof Inference)) {
							return null;
						}
						final Inference<?, ?> inf = (Inference<?, ?>) o;
						final Object c = inf.getConclusion();
						if (!(c instanceof Conclusion)) {
							return null;
						}
						final Conclusion concl = (Conclusion) c;
						
						final Collection<? extends Set<ElkAxiom>> premiseJs =
								computation.computeJustifications(premise, sizeLimit);
						final Collection<? extends Set<ElkAxiom>> conclJs =
								computation.computeJustifications(concl, sizeLimit);
						
						int countInf = 0;
						for (final Set<ElkAxiom> just : premiseJs) {
							if (BottomUpJustificationComputation.isMinimal(just, conclJs)) {
								countInf++;
							}
						}
						
						int countGoal = 0;
						for (final Set<ElkAxiom> just : premiseJs) {
							if (BottomUpJustificationComputation.isMinimal(just, justs)) {
								countGoal++;
							}
						}
						
						return "<html>minimal in inf conclusion: " + countInf +
								"<br/>minimal in goal: " + countGoal +
								"</html>";
					}
				};
				
				showProofBrowser(inferenceSet, expression, "size " + sizeLimit,
						decorator, toolTipProvider);
				
				try {
					System.out.print("Press ENTER to continue: ");
					for (;;) {
						final int ch = System.in.read();
						if (ch == 10) {
							break;
						}
					}
					
				} catch (final IOException e) {
					LOG.error("Error during input!", e);
					break;
				}
				
			}
			
		} catch (final FileNotFoundException e) {
			LOG.error("File Not Found!", e);
			System.exit(2);
		} catch (final ElkInconsistentOntologyException e) {
			LOG.error("The ontology is inconsistent!", e);
			System.exit(2);
		} catch (final ElkException e) {
			LOG.error("Could not classify the ontology!", e);
			System.exit(2);
		} finally {
			if (ontologyIS != null) {
				try {
					ontologyIS.close();
				} catch (final IOException e) {}
			}
		}
		
	}
	
	public static <C, A> void showProofBrowser(
			final InferenceSet<C, A> inferenceSet, final C conclusion) {
		showProofBrowser(inferenceSet, conclusion, null, null, null);
	}
	
	public static <C, A> void showProofBrowser(
			final InferenceSet<C, A> inferenceSet, final C conclusion,
			final TreeNodeLabelProvider nodeDecorator,
			final TreeNodeLabelProvider toolTipProvider) {
		showProofBrowser(inferenceSet, conclusion, null, nodeDecorator,
				toolTipProvider);
	}
	
	public static <C, A> void showProofBrowser(
			final InferenceSet<C, A> inferenceSet, final C conclusion,
			final String title, final TreeNodeLabelProvider nodeDecorator,
			final TreeNodeLabelProvider toolTipProvider) {
		
		final StringBuilder message = new StringBuilder("Change Look and Feel by adding one of the following properties:");
		for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
			message.append("\nswing.defaultlaf=").append(info.getClassName());
		}
		LOG.info(message.toString());
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				final JFrame frame;
				if (title == null) {
					frame = new JFrame("Proof Browser - " + conclusion);
				} else {
					frame = new JFrame("Proof Browser - " + title);
				}
				
				final JScrollPane scrollPane =
						new JScrollPane(new InferenceSetTreeComponent<C, A>(
								inferenceSet, conclusion, nodeDecorator,
								toolTipProvider));
				frame.getContentPane().add(scrollPane);
				
				frame.pack();
//				frame.setSize(500, 500);
				frame.setVisible(true);
			}
		});
		
	}
	
	public static <C, A> void showProofGraphBrowser(
			final InferenceSet<C, A> inferenceSet, final C conclusion,
			final String title) {

		final InferenceSetWrapper<C, A> root =
				new InferenceSetWrapper<C, A>(inferenceSet, conclusion);
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				final JFrame frame;
				if (title == null) {
					frame = new JFrame("Proof Browser - " + conclusion);
				} else {
					frame = new JFrame("Proof Browser - " + title);
				}
				
				final JScrollPane scrollPane =
						new JScrollPane(new TreeComponent(root));
				frame.getContentPane().add(scrollPane);
				
				frame.pack();
//				frame.setSize(500, 500);
				frame.setVisible(true);
			}
		});
		
	}
	
}
