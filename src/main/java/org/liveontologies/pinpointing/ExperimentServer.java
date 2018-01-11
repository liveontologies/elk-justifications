package org.liveontologies.pinpointing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class ExperimentServer extends NanoHTTPD {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(ExperimentServer.class);

	public static final String OPT_PORT = "port";
	public static final String OPT_RESULTS = "results";
	public static final String OPT_PLOT = "plot";
	public static final String OPT_ONTOLOGIES = "ontologies";
	public static final String OPT_COMMAND = "command";

	public static final Integer DEFAULT_PORT = 80;

	public static class Options {
		@Arg(dest = OPT_PORT)
		public Integer port;
		@Arg(dest = OPT_RESULTS)
		public File results;
		@Arg(dest = OPT_PLOT)
		public File plot;
		@Arg(dest = OPT_ONTOLOGIES)
		public File ontologies;
		@Arg(dest = OPT_COMMAND)
		public String[] command;
	}

	public static void main(final String[] args) {

		final ArgumentParser parser = ArgumentParsers
				.newArgumentParser(ExperimentServer.class.getSimpleName())
				.description(
						"Simple HTTP server that runs experiments and reports the results.");
		parser.addArgument("--" + OPT_PORT).type(Integer.class)
				.setDefault(DEFAULT_PORT)
				.help("the port on which the server listens (default: "
						+ DEFAULT_PORT + ")");
		parser.addArgument("--" + OPT_ONTOLOGIES).type(File.class)
				.required(true)
				.help("the file into which the input ontologies are uploaded");
		parser.addArgument("--" + OPT_RESULTS).type(File.class).required(true)
				.help("the file into which the experiment saves its results");
		parser.addArgument("--" + OPT_PLOT).type(File.class).required(true)
				.help("the file into which the results are plotted");
		parser.addArgument(OPT_COMMAND).nargs("+").help(
				"the command that starts the experiment and its arguments\n"
						+ "(" + PATTERN_TIMEPUT_
						+ " will be substituted for timeout and "
						+ PATTERN_GLOBAL_TIMEPUT_ + " for global timeout)");

		try {

			final Options opt = new Options();
			parser.parseArgs(args, opt);

			LOGGER_.info("Binding server to port {}", opt.port);
			new ExperimentServer(opt.port, opt.ontologies, opt.results,
					opt.plot, opt.command);

		} catch (final IOException e) {
			LOGGER_.error("Cannot start server!", e);
			System.exit(1);
		} catch (final ArgumentParserException e) {
			parser.handleError(e);
			System.exit(2);
		}
	}

	public ExperimentServer(final int port, final File ontologiesFile,
			final File resultsFile, final File plotFile,
			final String... command) throws IOException {
		super(port);
		this.ontologiesFile_ = ontologiesFile;
		this.resultsFile_ = resultsFile;
		this.plotFile_ = plotFile;
		this.command_ = command;
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		LOGGER_.info("Server running ;-)");
	}

	private final File ontologiesFile_;
	private final File plotFile_;
	private final File resultsFile_;
	private final String[] command_;

	@GuardedBy("this")
	private Process experimentProcess_ = null;
	@GuardedBy("this")
	private StringBuilder experimentLog_ = new StringBuilder();

	private static final Pattern URI_INDEX_ = Pattern.compile("^/$");
	private static final Pattern URI_LOG_ = Pattern.compile("^/log/?$");
	private static final Pattern URI_LOG_SOURCE_ = Pattern
			.compile("^/log_source/?$");
	private static final Pattern URI_DONE_ = Pattern.compile("^/done/?$");
	private static final Pattern URI_KILL_ = Pattern.compile("^/kill/?$");
	private static final Pattern URI_RESULTS_ = Pattern
			.compile("^/results.tar.gz$");

	private static final String FIELD_TIMEOUT_ = "timeout";
	private static final String FIELD_GLOBAL_TIMEOUT_ = "global_timeout";
	private static final String FIELD_ONTOLOGIES_ = "ontologies";
	private static final String FIELD_DIRECT_ = "direct";
	private static final String FIELD_UNTOLD_ = "untold";
	private static final String FIELD_TAUT_ = "taut";
	private static final String FIELD_NOBOTTOM_ = "nobottom";

	private static final int DEFAULT_TIMEOUT_ = 60;
	private static final int DEFAULT_GLOBAL_TIMEOUT_ = 3600;

	// @formatter:off
	private static final String TEMPLATE_INDEX_ = "<!DOCTYPE html>\n"
			+ "<html>\n"
			+ "<body>\n"
			+ "  <h1>Choose experiment parameters:</h1>\n"
			+ "  <form method='post' enctype='multipart/form-data'>\n"
			+ "    <p><label for='" + FIELD_TIMEOUT_ + "'>Local timeout (seconds):</label><br/>\n"
			+ "%s"// validation message
			+ "    <input type='number' name='" + FIELD_TIMEOUT_ + "' required min='0' step='1' value='%s'></p>\n"
			+ "    <p><label for='" + FIELD_GLOBAL_TIMEOUT_ + "'>Global timeout (seconds):</label><br/>\n"
			+ "%s"// validation message
			+ "    <input type='number' name='" + FIELD_GLOBAL_TIMEOUT_ + "' min='0' step='1' value='%s'></p>\n"
			+ "    <p><label for='" + FIELD_ONTOLOGIES_ + "'>Archive with input ontologies (*.tar.gz):<br/>\n"
			+ "      (The ontology files must be in the root of the archive!)</label><br/>\n"
			+ "%s"// validation message
			+ "    <input type='file' name='" + FIELD_ONTOLOGIES_ + "' required></p>\n"
			+ "    <p>Options for query generation<br/>\n"
			+ "    (for which subsumptions should the justification be computed)<br/>\n"
			+ "    <input type='checkbox' name='" + FIELD_DIRECT_ + "' checked value='" + FIELD_DIRECT_ + "'>\n"
			+ "    <label for='" + FIELD_DIRECT_ + "'>only direct subsumptions</label><br/>\n"
			+ "    <input type='checkbox' name='" + FIELD_UNTOLD_ + "' value='" + FIELD_UNTOLD_ + "'>\n"
			+ "    <label for='" + FIELD_UNTOLD_ + "'>only subsumptions that are not asserted in the ontology</label><br/>\n"
			+ "    <input type='checkbox' name='" + FIELD_TAUT_ + "' checked value='" + FIELD_TAUT_ + "'>\n"
			+ "    <label for='" + FIELD_TAUT_ + "'>exclude obviously tautological subsumptions</label><br/>\n"
			+ "    <input type='checkbox' name='" + FIELD_NOBOTTOM_ + "' checked value='" + FIELD_NOBOTTOM_ + "'>\n"
			+ "    <label for='" + FIELD_NOBOTTOM_ + "'>exclude subsumptions involving inconsistent classes</label><br/>\n"
			+ "    </p>\n"
			+ "    <p><input type='submit' value='Submit'>\n"
			+ "    <input type='reset'></p>\n"
			+ "  </form>\n"
			+ "</body>\n"
			+ "</html>";
	private static final String TEMPLATE_LOG_ = "<!DOCTYPE html>\n"
			+ "<html>\n"
			+ "<body>\n"
			+ "  <h1>Experiment log</h1>\n"
			+ "  <p><a href=/kill/>Kill</a> (Carefully! No confirmation, no questions asked.)"
			+ "  <pre id='log'>%s</pre>\n"
			+ "  <script type='text/javascript'>\n"
			+ "    var source = new EventSource('/log_source/');\n"
			+ "    source.onmessage = function(event) {\n"
			+ "      document.getElementById('log').innerHTML = event.data;\n"
			+ "      if (event.lastEventId == 'last_log') {\n"
			+ "        source.close();\n"
			+ "        window.location.assign('/done/');\n"
			+ "      }\n"
			+ "    };\n"
			+ "  </script>\n"
			+ "</body>\n"
			+ "</html>";
	private static final String TEMPLATE_DONE_ = "<!DOCTYPE html>\n"
			+ "<html>\n"
			+ "<body>\n"
			+ "  <h1>Experiment finished</h1>\n"
			+ "  <p>Download the results from <a href=/results.tar.gz>here</a>."
			+ "  Or start from beginning <a href=/>here</a>.</p>\n"
			+ "  %s\n"// The plot
			+ "  <pre id='log'>%s</pre>\n"
			+ "</body>\n"
			+ "</html>";
	private static final String TEMPLATE_ALREADY_RUNNING_ = "<!DOCTYPE html>\n"
			+ "<html>\n"
			+ "<body>\n"
			+ "  <h1>An experiment is already running!</h1>\n"
			+ "  <a href=/log/>See the log here.</a>\n"
			+ "</body>\n"
			+ "</html>";
	private static final String TEMPLATE_NOT_FOUND_ = "<!DOCTYPE html>\n"
			+ "<html>\n"
			+ "<body>\n"
			+ "  <h1>404 Not Found!</h1>\n"
			+ "  <pre>\n"
			+ "%s"// uri path
			+ "  </pre>\n"
			+ "</body>\n"
			+ "</html>";
	private static final String TEMPLATE_INTERNAL_ERROR_ = "<!DOCTYPE html>\n"
			+ "<html>\n"
			+ "<body>\n"
			+ "  <h1>500 %s</h1>\n"
			+ "  <pre>\n"
			+ "%s"// exception message
			+ "  </pre>\n"
			+ "</body>\n"
			+ "</html>";
	// @formatter:on

	@Override
	public Response serve(final IHTTPSession session) {
		LOGGER_.info("request URI: {}", session.getUri());
		try {
			final URI requestUri = new URI(session.getUri());
			if (URI_INDEX_.matcher(requestUri.getPath()).matches()) {
				return indexView(session);
			} else if (URI_LOG_.matcher(requestUri.getPath()).matches()) {
				return logView(session);
			} else if (URI_LOG_SOURCE_.matcher(requestUri.getPath())
					.matches()) {
				return logSourceView(session);
			} else if (URI_DONE_.matcher(requestUri.getPath()).matches()) {
				return doneView(session);
			} else if (URI_KILL_.matcher(requestUri.getPath()).matches()) {
				return killView(session);
			} else if (URI_RESULTS_.matcher(requestUri.getPath()).matches()) {
				return resultsView(session);
			} else {
				return newFixedLengthResponse(Status.NOT_FOUND,
						NanoHTTPD.MIME_HTML, String.format(TEMPLATE_NOT_FOUND_,
								requestUri.getPath()));
			}
		} catch (final URISyntaxException e) {
			return newErrorResponse("Illegal request URI!", e);
		}
	}

	private Response indexView(final IHTTPSession session) {
		LOGGER_.info("index view");

		boolean formDataIsReady = true;
		final Map<String, String> validationMessages = new HashMap<>(2);
		validationMessages.put(FIELD_TIMEOUT_, "");
		validationMessages.put(FIELD_GLOBAL_TIMEOUT_, "");
		validationMessages.put(FIELD_ONTOLOGIES_, "");
		final String timeoutValue;
		final int timeout;
		final String globalTimeoutValue;
		final int globalTimeout;
		final String ontologiesFileName;
		final String queryGenerationOptions;
		try {

			final Map<String, String> files = new HashMap<String, String>();
			session.parseBody(files);
			final Map<String, String> params = session.getParms();
			LOGGER_.info("params: {}", params);
			LOGGER_.info("files: {}", files);

			if (params.containsKey(FIELD_TIMEOUT_)) {
				timeoutValue = params.get(FIELD_TIMEOUT_);
				int t;
				try {
					t = Integer.parseInt(timeoutValue);
				} catch (final NumberFormatException e) {
					formDataIsReady = false;
					t = DEFAULT_TIMEOUT_;
					validationMessages.put(FIELD_TIMEOUT_,
							"<strong>Local timeout is not a number!</strong><br/>\n");
				}
				timeout = t;
			} else {
				formDataIsReady = false;
				timeoutValue = "" + DEFAULT_TIMEOUT_;
				timeout = DEFAULT_TIMEOUT_;
			}
			LOGGER_.info("timeout: {}", timeout);

			if (params.containsKey(FIELD_GLOBAL_TIMEOUT_)) {
				globalTimeoutValue = params.get(FIELD_GLOBAL_TIMEOUT_);
				if (globalTimeoutValue.isEmpty()) {
					globalTimeout = Integer.MAX_VALUE;
				} else {
					int t;
					try {
						t = Integer.parseInt(globalTimeoutValue);
					} catch (final NumberFormatException e) {
						formDataIsReady = false;
						t = DEFAULT_GLOBAL_TIMEOUT_;
						validationMessages.put(FIELD_GLOBAL_TIMEOUT_,
								"<strong>Global timeout is not a number!</strong><br/>\n");
					}
					globalTimeout = t;
				}
			} else {
				formDataIsReady = false;
				globalTimeoutValue = "" + DEFAULT_GLOBAL_TIMEOUT_;
				globalTimeout = DEFAULT_GLOBAL_TIMEOUT_;
			}
			LOGGER_.info("globalTimeout: {}", globalTimeout);

			if (params.containsKey(FIELD_ONTOLOGIES_)
					&& files.containsKey(FIELD_ONTOLOGIES_)) {
				ontologiesFileName = params.get(FIELD_ONTOLOGIES_);
				final File ontolgiesTmpFile = new File(
						files.get(FIELD_ONTOLOGIES_));
				LOGGER_.info("ontolgiesTmpFile: {}", ontolgiesTmpFile);
				if (ontolgiesTmpFile.exists()) {
					LOGGER_.info("copying ontologies");
					Files.copy(ontolgiesTmpFile.toPath(),
							ontologiesFile_.toPath(),
							StandardCopyOption.REPLACE_EXISTING);
				} else {
					formDataIsReady = false;
					validationMessages.put(FIELD_ONTOLOGIES_,
							"<strong>Ontologies archive not provided!</strong><br/>\n");
				}
			} else {
				formDataIsReady = false;
				ontologiesFileName = null;
			}
			LOGGER_.info("ontologiesFileName: {}", ontologiesFileName);

			queryGenerationOptions = createQueryGenerationOptions(
					params.containsKey(FIELD_DIRECT_),
					params.containsKey(FIELD_UNTOLD_),
					!params.containsKey(FIELD_TAUT_),
					params.containsKey(FIELD_NOBOTTOM_));

		} catch (final IOException | ResponseException e) {
			return newErrorResponse("Cannot parse the request!", e);
		}

		if (formDataIsReady) {
			LOGGER_.info("Starting the experiments!");
			try {

				synchronized (this) {
					if (experimentProcess_ != null) {
						if (experimentProcess_.isAlive()) {
							return newFixedLengthResponse(
									TEMPLATE_ALREADY_RUNNING_);
						} else {
							try {
								updateExperimentLog();
							} catch (final IOException e) {
								return newErrorResponse(
										"Cannot read experiment log!", e);
							}
							experimentProcess_ = null;
							final Response response = newFixedLengthResponse(
									Status.REDIRECT, NanoHTTPD.MIME_HTML, "");
							response.addHeader("Location", "/done/");
							return response;
						}
					}
					// else
					experimentLog_.setLength(0);
					experimentProcess_ = new ProcessBuilder(
							substituteCommand(command_, timeout, globalTimeout,
									queryGenerationOptions))
											.redirectErrorStream(true).start();
				}

				final Response response = newFixedLengthResponse(
						Status.REDIRECT, NanoHTTPD.MIME_HTML, "");
				response.addHeader("Location", "/log/");
				return response;

			} catch (final IOException e) {
				return newErrorResponse("Cannot start the experiment!", e);
			}
		} else {
			return newFixedLengthResponse(String.format(TEMPLATE_INDEX_,
					validationMessages.get(FIELD_TIMEOUT_), timeoutValue,
					validationMessages.get(FIELD_GLOBAL_TIMEOUT_),
					globalTimeoutValue,
					validationMessages.get(FIELD_ONTOLOGIES_)));
		}

	}

	private String createQueryGenerationOptions(final boolean direct,
			final boolean untold, final boolean taut, final boolean nobottom) {
		final StringBuilder result = new StringBuilder();
		if (direct) {
			result.append("--direct ");
		}
		if (untold) {
			result.append("--untold ");
		}
		if (taut) {
			result.append("--taut ");
		}
		if (nobottom) {
			result.append("--nobottom ");
		}
		return result.toString();
	}

	private Response logView(final IHTTPSession session) {
		LOGGER_.info("log view");
		return newFixedLengthResponse(String.format(TEMPLATE_LOG_, ""));
	}

	private synchronized Response doneView(final IHTTPSession session) {
		LOGGER_.info("done view");

		// Paste the SVG plot into the template.
		final StringBuilder plotString = new StringBuilder("<p>");
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(plotFile_));
			String line = in.readLine();// skip the first line
			while ((line = in.readLine()) != null) {
				plotString.append(line);
			}
		} catch (IOException e) {
			return newErrorResponse("Cannot read the plot!", e);
		} finally {
			Utils.closeQuietly(in);
		}
		plotString.append("</p>");

		return newFixedLengthResponse(String.format(TEMPLATE_DONE_,
				plotString.toString(), experimentLog_.toString()));
	}

	private Response killView(final IHTTPSession session) {
		LOGGER_.info("kill view");
		if (experimentProcess_ != null && experimentProcess_.isAlive()) {
			experimentProcess_.destroyForcibly();
			experimentProcess_ = null;
		}
		final Response response = newFixedLengthResponse(Status.REDIRECT,
				NanoHTTPD.MIME_HTML, "");
		response.addHeader("Location", "/");
		response.addHeader("Cache-Control", "no-cache");
		return response;
	}

	private synchronized Response logSourceView(final IHTTPSession session) {
		LOGGER_.info("log source view");

		try {

			final StringBuilder message = new StringBuilder();

			if (experimentProcess_ != null) {

				final boolean isDead = !experimentProcess_.isAlive();
				updateExperimentLog();

				// encode to text/event-stream
				final String[] lines = experimentLog_.toString().split("\n",
						-1);
				if (isDead) {
					experimentProcess_ = null;
					message.append("id: last_log\n");
				} else {
					message.append("id: log\n");
				}
				for (final String line : lines) {
					message.append("data: ").append(line).append("\n");
				}
				message.append("\n");

			}

			final Response response = newFixedLengthResponse(
					message.toString());
			response.addHeader("Content-Type", "text/event-stream");
			response.addHeader("Cache-Control", "no-cache");
			return response;

		} catch (final IOException e) {
			LOGGER_.error("Cannot read experiment log!", e);
			final Response response = newFixedLengthResponse(
					Status.INTERNAL_ERROR, NanoHTTPD.MIME_HTML,
					"Cannot read experiment log!\n" + e.getMessage());
			response.addHeader("Content-Type", "text/event-stream");
			response.addHeader("Cache-Control", "no-cache");
			return response;
		}

	}

	private Response resultsView(final IHTTPSession session) {
		LOGGER_.info("results view");
		if (!resultsFile_.exists() || resultsFile_.isDirectory()) {
			return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_HTML,
					String.format(TEMPLATE_NOT_FOUND_, resultsFile_.getPath()));
		}
		// else
		try {
			final InputStream data = new FileInputStream(resultsFile_);
			final Response response = newChunkedResponse(Status.OK,
					"application/octet-stream", data);
			response.addHeader("Content-Disposition",
					"attachment; filename=\"results.tar.gz\"");
			return response;
		} catch (final FileNotFoundException e) {
			return newErrorResponse("Cannot find the results file!", e);
		}
	}

	private void updateExperimentLog() throws IOException {
		final InputStream in = experimentProcess_.getInputStream();
		final int available = in.available();
		LOGGER_.debug("available: {}", available);
		final byte[] buffer = new byte[available];
		final int read = in.read(buffer);
		LOGGER_.debug("log: {}", read);
		if (read >= 0) {
			// something was actually read
			final String s = new String(buffer, 0, read);
			LOGGER_.debug("string: {}", s);
			experimentLog_.append(s);
		}
	}

	private Response newErrorResponse(final String message,
			final Throwable cause) {
		LOGGER_.error(message, cause);
		return newFixedLengthResponse(Status.INTERNAL_ERROR,
				NanoHTTPD.MIME_HTML, String.format(TEMPLATE_INTERNAL_ERROR_,
						message, cause.getMessage()));
	}

	private static final String PATTERN_TIMEPUT_ = "<t>";
	private static final String PATTERN_GLOBAL_TIMEPUT_ = "<g>";
	private static final String PATTERN_QUERY_GENERATION_OPTIONS_ = "<q>";

	private static String substituteCommand(final String command,
			final String pattern, final String value) {
		final Pattern p = Pattern.compile(Pattern.quote(pattern));
		final Matcher m = p.matcher(command);
		if (m == null) {
			return command;
		} else {
			return m.replaceAll(value);
		}
	}

	private static String[] substituteCommand(final String[] command,
			final int localTimeout, final int globalTimeout,
			final String queryGenerationOptions) {
		final String[] result = new String[command.length];
		for (int i = 0; i < command.length; i++) {
			result[i] = substituteCommand(
					substituteCommand(
							substituteCommand(command[i], PATTERN_TIMEPUT_,
									"" + localTimeout),
							PATTERN_GLOBAL_TIMEPUT_, "" + globalTimeout),
					PATTERN_QUERY_GENERATION_OPTIONS_,
					"" + queryGenerationOptions);
		}
		return result;
	}

}
