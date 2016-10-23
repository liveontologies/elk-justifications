package org.semanticweb.elk.justifications;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.semanticweb.elk.justifications.asp.BackwardClaspEncoder;
import org.semanticweb.elk.justifications.asp.DerivabilityClaspEncoder;
import org.semanticweb.elk.justifications.asp.Encoder;
import org.semanticweb.elk.justifications.asp.Encoders;
import org.semanticweb.elk.justifications.asp.ForwardClaspEncoder;
import org.semanticweb.elk.justifications.asp.Index;
import org.semanticweb.elk.justifications.asp.SuffixClaspEncoder;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinearAspJustificationComputation<C, A>
		extends CancellableJustificationComputation<C, A> {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(LinearAspJustificationComputation.class);
	
	public static final String STAT_NAME_JUSTIFICATIONS = "LinearAspJustificationComputation.nJustificationsOfAllConclusions";
	public static final String STAT_NAME_MAX_JUST_OF_CONCL = "LinearAspJustificationComputation.maxNJustificationsOfAConclusion";
	public static final String STAT_NAME_LITERALS = "LinearAspJustificationComputation.nLiterals";
	
	public static final String[] STAT_NAMES = new String[] {
			STAT_NAME_JUSTIFICATIONS,
			STAT_NAME_MAX_JUST_OF_CONCL,
			STAT_NAME_LITERALS,
	};
	
	private final String claspExecutable;
	
	private int countLiterals;
	
	public LinearAspJustificationComputation(
			final InferenceSet<C, A> inferences,
			final Monitor monitor,
			final String claspExecutable) {
		super(inferences, monitor);
		this.claspExecutable = claspExecutable;
	}
	
	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C conclusion, final int sizeLimit) {
		return computeJustifications(conclusion);
	}
	
	@Override
	public Collection<? extends Set<A>> computeJustifications(
			final C goalConclusion) {
		
		try {
			
			final Process process = new ProcessBuilder(
					claspExecutable,
					"0"// Find all answer sets.
				).start();
			
			final Thread cancellerThread = new Thread(new Canceller(process));
			cancellerThread.start();
			
			final PrintWriter outWriter =
					new PrintWriter(process.getOutputStream(), true);
			// TODO: parse these
			new Thread(new Pipe(process.getInputStream(), System.out)).start();
			new Thread(new Pipe(process.getErrorStream(), System.err)).start();
			
			final InferenceSet<C, A> inferenceSet = getInferenceSet();
			
			final Index<C> conclIndex = new Index<>();
			final Index<A> axiomIndex = new Index<>();
			final Index<Inference<C, A>> infIndex = new Index<>();
			final Index<String> literalIndex = new Index<>(2);// Gringo starts indexing from 2 !!!
			
			final Encoder<C, A> encoder = Encoders.combine(
					new BackwardClaspEncoder<C, A>(outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
					new ForwardClaspEncoder<C, A>(outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
					new DerivabilityClaspEncoder<C, A>(outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
//					new SuffixClaspEncoder<C, A>("", outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex),
					new SuffixClaspEncoder<C, A>("axiom", outWriter, inferenceSet, conclIndex, axiomIndex, infIndex, literalIndex)
				);
			
			Encoders.encode(inferenceSet, goalConclusion, encoder);
			
			outWriter.flush();
			outWriter.close();
			
			countLiterals = literalIndex.getIndex().size();
			
			
			// TODO .: Instead, parse the output and kill after the end !!!
			process.waitFor();
			
			
			cancellerThread.interrupt();
			
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}
	
	@Override
	public String[] getStatNames() {
		return STAT_NAMES;
	}
	
	@Override
	public Map<String, Object> getStatistics() {
		final Map<String, Object> stats = new HashMap<String, Object>();
//		stats.put(STAT_NAME_JUSTIFICATIONS, justifications_.size());
//		int max = 0;
//		for (final C conclusion : justifications_.keySet()) {
//			final List<Justification<C, A>> justs = justifications_
//					.get(conclusion);
//			if (justs.size() > max) {
//				max = justs.size();
//			}
//		}
//		stats.put(STAT_NAME_MAX_JUST_OF_CONCL, max);
		stats.put(STAT_NAME_JUSTIFICATIONS, 0);
		stats.put(STAT_NAME_MAX_JUST_OF_CONCL, 0);
		stats.put(STAT_NAME_LITERALS, countLiterals);
		return stats;
	}
	
	@Override
	public void logStatistics() {
		if (LOG.isDebugEnabled()) {
//			LOG.debug("{}: number of justifications of all conclusions",
//					justifications_.size());
//			int max = 0;
//			for (final C conclusion : justifications_.keySet()) {
//				final List<Justification<C, A>> justs = justifications_
//						.get(conclusion);
//				if (justs.size() > max) {
//					max = justs.size();
//				}
//			}
//			LOG.debug("{}: number of justifications of the conclusion "
//					+ "with most justifications", max);
			LOG.debug("{}: number of literals in encoding", countLiterals);
		}
	}
	
	@Override
	public void resetStatistics() {
		countLiterals = 0;
	}
	
	public static class Factory<C, A> implements JustificationComputation.Factory<C, A> {
		
		private final String claspExecutable;
		
		public Factory(final String claspExecutable) {
			this.claspExecutable = claspExecutable;
		}
		
		@Override
		public JustificationComputation<C, A> create(
				final InferenceSet<C, A> inferenceSet,
				final Monitor monitor) {
			return new LinearAspJustificationComputation<C, A>(
					inferenceSet,
					monitor,
					claspExecutable
				);
		}
		
		@Override
		public String[] getStatNames() {
			return STAT_NAMES;
		}
		
	}
	
	private class Pipe implements Runnable {
		
		private final InputStream from;
		private final OutputStream to;
		
		public Pipe(final InputStream from, final OutputStream to) {
			this.from = from;
			this.to = to;
		}
		
		@Override
		public void run() {
			
			try {
				
				int b;
				while ((b = from.read()) != -1) {
					to.write(b);
				}
				to.close();
				
			} catch (final IOException e) {
				LOG.warn("Broken Pipe", e);
			}
			
		}
		
	}
	
	private class Canceller implements Runnable {
		
		private final Process process;
		
		public Canceller(final Process process) {
			this.process = process;
		}
		
		@Override
		public void run() {
			
			try {
				
				while (true) {
					if (checkCancelled()) {
						process.destroy();
					}
					Thread.sleep(100);
				}
				
			} catch (final InterruptedException e) {
				// Termination.
			}
			
		}
		
	}
	
}
