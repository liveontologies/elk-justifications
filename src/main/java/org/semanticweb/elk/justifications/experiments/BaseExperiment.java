package org.semanticweb.elk.justifications.experiments;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.justifications.JustificationComputation;
import org.semanticweb.elk.justifications.JustificationComputation.Factory;
import org.semanticweb.elk.justifications.Monitor;
import org.semanticweb.elk.justifications.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

public abstract class BaseExperiment<C, A, Q, G extends C, R>
		extends Experiment {

	public static final String STAT_NAME_AXIOMS = BaseExperiment.class
			.getSimpleName() + ".nAxiomsInAllProofs";
	public static final String STAT_NAME_INFERENCES = BaseExperiment.class
			.getSimpleName() + ".nInferencesInAllProofs";
	public static final String STAT_NAME_CONCLUSIONS = BaseExperiment.class
			.getSimpleName() + ".nConclusionsInAllProofs";

	private static final Logger LOG = LoggerFactory
			.getLogger(BaseExperiment.class);

	private final JustificationComputation.Factory<C, A> factory_;
	private final String queryFileName_;
	private final R reasoner_;
	private final File outputDirectory_;

	private QueryIterator<Q> queryIterator_ = null;

	private AtomicReference<Q> conclusion_ = new AtomicReference<Q>();
	private AtomicReference<Collection<? extends Set<A>>> justifications_ = new AtomicReference<Collection<? extends Set<A>>>();
	private AtomicReference<JustificationComputation<C, A>> computation_ = new AtomicReference<JustificationComputation<C, A>>();
	private AtomicReference<Map<String, Object>> stats_ = new AtomicReference<Map<String, Object>>();

	public BaseExperiment(final String[] args) throws ExperimentException {
		super(args);

		if (args.length < 1) {
			throw new ExperimentException("Insufficient arguments!");
		}

		final String computationClassName = args[0];
		final String ontologyFileName = args[1];
		queryFileName_ = args[2];
		if (args.length >= 4) {
			outputDirectory_ = new File(args[3]);
			if (!Utils.cleanDir(outputDirectory_)) {
				LOG.error("Could not prepare the output directory!");
				System.exit(2);
			}
		} else {
			outputDirectory_ = null;
		}

		try {
			final Class<?> computationClass = Class
					.forName(computationClassName);
			final Method getFactory = computationClass.getMethod("getFactory");
			@SuppressWarnings("unchecked")
			final Factory<C, A> factory = (JustificationComputation.Factory<C, A>) getFactory
					.invoke(null);
			factory_ = factory;
		} catch (final ClassNotFoundException e) {
			throw new ExperimentException(e);
		} catch (final NoSuchMethodException e) {
			throw new ExperimentException(e);
		} catch (final SecurityException e) {
			throw new ExperimentException(e);
		} catch (final IllegalAccessException e) {
			throw new ExperimentException(e);
		} catch (final IllegalArgumentException e) {
			throw new ExperimentException(e);
		} catch (final InvocationTargetException e) {
			throw new ExperimentException(e);
		}

		reasoner_ = loadAndClassifyOntology(ontologyFileName);

	}

	protected abstract R loadAndClassifyOntology(String ontologyFileName)
			throws ExperimentException;

	protected abstract QueryIterator<Q> newQueryIterator(String queryFileName)
			throws ExperimentException;

	protected JustificationComputation<C, A> newComputation(
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
			final Monitor monitor) {
		return factory_.create(inferenceSet, monitor);
	}

	protected abstract G getGoalConclusion(R reasoner, Q query)
			throws ExperimentException;

	protected abstract GenericInferenceSet<C, ? extends JustifiedInference<C, A>> newInferenceSet(
			R reasoner, G goal) throws ExperimentException;

	protected void saveJustifications(final Q query,
			final Collection<? extends Set<A>> justifications,
			final File outputDirectory) throws ExperimentException {
		// Empty.
	}

	@Override
	public void init() throws ExperimentException {
		conclusion_.set(null);
		justifications_.set(null);

		if (queryIterator_ != null) {
			queryIterator_.dispose();
		}
		queryIterator_ = newQueryIterator(queryFileName_);

	}

	@Override
	public boolean hasNext() {
		if (queryIterator_ == null) {
			return false;
		}
		return queryIterator_.hasNext();
	}

	@Override
	public Record run(final Monitor monitor) throws ExperimentException {

		final Q query = queryIterator_.next();
		if (query == null) {
			throw new ExperimentException("No more queries!");
		}
		conclusion_.set(query);

		// long time = System.currentTimeMillis();
		long time = System.nanoTime();
		final G goal = getGoalConclusion(reasoner_, query);
		final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet = newInferenceSet(
				reasoner_, goal);
		final JustificationComputation<C, A> computation = newComputation(
				inferenceSet, monitor);
		final Collection<? extends Set<A>> justifications = computation
				.computeJustifications(goal);
		// time = System.currentTimeMillis() - time;
		time = System.nanoTime() - time;

		justifications_.set(justifications);
		computation_.set(computation);

		// return new Record(time, justifications.size());
		return new Record(time / 1000000.0, justifications.size());
	}

	@Override
	public String getInputName() throws ExperimentException {
		return conclusion_.get() == null ? "null"
				: conclusion_.get().toString();
	}

	@Override
	public void processResult() throws ExperimentException {

		final Q query = conclusion_.get();

		final G goal = getGoalConclusion(reasoner_, query);
		final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet = newInferenceSet(
				reasoner_, goal);

		final Set<A> axiomExprs = new HashSet<A>();
		final Set<C> lemmaExprs = new HashSet<C>();
		final Set<JustifiedInference<C, A>> inferences = new HashSet<JustifiedInference<C, A>>();

		Utils.traverseProofs(goal, inferenceSet,
				new Function<JustifiedInference<C, A>, Void>() {
					@Override
					public Void apply(final JustifiedInference<C, A> inf) {
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

		final Map<String, Object> stats = new HashMap<String, Object>();
		stats.put(STAT_NAME_AXIOMS, axiomExprs.size());
		stats.put(STAT_NAME_CONCLUSIONS, lemmaExprs.size());
		stats.put(STAT_NAME_INFERENCES, inferences.size());
		stats_.set(stats);

		if (outputDirectory_ != null) {
			saveJustifications(query, justifications_.get(), outputDirectory_);
		}

	}

	@Override
	public String[] getStatNames() {
		final String[] statNames = new String[] { STAT_NAME_AXIOMS,
				STAT_NAME_CONCLUSIONS, STAT_NAME_INFERENCES, };
		final String[] otherStatNames = factory_.getStatNames();
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
		final JustificationComputation<C, A> computation = computation_.get();
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
		final JustificationComputation<C, A> computation = computation_.get();
		if (computation != null) {
			computation.logStatistics();
		}
	}

	@Override
	public void resetStatistics() {
		stats_.set(null);
		final JustificationComputation<C, A> computation = computation_.get();
		if (computation != null) {
			computation.resetStatistics();
		}
	}

}
