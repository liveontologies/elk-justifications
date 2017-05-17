package org.semanticweb.elk.justifications.experiments;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.proofs.adapters.DirectSatEncodingProofAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectSatJustificationExperiment
		extends BaseJustificationExperiment<Integer, Integer> {

	private static final Logger LOG = LoggerFactory
			.getLogger(DirectSatJustificationExperiment.class);

	private String cnfFileName_;
	private String assumptionsFileName_;

	@Override
	public void init(final String[] args) throws ExperimentException {
		super.init(args);

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
	protected Proof<Integer> newProof(final Integer query)
			throws ExperimentException {

		InputStream cnf = null;
		InputStream assumptions = null;

		try {

			cnf = new FileInputStream(cnfFileName_);
			assumptions = new FileInputStream(assumptionsFileName_);

			LOG.info("Loading CNF ...");
			final long start = System.currentTimeMillis();
			final Proof<Integer> result = DirectSatEncodingProofAdapter
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

	@Override
	protected InferenceJustifier<Integer, ? extends Set<? extends Integer>> newJustifier()
			throws ExperimentException {
		return DirectSatEncodingProofAdapter.JUSTIFIER;
	}

}
