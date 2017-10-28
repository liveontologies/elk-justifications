package org.liveontologies.pinpointing.experiments;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.liveontologies.puli.pinpointing.MinimalSubsetEnumerator.Factory;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;

public class SatFactoryJustificationExperiment extends
		SatJustificationExperiment<SatFactoryJustificationExperiment.Options> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(SatFactoryJustificationExperiment.class);

	public static final String OPT_FACTORY_CLASS = "class";

	public static class Options extends SatJustificationExperiment.Options {
		@Arg(dest = OPT_FACTORY_CLASS)
		public String computationFactoryClassName;
	}

	private MinimalSubsetsFromProofs.Factory<Integer, Inference<Integer>, Integer> factory_;

	@Override
	protected Options newOptions() {
		return new Options();
	}

	@Override
	protected void addArguments(final ArgumentParser parser) {
		super.addArguments(parser);
		parser.description(
				"Experiment using provided Justification Computation and proofs from SAT encoding.");
		parser.addArgument(OPT_FACTORY_CLASS)
				.help("class of the computation factory");
	}

	@Override
	protected void init(final Options options) throws ExperimentException {
		super.init(options);
		LOGGER_.info("computationFactoryClassName: {}",
				options.computationFactoryClassName);
		try {
			final Class<?> computationClass = Class
					.forName(options.computationFactoryClassName);
			final Method getFactory = computationClass.getMethod("getFactory");
			@SuppressWarnings("unchecked")
			final MinimalSubsetsFromProofs.Factory<Integer, Inference<Integer>, Integer> factory = (MinimalSubsetsFromProofs.Factory<Integer, Inference<Integer>, Integer>) getFactory
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
	}

	@Override
	protected Factory<Integer, Integer> newComputation(
			final Proof<? extends Inference<Integer>> proof,
			final InferenceJustifier<? super Inference<Integer>, ? extends Set<? extends Integer>> justifier,
			final InterruptMonitor monitor) throws ExperimentException {
		return factory_.create(proof, justifier, monitor);
	}

}
