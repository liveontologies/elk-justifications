package org.semanticweb.elk.proofs.browser;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
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
		
		final String ontologyFileName = args[0];
		final String subFullIri = args[1];
		final String supFullIri = args[2];
		
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

			showProofBrowser(inferenceSet, expression);
			
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
		showProofBrowser(inferenceSet, conclusion, null);
	}
	
	public static <C, A> void showProofBrowser(
			final InferenceSet<C, A> inferenceSet, final C conclusion,
			final String title) {
		
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
								inferenceSet, conclusion));
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
