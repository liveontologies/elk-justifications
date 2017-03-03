package org.semanticweb.elk.justifications.experiments;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.proofs.adapters.DirectSatEncodingInferenceSetAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectSatJustificationExperiment
		extends BaseJustificationExperiment<Integer, Integer> {

	private static final Logger LOG = LoggerFactory
			.getLogger(DirectSatJustificationExperiment.class);

	private final String cnfFileName_;
	private final String assumptionsFileName_;

	public DirectSatJustificationExperiment(final String[] args)
			throws ExperimentException {
		super(args);

		final int requiredArgCount = 3;

		if (args.length < requiredArgCount) {
			throw new ExperimentException("Insufficient arguments!");
		}

		this.cnfFileName_ = args[1];
		this.assumptionsFileName_ = args[2];

	}

	@Override
	protected Integer decodeQuery(final String query)
			throws ExperimentException {

		final String[] cells = query.split("\\s");
		return Integer.valueOf(cells[0]);

	}

	@Override
	protected GenericInferenceSet<Integer, ? extends JustifiedInference<Integer, Integer>> newInferenceSet(
			final Integer query) throws ExperimentException {

		InputStream cnf = null;
		InputStream assumptions = null;

		try {

			cnf = new FileInputStream(cnfFileName_);
			assumptions = new FileInputStream(assumptionsFileName_);

			LOG.info("Loading CNF ...");
			final long start = System.currentTimeMillis();
			final GenericInferenceSet<Integer, ? extends JustifiedInference<Integer, Integer>> result = DirectSatEncodingInferenceSetAdapter
					.load(assumptions, cnf);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

			return result;

		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(cnf);
			Utils.closeQuietly(assumptions);
		}

	}

}
