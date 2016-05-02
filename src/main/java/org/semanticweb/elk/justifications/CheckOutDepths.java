package org.semanticweb.elk.justifications;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.DepthLimitInferenceSetAdapter.Wrap;
import org.semanticweb.elk.proofs.browser.ProofBrowser;
import org.semanticweb.elk.proofs.adapters.InferenceSets;
import org.semanticweb.elk.proofs.adapters.TracingInferenceSetInferenceSetAdapter;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.saturation.conclusions.model.ClassConclusion;
import org.semanticweb.elk.reasoner.stages.RestartingStageExecutor;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class CheckOutDepths {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(CheckOutDepths.class);
	
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

			checkOutDepths(inferenceSet, expression);
			
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
	
	public static <C, A> void checkOutDepths(
			final InferenceSet<C, A> original, final C conclusion) {
		
		ProofBrowser.showProofBrowser(original, conclusion);
		
		for (int maxDepth = 1; maxDepth <= 10; maxDepth++) {// TODO: actual proof depth !!!
			LOG.info("Checking depth: {}", maxDepth);
			
			LOG.info("limiting depth");
			final InferenceSet<C, Wrap<C, A>> limited =
					InferenceSets.limitDepth(original, maxDepth, conclusion);
			
			ProofBrowser.showProofBrowser(limited, conclusion);
//			ProofBrowser.showProofBrowser(limited, conclusion, "limited to " + maxDepth);
			
			LOG.info("collecting leaves");
			final Set<Wrap<C, A>> leaves = new HashSet<Wrap<C, A>>();
			Utils.traverseProofs(conclusion, limited,
					new Function<Inference<C, Wrap<C, A>>, Void>() {
						@Override
						public Void apply(final Inference<C, Wrap<C, A>> inf) {
							leaves.addAll(inf.getJustification());
							return null;
						}
					},
					Functions.<C>identity(),
					Functions.<Wrap<C, A>>identity());
			
			final JustificationComputation<C, Wrap<C, A>> computation =
					BottomUpJustificationComputation.<C, Wrap<C, A>>getFactory()
					.create(limited, new DummyMonitor());
			
			LOG.info("computing justifications");
			final Collection<? extends Set<Wrap<C, A>>> justifications =
					computation.computeJustifications(conclusion);
			
			for (final Set<Wrap<C, A>> justification : justifications) {
				leaves.removeAll(justification);
			}
			
			if (leaves.isEmpty()) {
				LOG.info("ALL LEAVES USED IN SOME JUSTIFICATION");
			} else {
				LOG.info("{} UNUSED LEAVES: {}", leaves.size(), leaves);
			}
			
			System.out.println("Press any key to continue.");
			try {
				System.in.read();
			} catch (IOException e) {}
		}
		
	}
	
}
