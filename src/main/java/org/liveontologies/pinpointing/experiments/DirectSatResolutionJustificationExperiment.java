package org.liveontologies.pinpointing.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.liveontologies.pinpointing.DirectSatEncodingUsingElkCsvQuery;
import org.liveontologies.pinpointing.Utils;
import org.liveontologies.proofs.adapters.DirectSatEncodingProofAdapter;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
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

	private File inputDir_ = null;

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
	}

	@Override
	protected Integer decodeQuery(final String query)
			throws ExperimentException {

		LOGGER_.info("Decoding query {} ...", query);
		long start = System.currentTimeMillis();

		final File queryDir = new File(inputDir_, Utils.toFileName(query));

		final File qFile = new File(queryDir,
				DirectSatEncodingUsingElkCsvQuery.FILE_NAME
						+ DirectSatEncodingUsingElkCsvQuery.SUFFIX_Q);

		final String decoded;
		BufferedReader qReader = null;
		try {
			qReader = new BufferedReader(new FileReader(qFile));
			final String line = qReader.readLine();
			if (line == null) {
				throw new ExperimentException(
						"Could not read question file in: " + queryDir);
			}
			decoded = line.split("\\s+")[0];
		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(qReader);
		}

		cnfFile_ = new File(queryDir,
				DirectSatEncodingUsingElkCsvQuery.FILE_NAME
						+ DirectSatEncodingUsingElkCsvQuery.SUFFIX_CNF);

		assumptionsFile_ = new File(queryDir,
				DirectSatEncodingUsingElkCsvQuery.FILE_NAME
						+ DirectSatEncodingUsingElkCsvQuery.SUFFIX_ASSUMPTIONS);

		LOGGER_.info("... took {}s",
				(System.currentTimeMillis() - start) / 1000.0);

		return Integer.valueOf(decoded);
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
