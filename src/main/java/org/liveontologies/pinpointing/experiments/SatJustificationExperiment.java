package org.liveontologies.pinpointing.experiments;

import java.io.File;

import org.liveontologies.proofs.ProofProvider;
import org.liveontologies.proofs.SatProofProvider;
import org.liveontologies.puli.Inference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;

public abstract class SatJustificationExperiment<O extends SatJustificationExperiment.Options>
		extends
		BaseJustificationExperiment<O, Integer, Inference<Integer>, Integer> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(SatJustificationExperiment.class);

	public static final String INPUT_DIR_OPT = "input";

	public static class Options extends BaseJustificationExperiment.Options {
		@Arg(dest = INPUT_DIR_OPT)
		public File inputDir;
	}

	private File inputDir_;

	@Override
	protected void addArguments(final ArgumentParser parser) {
		parser.addArgument(INPUT_DIR_OPT)
				.type(Arguments.fileType().verifyExists().verifyIsDirectory())
				.help("directory with the input");
	}

	@Override
	protected void init(final O options) throws ExperimentException {
		LOGGER_.info("inputDir: {}", options.inputDir);
		this.inputDir_ = options.inputDir;
	}

	@Override
	protected ProofProvider<String, Integer, Inference<Integer>, Integer> newProofProvider()
			throws ExperimentException {
		return new SatProofProvider(inputDir_);
	}

}
