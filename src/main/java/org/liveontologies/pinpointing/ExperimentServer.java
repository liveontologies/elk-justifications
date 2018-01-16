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
import java.util.Arrays;
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
	public static final String OPT_WORKSPACE = "workspace";
	public static final String OPT_COMMAND = "command";

	public static final Integer DEFAULT_PORT = 80;

	public static class Options {
		@Arg(dest = OPT_PORT)
		public Integer port;
		@Arg(dest = OPT_WORKSPACE)
		public File workspace;
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
		parser.addArgument("--" + OPT_WORKSPACE).type(File.class).required(true)
				.help("the directory in which the experiment manipulates files");
		parser.addArgument(OPT_COMMAND).nargs("+").help(
				"the command that starts the experiment and its arguments\n"
						+ "(" + PATTERN_TIMEPUT_
						+ " will be substituted for timeout and "
						+ PATTERN_GLOBAL_TIMEPUT_ + " for global timeout)");

		try {

			final Options opt = new Options();
			parser.parseArgs(args, opt);

			LOGGER_.info("Binding server to port {}", opt.port);
			LOGGER_.info("workspace={}", opt.workspace);
			LOGGER_.info("command={}", Arrays.toString(opt.command));
			new ExperimentServer(opt.port, opt.workspace, opt.command);

		} catch (final IOException e) {
			LOGGER_.error("Cannot start server!", e);
			System.exit(1);
		} catch (final ArgumentParserException e) {
			parser.handleError(e);
			System.exit(2);
		}
	}

	private static final String WS_INPUT_ = "input";
	private static final String WS_RESULTS_ = "results";
	private static final String WS_RESULTS_ARCHIVE_ = "results.zip";
	private static final String WS_PLOT_ = "plot.svg";

	public ExperimentServer(final int port, final File workspace,
			final String... command) throws IOException {
		super(port);
		this.workspace_ = workspace;
		Utils.cleanIfNotDir(this.workspace_);
		this.inputDir_ = new File(workspace, WS_INPUT_);
		Utils.cleanIfNotDir(this.inputDir_);
		this.resultsDir_ = new File(workspace, WS_RESULTS_);
		this.resultsFile_ = new File(workspace, WS_RESULTS_ARCHIVE_);
		this.plotFile_ = new File(workspace, WS_PLOT_);
		this.command_ = command;
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
		LOGGER_.info("Server running ;-)");
	}

	private final File workspace_;
	private final File inputDir_;
	private final File plotFile_;
	private final File resultsDir_;
	private final File resultsFile_;
	private final String[] command_;

	@GuardedBy("this")
	private Process experimentProcess_ = null;
	@GuardedBy("this")
	private StringBuilder experimentLog_ = new StringBuilder();

	private static final String FIELD_TIMEOUT_ = "timeout";
	private static final String FIELD_GLOBAL_TIMEOUT_ = "global_timeout";
	private static final String FIELD_SOURCE_ = "source";
	private static final String FIELD_SOURCE_FILE_ = "file";
	private static final String FIELD_SOURCE_WEB_ = "web";
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
			+ "    <p><label for='" + FIELD_ONTOLOGIES_ + "'>Either an ontology loadable by OWL API,<br/>\n"
			+ "      or an archive with input ontologies (*.tar.gz or *.zip)<br/>\n"
			+ "      (The ontology files must be in the root of the archive!),<br/>\n"
			+ "      or a link from which the ontology or an archive should be downloaded:</label><br/>\n"
			+ "%s"// validation message
			+ "    <input type='radio' name='" + FIELD_SOURCE_ + "' value='" + FIELD_SOURCE_FILE_ + "' checked\n"
			+ "      onclick=\"document.getElementById('onto_input').innerHTML = '<input type=\\'file\\' name=\\'" + FIELD_ONTOLOGIES_ + "\\' accept=\\'.tar.gz,.tgz,.zip,.owl\\' required>'\"> Upload a file\n"
			+ "    <input type='radio' name='" + FIELD_SOURCE_ + "' value='" + FIELD_SOURCE_WEB_ + "'\n"
			+ "      onclick=\"document.getElementById('onto_input').innerHTML = '<input type=\\'text\\' name=\\'" + FIELD_ONTOLOGIES_ + "\\' required>'\"> Download from web\n"
			+ "    <span id='onto_input'><input type='file' name='" + FIELD_ONTOLOGIES_ + "' accept='.tar.gz,.tgz,.zip,.owl' required><span></p>\n"
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
			+ "  <p>View the results <a href=/results/>here</a>."
			+ "  Download the results from <a href=/results.zip>here</a>."
			+ "  Or start from beginning <a href=/>here</a>.</p>\n"
			+ "  <pre id='log'>%s</pre>\n"
			+ "</body>\n"
			+ "</html>";
	private static final String TEMPLATE_RESULTS_ = "<!doctype html>\n"
			+ "<body>\n"
			+ "  <h1>Experiment results</h1>\n"
			+ "  <p>Download the results from <a href=/results.zip>here</a>."
			+ "  See the log <a href=/done/>here</a>.\n"
			+ "  Or start from beginning <a href=/>here</a>.</p>\n"
			+ "  %s\n"// The plot
			+ "  %s\n"// The result list
			+ "</body>";
	// The first line and no <html> tag seem to have huge impact on performance!
	private static final String TEMPLATE_RESULT_FILE_ = "<!doctype html>\n"
			+ "<body>\n"
			+ "  <h1>%s</h1>\n"// Title
			+ "  <div id=\"handsontable-container\"></div>\n"
			+ "  <pre id='data'>%s</pre>\n"// Result file
			+ "  <script src=\"https://cdn.jsdelivr.net/handsontable/0.28.4/handsontable.full.min.js\"></script>\n"
			+ "  <script src=\"https://cdn.jsdelivr.net/papaparse/4.1.2/papaparse.min.js\"></script>\n"
			+ "  <script type='text/javascript'>\n"
			+ "    var dataElement = document.getElementById('data')\n"
			+ "    var dataString = dataElement.innerHTML\n"
			+ "    dataElement.innerHTML = ''\n"
			+ "    var data = Papa.parse(dataString, {header: true, skipEmptyLines: true})\n"
			+ "    var handsontableContainer = document.getElementById('handsontable-container')\n"
			+ "    Handsontable(handsontableContainer, {data: data.data, rowHeaders: true, colHeaders: data.meta.fields, columnSorting: true, wordWrap: false})\n"
			+ "  </script>\n"
			+ "  <link rel=\"stylesheet\" href=\"https://cdn.jsdelivr.net/handsontable/0.28.4/handsontable.full.min.css\">\n"
			+ "</body>";
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

	private static final Pattern URI_INDEX_ = Pattern.compile("^/$");
	private static final Pattern URI_LOG_ = Pattern.compile("^/log/?$");
	private static final Pattern URI_LOG_SOURCE_ = Pattern
			.compile("^/log_source/?$");
	private static final Pattern URI_DONE_ = Pattern.compile("^/done/?$");
	private static final Pattern URI_KILL_ = Pattern.compile("^/kill/?$");
	private static final Pattern URI_RESULTS_ = Pattern.compile("^/results/?$");
	private static final Pattern URI_RESULTS_FILE_ = Pattern
			.compile("^/results/(?<file>[^/]+)$");
	private static final Pattern URI_RESULTS_ARCHIVE_ = Pattern
			.compile("^/results.zip$");

	@Override
	public Response serve(final IHTTPSession session) {
		LOGGER_.info("request URI: {}", session.getUri());
		try {
			final URI requestUri = new URI(session.getUri());
			final Matcher uriResultsFileMatcher = URI_RESULTS_FILE_
					.matcher(requestUri.getPath());
			if (URI_INDEX_.matcher(requestUri.getPath()).matches()) {
				return indexView(session);
			} else if (URI_LOG_.matcher(requestUri.getPath()).matches()) {
				return logView(session);
			} else if (URI_LOG_SOURCE_.matcher(requestUri.getPath())
					.matches()) {
				return logSourceView(session);
			} else if (URI_DONE_.matcher(requestUri.getPath()).matches()) {
				return doneView(session);
			} else if (URI_RESULTS_.matcher(requestUri.getPath()).matches()) {
				return resultsView(session);
			} else if (uriResultsFileMatcher.matches()) {
				return resultFileView(session,
						uriResultsFileMatcher.group("file"));
			} else if (URI_KILL_.matcher(requestUri.getPath()).matches()) {
				return killView(session);
			} else if (URI_RESULTS_ARCHIVE_.matcher(requestUri.getPath())
					.matches()) {
				return resultsArchiveView(session);
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
		final String sourceValue;
		final String ontologies;
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

			if (params.containsKey(FIELD_SOURCE_)) {
				sourceValue = params.get(FIELD_SOURCE_);
				if (FIELD_SOURCE_FILE_.equals(sourceValue)) {

					if (params.containsKey(FIELD_ONTOLOGIES_)
							&& files.containsKey(FIELD_ONTOLOGIES_)) {
						Utils.cleanDir(inputDir_);
						final File source = new File(
								files.get(FIELD_ONTOLOGIES_));
						final File target = new File(inputDir_,
								params.get(FIELD_ONTOLOGIES_));
						LOGGER_.info("ontolgiesTmpFile: {}", source);
						if (source.exists()) {
							LOGGER_.info("copying ontologies");
							Files.copy(source.toPath(), target.toPath(),
									StandardCopyOption.REPLACE_EXISTING);
							ontologies = target.getAbsolutePath();
						} else {
							formDataIsReady = false;
							ontologies = null;
							validationMessages.put(FIELD_ONTOLOGIES_,
									"<strong>Ontologies archive not provided!</strong><br/>\n");
						}
					} else {
						formDataIsReady = false;
						ontologies = null;
					}

				} else if (FIELD_SOURCE_WEB_.equals(sourceValue)) {

					if (params.containsKey(FIELD_ONTOLOGIES_)) {
						ontologies = params.get(FIELD_ONTOLOGIES_);
					} else {
						formDataIsReady = false;
						ontologies = null;
					}

				} else {
					return newErrorResponse("Illegal value of field "
							+ FIELD_SOURCE_ + ": " + params.get(FIELD_SOURCE_));
				}
			} else {
				formDataIsReady = false;
				sourceValue = null;
				ontologies = null;
			}
			LOGGER_.info("ontologiesValue: {}", ontologies);

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
					experimentProcess_ = new ProcessBuilder(substituteCommand(
							command_, timeout, globalTimeout, sourceValue,
							ontologies, queryGenerationOptions))
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
		return newFixedLengthResponse(
				String.format(TEMPLATE_DONE_, experimentLog_.toString()));
	}

	private synchronized Response resultsView(final IHTTPSession session) {
		LOGGER_.info("results view");

		if (!resultsDir_.exists() || !resultsDir_.isDirectory()) {
			return newErrorResponse("Results directory does not exist!");
		}
		// else

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

		final StringBuilder resultList = new StringBuilder("<ul>\n");
		final String[] fileNames = resultsDir_.list();
		Arrays.sort(fileNames);
		for (final String fileName : fileNames) {
			resultList.append("<li><a href='/results/");
			resultList.append(fileName);
			resultList.append("'>");
			resultList.append(fileName);
			resultList.append("</a></li>\n");
		}
		resultList.append("</ul>");

		return newFixedLengthResponse(String.format(TEMPLATE_RESULTS_,
				plotString.toString(), resultList.toString()));
	}

	private synchronized Response resultFileView(final IHTTPSession session,
			final String fileName) {
		LOGGER_.info("result file view: {}", fileName);

		// Paste the file into the template.
		// TODO: do this with streams!
		final StringBuilder fileString = new StringBuilder();
		BufferedReader in = null;
		try {
			in = new BufferedReader(
					new FileReader(new File(resultsDir_, fileName)));
			String line;
			while ((line = in.readLine()) != null) {
				fileString.append(line).append("\n");
			}
		} catch (IOException e) {
			return newErrorResponse("Cannot read the result file!", e);
		} finally {
			Utils.closeQuietly(in);
		}

		return newFixedLengthResponse(String.format(TEMPLATE_RESULT_FILE_,
				fileName, fileString.toString()));
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

	private Response resultsArchiveView(final IHTTPSession session) {
		LOGGER_.info("results archive view");
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
					"attachment; filename=\"results.zip\"");
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

	private Response newErrorResponse(final String message) {
		LOGGER_.error(message);
		return newFixedLengthResponse(Status.INTERNAL_ERROR,
				NanoHTTPD.MIME_HTML,
				String.format(TEMPLATE_INTERNAL_ERROR_, message, ""));
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
	private static final String PATTERN_SOURCE_ = "<s>";
	private static final String PATTERN_ONTOLOGIES_ = "<o>";
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
			final String source, final String ontologies,
			final String queryGenerationOptions) {
		final String[] result = new String[command.length];
		for (int i = 0; i < command.length; i++) {
			result[i] = substituteCommand(
					substituteCommand(
							substituteCommand(
									substituteCommand(
											substituteCommand(command[i],
													PATTERN_TIMEPUT_,
													"" + localTimeout),
											PATTERN_GLOBAL_TIMEPUT_,
											"" + globalTimeout),
									PATTERN_SOURCE_, source),
							PATTERN_ONTOLOGIES_, ontologies),
					PATTERN_QUERY_GENERATION_OPTIONS_, queryGenerationOptions);
		}
		return result;
	}

}
