package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.adapters.InferenceSets;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.reasoner.tracing.TracingInferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class CollectStatisticsUsingElk {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(CollectStatisticsUsingElk.class);

	public static void main(final String[] args) {
		
		if (args.length < 3) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final String ontologyFileName = args[0];
		final String conclusionsFileName = args[1];
		final File recordFile = new File(args[2]);
		if (recordFile.exists()) {
			Utils.recursiveDelete(recordFile);
		}
		
		final ElkObjectBaseFactory factory = new ElkObjectBaseFactory();
		
		InputStream ontologyIS = null;
		BufferedReader conclusionReader = null;
		PrintWriter stats = null;
		
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
			
			stats = new PrintWriter(recordFile);
			stats.println("conclusion,"
					+ "nAxiomsInAllProofs,"
					+ "nConclusionsInAllProofs,"
					+ "nInferencesInAllProofs,"
					+ "isCycleInInferenceGraph,"
					+ "sizeOfMaxComponentInInferenceGraph,"
					+ "nNonSingletonComponentsInInferenceGraph");
			
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			
			String line;
			while ((line = conclusionReader.readLine()) != null) {
				
				final String[] columns = line.split(",");
				if (columns.length < 2) {
					return;
				}
				
				final String subIri = strip(columns[0]);
				final String supIri = strip(columns[1]);
				
				final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
						factory.getClass(new ElkFullIri(subIri)),
						factory.getClass(new ElkFullIri(supIri)));
				
				LOG.debug("Collecting statistics for {}", conclusion);
				
				collectStatistics(conclusion, reasoner, stats);
				
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
		} catch (final IOException e) {
			LOG.error("Error while reading the conclusion file!", e);
			System.exit(2);
		} finally {
			if (ontologyIS != null) {
				try {
					ontologyIS.close();
				} catch (final IOException e) {}
			}
			if (conclusionReader != null) {
				try {
					conclusionReader.close();
				} catch (final IOException e) {}
			}
			if (stats != null) {
				stats.close();
			}
		}
		
	}
	
	private static String strip(final String s) {
		final String trimmed = s.trim();
		int start = 0;
		if (trimmed.charAt(0) == '"') {
			start = 1;
		}
		int end = trimmed.length();
		if (trimmed.charAt(trimmed.length() - 1) == '"') {
			end = trimmed.length() - 1;
		}
		return trimmed.substring(start, end);
	}
	
	private static void collectStatistics(final ElkSubClassOfAxiom conclusion,
			final Reasoner reasoner, final PrintWriter stats)
					throws ElkException {
		
		stats.print("\"");
		stats.print(conclusion);
		stats.print("\"");
		
		final Conclusion expression = Utils
				.getFirstDerivedConclusionForSubsumption(reasoner, conclusion);
		final TracingInferenceSet inferenceSet =
				reasoner.explainConclusion(expression);
		
		final Set<ElkAxiom> axiomExprs =
				new HashSet<ElkAxiom>();
		final Set<Conclusion> lemmaExprs =
				new HashSet<Conclusion>();
		final Set<JustifiedInference<Conclusion, ElkAxiom>> inferences =
				new HashSet<JustifiedInference<Conclusion, ElkAxiom>>();
		
		Utils.traverseProofs(expression, inferenceSet,
				new Function<JustifiedInference<Conclusion, ElkAxiom>, Void>() {
					@Override
					public Void apply(
							final JustifiedInference<Conclusion, ElkAxiom> inf) {
						inferences.add(inf);
						return null;
					}
				},
				new Function<Conclusion, Void>() {
					@Override
					public Void apply(final Conclusion expr) {
						lemmaExprs.add(expr);
						return null;
					}
				},
				new Function<ElkAxiom, Void>() {
					@Override
					public Void apply(final ElkAxiom axiom) {
						axiomExprs.add(axiom);
						return null;
					}
				}
		);
		
		stats.print(",");
		stats.print(axiomExprs.size());
		stats.print(",");
		stats.print(lemmaExprs.size());
		stats.print(",");
		stats.print(inferences.size());
		stats.flush();
		
		final boolean hasCycle =
				InferenceSets.hasCycle(inferenceSet, expression);
		stats.print(",");
		stats.print(hasCycle);
		stats.flush();
		
		final StronglyConnectedComponents<Conclusion> components =
				StronglyConnectedComponentsComputation.computeComponents(
						inferenceSet, expression);
		
		final List<List<Conclusion>> comps = components.getComponents();
		final List<Conclusion> maxComp =
				Collections.max(comps, SIZE_COMPARATOR);
		stats.print(",");
		stats.print(maxComp.size());
		
		final Collection<List<Conclusion>> nonSingletonComps =
				Collections2.filter(comps, new Predicate<List<Conclusion>>() {
			@Override
			public boolean apply(final List<Conclusion> comp) {
				return comp.size() > 1;
			}
		});
		stats.print(",");
		stats.print(nonSingletonComps.size());
		
		stats.println();
		stats.flush();
		
	}
	
	private static final Comparator<Collection<?>> SIZE_COMPARATOR =
			new Comparator<Collection<?>>() {
		@Override
		public int compare(final Collection<?> o1, final Collection<?> o2) {
			return Integer.compare(o1.size(), o2.size());
		}
	};
	
}
