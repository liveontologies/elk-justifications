package org.liveontologies.pinpointing.experiments;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;
import org.liveontologies.puli.pinpointing.InterruptMonitor;
import org.liveontologies.puli.pinpointing.MinimalSubsetsFromProofs;
import org.liveontologies.puli.pinpointing.MinimalSubsetEnumerator.Factory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;

public class ElkFactoryJustificationExperiment extends
		ElkJustificationExperiment<ElkFactoryJustificationExperiment.Options> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(ElkFactoryJustificationExperiment.class);

	public static final String OPT_FACTORY_CLASS = "class";

	public static class Options
			extends ElkJustificationExperiment.Options {
		@Arg(dest = OPT_FACTORY_CLASS)
		public String computationFactoryClassName;
	}

	private MinimalSubsetsFromProofs.Factory<Object, Inference<Object>, ElkAxiom> factory_;

	@Override
	protected Options newOptions() {
		return new Options();
	}

	@Override
	protected void addArguments(final ArgumentParser parser) {
		super.addArguments(parser);
		parser.description(
				"Experiment using provided Justification Computation and internal proofs from ELK.");
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
			final MinimalSubsetsFromProofs.Factory<Object, Inference<Object>, ElkAxiom> factory = (MinimalSubsetsFromProofs.Factory<Object, Inference<Object>, ElkAxiom>) getFactory
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
	protected Factory<Object, ElkAxiom> newComputation(
			final Proof<? extends Inference<Object>> proof,
			final InferenceJustifier<? super Inference<Object>, ? extends Set<? extends ElkAxiom>> justifier,
			final InterruptMonitor monitor) throws ExperimentException {
		return factory_.create(proof, justifier, monitor);
	}

}
