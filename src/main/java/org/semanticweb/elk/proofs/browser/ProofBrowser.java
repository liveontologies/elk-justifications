package org.semanticweb.elk.proofs.browser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
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
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.InferenceSets;
import org.semanticweb.elk.proofs.adapters.TracingInferenceSetInferenceSetAdapter;
import org.semanticweb.elk.proofs.adapters.DepthLimitInferenceSetAdapter.Wrap;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.saturation.conclusions.model.ClassConclusion;
import org.semanticweb.elk.reasoner.stages.RestartingStageExecutor;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

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
//			
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//					factory.getClass(new ElkFullIri(subFullIri)),
//					factory.getClass(new ElkFullIri(supFullIri)));
			
			// bad
			
			// [:GO_2000370] ⊑ <∃:RO_0002211>.:GO_0008150
			// Huge number of alternative inferences
			// 28 PgDown
			// Contains many cycles
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//					factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
//					factory.getObjectSomeValuesFrom(
//							factory.getObjectProperty(new ElkFullIri("http://purl.obolibrary.org/obo/RO_0002211")),
//							factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_0008150"))));
			
			// [:GO_2000370] ⊑ <∃:RO_0002211>.:GO_0009987
			// 15 PgDown
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
//			factory.getObjectSomeValuesFrom(
//					factory.getObjectProperty(new ElkFullIri("http://purl.obolibrary.org/obo/RO_0002211")),
//					factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_0009987"))));
			
			// good
			
			// [:GO_2000370] ⊑ -:GO_2000369
			// There is a tautology that has a huge alternative
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000369")));
			
			// [:GO_2000370] ⊑ -∃:RO_0002213.:GO_0006810
			// There was cut just through premises of many alternative infs
			// 8 PgDown
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
//			factory.getObjectSomeValuesFrom(
//					factory.getObjectProperty(new ElkFullIri("http://purl.obolibrary.org/obo/RO_0002213")),
//					factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_0006810"))));
			
			// [:GO_2000370] ⊑ +:GO_0030100
			// 24 PgDown
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_0030100")));
			
			// depth 5
			
			// [:GO_2000370] ⊑ +:GO_0050789
			// Huge alternatives
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_0050789")));
			
			// depth 9
			
			// bad
			
			// [:GO_2000370] ⊑ +∃:RO_0002211.:GO_0051179
			// Huge alternatives
//			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
//			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
//			factory.getObjectSomeValuesFrom(
//					factory.getObjectProperty(new ElkFullIri("http://purl.obolibrary.org/obo/RO_0002211")),
//					factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_0051179"))));
			
			// good
			
			// [:GO_2000370] ⊑ +∃:RO_0002211.:GO_0072583
			// A bit less alternatives :-P
			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
			factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_2000370")),
			factory.getObjectSomeValuesFrom(
					factory.getObjectProperty(new ElkFullIri("http://purl.obolibrary.org/obo/RO_0002211")),
					factory.getClass(new ElkFullIri("http://purl.obolibrary.org/obo/GO_0072583"))));
			
			final ClassConclusion expression = Utils
					.getFirstDerivedConclusionForSubsumption(reasoner,
							conclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner.explainConclusion(expression));
			
			final InferenceSet<Conclusion, Wrap<Conclusion, ElkAxiom>> limited =
					InferenceSets.limitDepth(inferenceSet, 9, expression);

//			final JustificationComputation<Conclusion, ElkAxiom> computation =
//					BottomUpJustificationComputation
//					.<Conclusion, ElkAxiom> getFactory()
//					.create(inferenceSet, DummyMonitor.INSTANCE);
			final JustificationComputation<Conclusion, Wrap<Conclusion, ElkAxiom>> computation =
					BottomUpJustificationComputation
					.<Conclusion, Wrap<Conclusion, ElkAxiom>> getFactory()
					.create(limited, DummyMonitor.INSTANCE);
			
			for (int size = startingSizeLimit; size <= Integer.MAX_VALUE; size++) {
				
//				decorateAndShowProofBrowser(inferenceSet, expression,
//						computation, size);
				decorateAndShowProofBrowser(limited, expression,
						computation, size);
				
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
	
	private static <C, A> void decorateAndShowProofBrowser(
			final InferenceSet<C, A> inferenceSet, final C conclusion,
			final JustificationComputation<C, A> computation,
			final int sizeLimit) {
		
		// I didn't find other way to distinguish between lemmas and axioms.
		final Set<Inference<C, A>> infs = new HashSet<>();
		final Set<A> axioms = new HashSet<>();
		final Set<C> concls = new HashSet<>();
		Utils.traverseProofs(conclusion, inferenceSet,
				new Function<Inference<C, A>, Void>() {
					@Override
					public Void apply(final Inference<C, A> inf) {
						infs.add(inf);
						return null;
					}
				},
				new Function<C, Void>() {
					@Override
					public Void apply(final C concl) {
						concls.add(concl);
						return null;
					}
				},
				new Function<A, Void>() {
					@Override
					public Void apply(final A axiom) {
						axioms.add(axiom);
						return null;
					}
				});
		
		final Collection<? extends Set<A>> justs =
				computation.computeJustifications(conclusion, sizeLimit);
		
		final TreeNodeLabelProvider decorator = new TreeNodeLabelProvider() {
			@SuppressWarnings("unchecked")
			@Override
			public String getLabel(final Object obj,
					final TreePath path) {
				
				try {
					if (obj instanceof Inference) {
						final Inference<?, ?> inf = (Inference<?, ?>) obj;
						int product = 1;
						for (final Object premise : inf.getPremises()) {
							final Collection<? extends Set<A>> js =
									computation.computeJustifications((C) premise, sizeLimit);
							product *= js.size();
						}
						return "<" + product + "> ";
					} else {
						final C c = (C) obj;
						if (concls.contains(c)) {
							final Collection<? extends Set<A>> js =
									computation.computeJustifications(c, sizeLimit);
							return "[" + js.size() + "] ";
						}
					}
				} catch (final ClassCastException e) {
					// obj was not of type C
				}
				
				return "";
			}
		};
		
		final TreeNodeLabelProvider toolTipProvider = new TreeNodeLabelProvider() {
			@SuppressWarnings("unchecked")
			@Override
			public String getLabel(final Object obj,
					final TreePath path) {
				
				if (path == null || path.getPathCount() < 2) {
					return null;
				}
				boolean isConclusion = false;
				try {
					final C premise = (C) obj;
					if (concls.contains(premise)) {
						isConclusion = true;
						final Object o = path.getPathComponent(path.getPathCount() - 2);
						if (!(o instanceof Inference)) {
							return null;
						}
						final Inference<?, ?> inf = (Inference<?, ?>) o;
						final Object c = inf.getConclusion();
						if (!(c instanceof Conclusion)) {
							return null;
						}
						final C concl = (C) c;
						
						final Collection<? extends Set<A>> premiseJs =
								computation.computeJustifications(premise, sizeLimit);
						final Collection<? extends Set<A>> conclJs =
								computation.computeJustifications(concl, sizeLimit);
						
						int countInf = 0;
//<<<<<<< HEAD
//						for (final Set<A> just : premiseJs) {
//							if (BottomUpJustificationComputation.isMinimal(just, conclJs)) {
//=======
						for (final Set<A> just : premiseJs) {
							if (Utils.isMinimal(just, conclJs)) {
//>>>>>>> master
								countInf++;
							}
						}
						
						int countGoal = 0;
//<<<<<<< HEAD
//						for (final Set<A> just : premiseJs) {
//							if (BottomUpJustificationComputation.isMinimal(just, justs)) {
//=======
						for (final Set<A> just : premiseJs) {
							if (Utils.isMinimal(just, justs)) {
//>>>>>>> master
								countGoal++;
							}
						}
						
						return "<html>minimal in inf conclusion: " + countInf +
								"<br/>minimal in goal: " + countGoal +
								"</html>";
					}
				} catch (final ClassCastException e) {
					// obj was not of type C
				}
				
				if (!isConclusion) {
					try {
						final A axiom = (A) obj;
						if (axioms.contains(axiom)) {
							for (final Set<A> just : justs) {
								if (just.contains(axiom)) {
									return "Used in some justification";
								}
							}
							return "Not used in any justification";
						}
					} catch (final ClassCastException e) {
						// obj was not of type A
					}
				}
				
				// TODO .: product of premise-minimal justs for infs
				return null;
			}
		};
		
		showProofBrowser(inferenceSet, conclusion, "size " + sizeLimit,
				decorator, toolTipProvider);
		
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
