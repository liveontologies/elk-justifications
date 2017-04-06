package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.InferenceSet;
import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.proofs.adapters.DirectSatEncodingInferenceSetAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

public class DirectSatResolutionJustificationExperiment
		extends ResolutionJustificationExperiment<Integer, Integer> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(DirectSatResolutionJustificationExperiment.class);

	private static final String LITERAL_GROUP_ = "literal";
	private static final String SUB_GROUP_ = "sub";
	private static final String SUP_GROUP_ = "sup";
	private static final Pattern ZZZ_RE_ = Pattern.compile("(?<"
			+ LITERAL_GROUP_ + ">\\d+)\\s+\\(\\s*implies\\s+(?<" + SUB_GROUP_
			+ ">[^\\s]+)\\s+(?<" + SUP_GROUP_ + ">[^\\s]+)\\s*\\)\\s*");

	public static final String ZZZ_OPT = "zzz";
	public static final String INPUT_DIR_OPT = "input";
	public static final String NAME_OPT = "name";

	private File inputDir_ = null;
	private String name_ = null;

	private File cnfFile_ = null;
	private File assumptionsFile_ = null;

	private final Map<String, String> literalsPerQueries_ = new HashMap<String, String>();

	@Override
	protected void addArguments(final ArgumentParser parser) {
		parser.addArgument(ZZZ_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("file mapping conclusions to literals");
		parser.addArgument(INPUT_DIR_OPT)
				.type(Arguments.fileType().verifyExists().verifyIsDirectory())
				.help("directory with the input");
		parser.addArgument(NAME_OPT).help("name of the encoding");
	}

	@Override
	protected void init(final Namespace options) throws ExperimentException {
		final File zzzFile = options.get(ZZZ_OPT);
		inputDir_ = options.get(INPUT_DIR_OPT);
		name_ = options.getString(NAME_OPT);

		BufferedReader zzzReader = null;
		try {

			zzzReader = new BufferedReader(new FileReader(zzzFile));

			LOGGER_.info("Indexing queries ...");
			long start = System.currentTimeMillis();

			String line;
			while ((line = zzzReader.readLine()) != null) {

				final Matcher m = ZZZ_RE_.matcher(line);
				if (!m.matches()) {
					continue;
				}
				// else

				final String literal = m.group(LITERAL_GROUP_);
				final String query = m.group(SUB_GROUP_) + " "
						+ m.group(SUP_GROUP_);

				literalsPerQueries_.put(query, literal);

			}

			LOGGER_.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);

		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(zzzReader);
		}

	}

	@Override
	protected Integer decodeQuery(final String query)
			throws ExperimentException {

		LOGGER_.info("Decoding query {} ...", query);
		long start = System.currentTimeMillis();

		final String literal = literalsPerQueries_.get(query);
		if (literal == null) {
			throw new ExperimentException("No literal for query " + query);
		}
		// else

		final File literalDir = new File(inputDir_, literal);
		final File queryFile = new File(literalDir, new StringBuilder(name_)
				.append(".").append(literal).append(".query").toString());

		final String decoded;
		BufferedReader queryReader = null;
		try {
			queryReader = new BufferedReader(new FileReader(queryFile));
			decoded = queryReader.readLine();
			if (decoded == null) {
				throw new ExperimentException(
						"Could not read query file for literal: " + literal
								+ ", query: " + query);
			}
		} catch (final IOException e) {
			throw new ExperimentException(e);
		} finally {
			Utils.closeQuietly(queryReader);
		}

		cnfFile_ = new File(literalDir,
				new StringBuilder(name_).append(".").append(literal).append(".")
						.append(decoded).append(".coi.cnf").toString());

		assumptionsFile_ = new File(literalDir,
				new StringBuilder(name_).append(".").append(literal).append(".")
						.append(decoded).append(".module.axioms").toString());

		LOGGER_.info("... took {}s",
				(System.currentTimeMillis() - start) / 1000.0);

		return Integer.valueOf(decoded);
	}

	@Override
	protected InferenceSet<Integer> newInferenceSet(final Integer query)
			throws ExperimentException {

		InputStream cnf = null;
		InputStream assumptions = null;
		try {

			cnf = new FileInputStream(cnfFile_);
			assumptions = new FileInputStream(assumptionsFile_);

			LOGGER_.info("Loading inference set ...");
			long start = System.currentTimeMillis();
			final InferenceSet<Integer> result = DirectSatEncodingInferenceSetAdapter
					.load(assumptions, cnf);
			LOGGER_.info("... took {}s",
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
		return DirectSatEncodingInferenceSetAdapter.JUSTIFIER;
	}

}
