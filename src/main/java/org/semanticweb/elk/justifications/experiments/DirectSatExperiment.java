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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
	private final InferenceSet<Integer, Integer> inferenceSet_;
	
	private AtomicReference<Collection<Set<Integer>>> justifications_ =
			new AtomicReference<Collection<Set<Integer>>>();
	
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
			inferenceSet_ =
					DirectSatEncodingInferenceSetAdapter.load(assumptions, cnf);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
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

	private AtomicInteger inputIndex_ = new AtomicInteger(0);
	private AtomicInteger conclusion_ = new AtomicInteger(0);
	private AtomicReference<String> label_ =
			new AtomicReference<String>("null");

	@Override
	public void init() throws ExperimentException {
		inputIndex_.set(0);
		conclusion_.set(0);
		label_.set("null");
	}

	@Override
	public boolean hasNext() {
		return inputIndex_.get() < conclusions_.size();
	}

	@Override
	public Record run() throws ExperimentException, InterruptedException {
		label_.set(labels_.get(inputIndex_.get()));
		final int conclusion = conclusions_.get(inputIndex_.getAndIncrement());
		conclusion_.set(conclusion);
		final JustificationComputation<Integer, Integer> computation =
				new BottomUpJustificationComputation<Integer, Integer>(
						inferenceSet_);
		long time = System.currentTimeMillis();
		final Collection<Set<Integer>> justifications =
				computation.computeJustifications(conclusion);
		time = System.currentTimeMillis() - time;
		justifications_.set(justifications);
		return new Record(time, justifications.size());
	}

	@Override
	public String getInputName() throws ExperimentException {
		return label_.get();
	}

	@Override
	public void processResult() throws ExperimentException {
		// Currently empty.
	}

}
