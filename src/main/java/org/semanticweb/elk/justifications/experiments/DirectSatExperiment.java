package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.DirectSatEncodingInferenceSetAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectSatExperiment extends Experiment {
	
	private static final Logger LOG = LoggerFactory.getLogger(
			DirectSatExperiment.class);

	private final List<Integer> conclusions_;
	private final List<String> labels_;
	private final JustificationComputation<Integer, Integer> computation_;
	
	private Collection<Set<Integer>> justifications_;
	
	public DirectSatExperiment(final String[] args) throws ExperimentException {
		super(args);
		
		if (args.length < 3) {
			throw new ExperimentException("Insufficient arguments!");
		}
		
		final String cnfFileName = args[0];
		final String assumptionsFileName = args[1];
		final String conclusionsFileName = args[2];
		
		this.conclusions_ = new ArrayList<Integer>();
		this.labels_ = new ArrayList<String>();
		
		InputStream cnf = null;
		InputStream assumptions = null;
		BufferedReader conclusionReader = null;
		try {
			
			LOG.info("Loading conclusions ...");
			long start = System.currentTimeMillis();
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			String line;
			while ((line = conclusionReader.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				final String[] cells = line.split("\\s");
				conclusions_.add(Integer.valueOf(cells[0]));
				labels_.add(line.substring(cells[0].length()).trim());
			}
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			cnf = new FileInputStream(cnfFileName);
			assumptions = new FileInputStream(assumptionsFileName);
			
			LOG.info("Loading CNF ...");
			start = System.currentTimeMillis();
			final InferenceSet<Integer, Integer> inferenceSet =
					DirectSatEncodingInferenceSetAdapter.load(assumptions, cnf);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			computation_ =
					new BottomUpJustificationComputation<Integer, Integer>(
							inferenceSet);
			
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final NumberFormatException e) {
			throw new ExperimentException(e);
		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			if (cnf != null) {
				try {
					cnf.close();
				} catch (final IOException e) {}
			}
			if (assumptions != null) {
				try {
					assumptions.close();
				} catch (final IOException e) {}
			}
			if (conclusionReader != null) {
				try {
					conclusionReader.close();
				} catch (final IOException e) {}
			}
		}
		
	}

	@Override
	public int getInputSize() throws ExperimentException {
		return conclusions_.size();
	}

	@Override
	public String getInputName(final int inputIndex)
			throws ExperimentException {
		return labels_.get(inputIndex).toString();
	}

	@Override
	public int run(final int inputIndex)
			throws ExperimentException, InterruptedException {
		justifications_ = computation_.computeJustifications(
				conclusions_.get(inputIndex));
		return justifications_.size();
	}

	@Override
	public void processResult(final int inputIndex) throws ExperimentException {
		// Currently empty.
	}

}
