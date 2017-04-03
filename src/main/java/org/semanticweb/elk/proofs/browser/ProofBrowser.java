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

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.MinimalSubsetCollector;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.TracingInferenceJustifier;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
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
			
			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory().createReasoner(
					loader);
			
			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
					factory.getClass(new ElkFullIri(subFullIri)),
					factory.getClass(new ElkFullIri(supFullIri)));
			
			final Conclusion expression = Utils
					.getFirstDerivedConclusionForSubsumption(reasoner,
							conclusion);
			final InferenceSet<Conclusion> inferenceSet =
					reasoner.explainConclusion(expression);
			final TracingInferenceJustifier justifier =
					TracingInferenceJustifier.INSTANCE;

			final MinimalSubsetCollector<Conclusion, ElkAxiom> collector =
					new MinimalSubsetCollector<Conclusion, ElkAxiom>(
							BottomUpJustificationComputation
							.<Conclusion, ElkAxiom> getFactory(),
							inferenceSet, justifier);
			
			for (int size = startingSizeLimit; size <= Integer.MAX_VALUE; size++) {
				
				final int sizeLimit = size;
				
				final Collection<? extends Set<ElkAxiom>> justs =
						collector.collect(expression, sizeLimit);
				
				final TreeNodeLabelProvider decorator = new TreeNodeLabelProvider() {
					@Override
					public String getLabel(final Object obj,
							final TreePath path) {
						
						if (obj instanceof Conclusion) {
							final Conclusion c = (Conclusion) obj;
							final Collection<? extends Set<ElkAxiom>> js =
									collector.collect(c, sizeLimit);
							return "[" + js.size() + "] ";
						} else if (obj instanceof Inference) {
							final Inference<?> inf = (Inference<?>) obj;
							int product = 1;
							for (final Object premise : inf.getPremises()) {
								final Collection<? extends Set<ElkAxiom>> js =
										collector.collect((Conclusion) premise, sizeLimit);
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
						final Inference<?> inf = (Inference<?>) o;
						final Object c = inf.getConclusion();
						if (!(c instanceof Conclusion)) {
							return null;
						}
						final Conclusion concl = (Conclusion) c;
						
						final Collection<? extends Set<ElkAxiom>> premiseJs =
								collector.collect(premise, sizeLimit);
						final Collection<? extends Set<ElkAxiom>> conclJs =
								collector.collect(concl, sizeLimit);
						
						int countInf = 0;
						for (final Set<ElkAxiom> just : premiseJs) {
							if (Utils.isMinimal(just, conclJs)) {
								countInf++;
							}
						}
						
						int countGoal = 0;
						for (final Set<ElkAxiom> just : premiseJs) {
							if (Utils.isMinimal(just, justs)) {
								countGoal++;
							}
						}
						
						return "<html>minimal in inf conclusion: " + countInf +
								"<br/>minimal in goal: " + countGoal +
								"</html>";
					}
				};
				
				showProofBrowser(inferenceSet, justifier, expression,
						"size " + sizeLimit, decorator, toolTipProvider);
				
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
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final C conclusion) {
		showProofBrowser(inferenceSet, justifier, conclusion, null, null, null);
	}
	
	public static <C, A> void showProofBrowser(
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final C conclusion, final TreeNodeLabelProvider nodeDecorator,
			final TreeNodeLabelProvider toolTipProvider) {
		showProofBrowser(inferenceSet, justifier, conclusion, null,
				nodeDecorator, toolTipProvider);
	}
	
	public static <C, A> void showProofBrowser(
			final InferenceSet<C> inferenceSet,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final C conclusion, final String title,
			final TreeNodeLabelProvider nodeDecorator,
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
								inferenceSet, justifier, conclusion,
								nodeDecorator, toolTipProvider));
				frame.getContentPane().add(scrollPane);
				
				frame.pack();
//				frame.setSize(500, 500);
				frame.setVisible(true);
			}
		});
		
	}
	
}
