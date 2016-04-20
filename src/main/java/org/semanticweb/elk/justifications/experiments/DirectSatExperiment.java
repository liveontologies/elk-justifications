package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.semanticweb.elk.justifications.BottomUpJustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.DirectSatEncodingInferenceSetAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

public class DirectSatExperiment extends Experiment {

	public static final String STAT_NAME_AXIOMS = "DirectSatExperiment.nAxiomsInAllProofs";
	public static final String STAT_NAME_INFERENCES = "DirectSatExperiment.nInferencesInAllProofs";
	public static final String STAT_NAME_CONCLUSIONS = "DirectSatExperiment.nConclusionsInAllProofs";
	
	private static final Logger LOG = LoggerFactory.getLogger(
			DirectSatExperiment.class);

	private final List<Integer> conclusions_;
	private final List<String> labels_;
	private final InferenceSet<Integer, Integer> inferenceSet_;
	
	private AtomicReference<Collection<? extends Set<Integer>>> justifications_ =
			new AtomicReference<Collection<? extends Set<Integer>>>();
	private AtomicInteger inputIndex_ = new AtomicInteger(0);
	private AtomicInteger conclusion_ = new AtomicInteger(0);
	private AtomicReference<String> label_ =
			new AtomicReference<String>("null");
	private AtomicReference<JustificationComputation<Integer, Integer>> computation_ =
			new AtomicReference<JustificationComputation<Integer, Integer>>();
	private AtomicReference<Map<String, Object>> stats_ =
			new AtomicReference<Map<String, Object>>();
	
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
	public Record run(final Monitor monitor)
			throws ExperimentException {
		label_.set(labels_.get(inputIndex_.get()));
		final int conclusion = conclusions_.get(inputIndex_.getAndIncrement());
		conclusion_.set(conclusion);
		final JustificationComputation<Integer, Integer> computation = BottomUpJustificationComputation
				.<Integer, Integer> getFactory().create(inferenceSet_, monitor);
//		long time = System.currentTimeMillis();
		long time = System.nanoTime();
		final Collection<? extends Set<Integer>> justifications =
				computation.computeJustifications(conclusion);
//		time = System.currentTimeMillis() - time;
		time = System.nanoTime() - time;
		justifications_.set(justifications);
		computation_.set(computation);
//		return new Record(time, justifications.size());
		return new Record(time/1000000.0, justifications.size());
	}

	@Override
	public String getInputName() throws ExperimentException {
		return label_.get();
	}

	@Override
	public void processResult() throws ExperimentException {
		
		final int conclusion = conclusion_.get();
		
		final Set<Integer> axiomExprs =
				new HashSet<Integer>();
		final Set<Integer> lemmaExprs =
				new HashSet<Integer>();
		final Set<Inference<Integer, Integer>> inferences =
				new HashSet<Inference<Integer, Integer>>();
		
		Utils.traverseProofs(conclusion, inferenceSet_,
				new Function<Inference<Integer, Integer>, Void>() {
					@Override
					public Void apply(
							final Inference<Integer, Integer> inf) {
						inferences.add(inf);
						return null;
					}
				},
				new Function<Integer, Void>(){
					@Override
					public Void apply(final Integer expr) {
						lemmaExprs.add(expr);
						return null;
					}
				},
				new Function<Integer, Void>(){
					@Override
					public Void apply(final Integer axiom) {
						axiomExprs.add(axiom);
						return null;
					}
				}
		);
		
		final Map<String, Object> stats = new HashMap<String, Object>();
		stats.put(STAT_NAME_AXIOMS, axiomExprs.size());
		stats.put(STAT_NAME_CONCLUSIONS, lemmaExprs.size());
		stats.put(STAT_NAME_INFERENCES, inferences.size());
		stats_.set(stats);
		
		// Currently empty.
	}

	@Override
	public String[] getStatNames() {
		final String[] statNames = new String[] {
				STAT_NAME_AXIOMS,
				STAT_NAME_CONCLUSIONS,
				STAT_NAME_INFERENCES,
			};
		final String[] otherStatNames =
				BottomUpJustificationComputation.getFactory().getStatNames();
		final String[] ret = Arrays.copyOf(statNames,
				statNames.length + otherStatNames.length);
		System.arraycopy(otherStatNames, 0, ret, statNames.length,
				otherStatNames.length);
		return ret;
	}

	@Override
	public Map<String, Object> getStatistics() {
		Map<String, Object> stats = stats_.get();
		if (stats == null) {
			stats = new HashMap<String, Object>();
		}
		final JustificationComputation<Integer, Integer> computation =
				computation_.get();
		if (computation != null) {
			stats.putAll(computation.getStatistics());
		}
		return stats;
	}

	@Override
	public void logStatistics() {
		final Map<String, Object> stats = stats_.get();
		if (stats != null && LOG.isDebugEnabled()) {
			LOG.debug("{}: number of axioms in all proofs",
					stats.get(STAT_NAME_AXIOMS));
			LOG.debug("{}: number of conclusions in all proofs",
					stats.get(STAT_NAME_CONCLUSIONS));
			LOG.debug("{}: number of inferences in all proofs",
					stats.get(STAT_NAME_INFERENCES));
		}
		final JustificationComputation<Integer, Integer> computation =
				computation_.get();
		if (computation != null) {
			computation.logStatistics();
		}
	}

	@Override
	public void resetStatistics() {
		stats_.set(null);
		final JustificationComputation<Integer, Integer> computation =
				computation_.get();
		if (computation != null) {
			computation.resetStatistics();
		}
	}

}
