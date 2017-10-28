package org.liveontologies.proofs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;

import org.liveontologies.pinpointing.DirectSatEncodingUsingElkCsvQuery;
import org.liveontologies.pinpointing.Utils;
import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.proofs.adapters.DirectSatEncodingProofAdapter;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.Proof;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SatProofProvider
		implements ProofProvider<String, Integer, Inference<Integer>, Integer> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(SatProofProvider.class);

	private final File inputDir_;

	public SatProofProvider(final File inputDir) {
		this.inputDir_ = inputDir;
	}

	@Override
	public JustificationCompleteProof<Integer, Inference<Integer>, Integer> getProof(
			final String query) throws ExperimentException {

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
		final Integer goal = Integer.valueOf(decoded);

		final File cnfFile = new File(queryDir,
				DirectSatEncodingUsingElkCsvQuery.FILE_NAME
						+ DirectSatEncodingUsingElkCsvQuery.SUFFIX_CNF);

		final File assumptionsFile = new File(queryDir,
				DirectSatEncodingUsingElkCsvQuery.FILE_NAME
						+ DirectSatEncodingUsingElkCsvQuery.SUFFIX_ASSUMPTIONS);

		LOGGER_.info("... took {}s",
				(System.currentTimeMillis() - start) / 1000.0);

		InputStream cnf = null;
		InputStream assumptions = null;
		try {

			cnf = new FileInputStream(cnfFile);
			assumptions = new FileInputStream(assumptionsFile);

			LOGGER_.info("Loading proof ...");
			start = System.currentTimeMillis();
			final Proof<Inference<Integer>> proof = DirectSatEncodingProofAdapter
					.load(assumptions, cnf);
			LOGGER_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

			return new BaseJustificationCompleteProof<>(goal, proof,
					DirectSatEncodingProofAdapter.JUSTIFIER);

		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(cnf);
			Utils.closeQuietly(assumptions);
		}

	}

	@Override
	public void dispose() {
		// Empty.
	}

}
