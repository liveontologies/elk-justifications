package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.semanticweb.elk.proofs.adapters.DirectSatEncodingProofAdapter;
import org.semanticweb.elk.proofs.adapters.Proofs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;

public class CollectStatisticsUsingDirectSat {

	private static final Logger LOG = LoggerFactory
			.getLogger(CollectStatisticsUsingDirectSat.class);

	public static final String ENCODING_NAME = "encoding";
	public static final String SUFFIX_QUERY = ".query";
	public static final String SUFFIX_QUESTION = ".question";
	public static final String SUFFIX_CNF = ".cnf";
	public static final String SUFFIX_ASSUMPTIONS = ".assumptions";

	public static void main(final String[] args) {

		if (args.length < 2) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}

		final String inputDirName = args[0];
		final File recordFile = new File(args[1]);
		if (recordFile.exists()) {
			Utils.recursiveDelete(recordFile);
		}

		final InferenceJustifier<Integer, ? extends Set<? extends Integer>> justifier = DirectSatEncodingProofAdapter.JUSTIFIER;

		final File[] queryDirs = new File(inputDirName).listFiles();
		Arrays.sort(queryDirs);

		PrintWriter stats = null;
		try {

			stats = new PrintWriter(recordFile);
			stats.println("query," + "nAxiomsInAllProofs,"
					+ "nConclusionsInAllProofs," + "nInferencesInAllProofs,"
					+ "isCycleInInferenceGraph,"
					+ "sizeOfMaxComponentInInferenceGraph,"
					+ "nNonSingletonComponentsInInferenceGraph");

			for (final File queryDir : queryDirs) {

				BufferedReader queryReader = null;
				BufferedReader questionReader = null;
				InputStream cnf = null;
				InputStream assumptions = null;
				try {

					queryReader = new BufferedReader(new FileReader(
							new File(queryDir, ENCODING_NAME + SUFFIX_QUERY)));
					final String query = queryReader.readLine();
					if (query == null) {
						throw new IOException(
								"Could not read query file in: " + queryDir);
					}
					// else

					LOG.debug("Collecting statistics for {}", query);

					stats.print("\"");
					stats.print(query);
					stats.print("\"");

					questionReader = new BufferedReader(new FileReader(new File(
							queryDir, ENCODING_NAME + SUFFIX_QUESTION)));
					final String line = questionReader.readLine();
					if (line == null) {
						throw new IOException(
								"Could not read question file in: " + queryDir);
					}
					final int question = -Integer
							.valueOf(line.split("\\s+")[0]);

					cnf = new FileInputStream(
							new File(queryDir, ENCODING_NAME + SUFFIX_CNF));
					assumptions = new FileInputStream(new File(queryDir,
							ENCODING_NAME + SUFFIX_ASSUMPTIONS));

					final Proof<Integer> proof = DirectSatEncodingProofAdapter
							.load(assumptions, cnf);

					collectStatistics(question, proof, justifier, stats);

				} catch (final IOException e) {
					LOG.error("Error while reading the query dir!", e);
					System.exit(2);
				} finally {
					Utils.closeQuietly(queryReader);
					Utils.closeQuietly(questionReader);
					Utils.closeQuietly(cnf);
					Utils.closeQuietly(assumptions);
				}

			}

		} catch (final IOException e) {
			LOG.error("Error while opening record file!", e);
			System.exit(2);
		} finally {
			Utils.closeQuietly(stats);
		}

	}

	private static <C, A> void collectStatistics(final C expression,
			final Proof<C> proof,
			final InferenceJustifier<C, ? extends Set<? extends A>> justifier,
			final PrintWriter stats) {

		final Set<A> axiomExprs = new HashSet<A>();
		final Set<C> lemmaExprs = new HashSet<C>();
		final Set<Inference<C>> inferences = new HashSet<Inference<C>>();

		Utils.traverseProofs(expression, proof, justifier,
				new Function<Inference<C>, Void>() {
					@Override
					public Void apply(final Inference<C> inf) {
						inferences.add(inf);
						return null;
					}
				}, new Function<C, Void>() {
					@Override
					public Void apply(final C expr) {
						lemmaExprs.add(expr);
						return null;
					}
				}, new Function<A, Void>() {
					@Override
					public Void apply(final A axiom) {
						axiomExprs.add(axiom);
						return null;
					}
				});

		stats.print(",");
		stats.print(axiomExprs.size());
		stats.print(",");
		stats.print(lemmaExprs.size());
		stats.print(",");
		stats.print(inferences.size());
		stats.flush();

		final boolean hasCycle = Proofs.hasCycle(proof, expression);
		stats.print(",");
		stats.print(hasCycle);
		stats.flush();

		final StronglyConnectedComponents<C> components = StronglyConnectedComponentsComputation
				.computeComponents(proof, expression);

		final List<List<C>> comps = components.getComponents();
		final List<C> maxComp = Collections.max(comps, SIZE_COMPARATOR);
		stats.print(",");
		stats.print(maxComp.size());

		final Collection<List<C>> nonSingletonComps = Collections2.filter(comps,
				new Predicate<List<C>>() {
					@Override
					public boolean apply(final List<C> comp) {
						return comp.size() > 1;
					}
				});
		stats.print(",");
		stats.print(nonSingletonComps.size());

		stats.println();
		stats.flush();

	}

	private static final Comparator<Collection<?>> SIZE_COMPARATOR = new Comparator<Collection<?>>() {
		@Override
		public int compare(final Collection<?> o1, final Collection<?> o2) {
			return Integer.compare(o1.size(), o2.size());
		}
	};

}
