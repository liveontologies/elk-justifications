package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.proofs.adapters.DirectSatEncodingProofAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class DirectSatResolutionJustificationExperiment
		extends ResolutionJustificationExperiment<Integer, Integer> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(DirectSatResolutionJustificationExperiment.class);

	public static final String INPUT_DIR_OPT = "input";

	public static final String ENCODING_NAME = "encoding";
	public static final String SUFFIX_QUERY = ".query";
	public static final String SUFFIX_QUESTION = ".question";
	public static final String SUFFIX_CNF = ".cnf";
	public static final String SUFFIX_ASSUMPTIONS = ".assumptions";

	private File inputDir_ = null;

	private Iterator<File> queryDirIter_ = null;
	private File queryDir_ = null;
	private File cnfFile_ = null;
	private File assumptionsFile_ = null;

	@Override
	protected void addArguments(final ArgumentParser parser) {
		parser.addArgument(INPUT_DIR_OPT)
				.type(Arguments.fileType().verifyExists().verifyIsDirectory())
				.help("directory with the input");
	}

	@Override
	protected void init(final Namespace options) throws ExperimentException {
		inputDir_ = options.get(INPUT_DIR_OPT);

		final File[] queryDirs = inputDir_.listFiles();
		Arrays.sort(queryDirs);
		queryDirIter_ = Arrays.asList(queryDirs).iterator();

	}

	@Override
	public String before(final String query) throws ExperimentException {

		if (!queryDirIter_.hasNext()) {
			throw new ExperimentException("No more queries!");
		}
		// else
		queryDir_ = queryDirIter_.next();

		super.before(query);

		BufferedReader queryReader = null;
		try {

			queryReader = new BufferedReader(new FileReader(
					new File(queryDir_, ENCODING_NAME + SUFFIX_QUERY)));

			final String line = queryReader.readLine();
			if (line == null) {
				throw new ExperimentException(
						"Could not read query file in: " + queryDir_);
			}
			// else
			return line;

		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(queryReader);
		}

	}

	@Override
	protected Integer decodeQuery(final String query)
			throws ExperimentException {

		LOGGER_.info("Decoding query {} ...", query);
		long start = System.currentTimeMillis();

		final File queryFile = new File(queryDir_,
				ENCODING_NAME + SUFFIX_QUESTION);

		final String decoded;
		BufferedReader queryReader = null;
		try {
			queryReader = new BufferedReader(new FileReader(queryFile));
			final String line = queryReader.readLine();
			if (line == null) {
				throw new ExperimentException(
						"Could not read question file in: " + queryDir_);
			}
			decoded = line.split("\\s+")[0];
		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(queryReader);
		}

		cnfFile_ = new File(queryDir_, ENCODING_NAME + SUFFIX_CNF);

		assumptionsFile_ = new File(queryDir_,
				ENCODING_NAME + SUFFIX_ASSUMPTIONS);

		LOGGER_.info("... took {}s",
				(System.currentTimeMillis() - start) / 1000.0);

		return -Integer.valueOf(decoded);
	}

	@Override
	protected Proof<Integer> newProof(final Integer query)
			throws ExperimentException {

		InputStream cnf = null;
		InputStream assumptions = null;
		try {

			cnf = new FileInputStream(cnfFile_);
			assumptions = new FileInputStream(assumptionsFile_);

			LOGGER_.info("Loading proof ...");
			long start = System.currentTimeMillis();
			final Proof<Integer> result = DirectSatEncodingProofAdapter
					.load(assumptions, cnf);
			LOGGER_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);
			return result;

		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(cnf);
			Utils.closeQuietly(assumptions);
		}

	}

	@Override
	protected InferenceJustifier<Integer, ? extends Set<? extends Integer>> newJustifier()
			throws ExperimentException {
		return DirectSatEncodingProofAdapter.JUSTIFIER;
	}

}
