package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class BaseJustificationComputationTest<C, A> {

	public static Collection<Object[]> getParameters(
			final List<JustificationComputation.Factory<?, ?>> computationFactories,
			final String testInputDir) throws URISyntaxException {

		final Collection<Object[]> inputFiles = collectJustificationTestInputFiles(
				testInputDir, BaseJustificationComputationTest.class);

		final List<Object[]> result = new ArrayList<Object[]>();
		for (final JustificationComputation.Factory<?, ?> c : computationFactories) {
			for (final Object[] files : inputFiles) {
				final Object[] r = new Object[files.length + 1];
				r[0] = c;
				System.arraycopy(files, 0, r, 1, files.length);
				result.add(r);
			}
		}

		return result;
	}

	private final JustificationComputation.Factory<C, A> factory_;
	private final File ontoFile_;
	private final Map<File, File[]> entailFilesPerJustFile_;

	public BaseJustificationComputationTest(
			final JustificationComputation.Factory<C, A> factory,
			final File ontoFile,
			final Map<File, File[]> entailFilesPerJustFile) {
		this.factory_ = factory;
		this.ontoFile_ = ontoFile;
		this.entailFilesPerJustFile_ = entailFilesPerJustFile;
	}

	public JustificationComputation.Factory<C, A> getFactory() {
		return factory_;
	}

	public File getOntoFile() {
		return ontoFile_;
	}

	public Map<File, File[]> getJustFilePerEntailFiles() {
		return entailFilesPerJustFile_;
	}

	public void setUp() {
		// Empty default.
	}

	public abstract Set<? extends Set<? extends A>> getActualJustifications(
			final File entailFile) throws Exception;

	public abstract Set<? extends Set<? extends A>> getExpectedJustifications(
			final File[] justFiles) throws Exception;

	public void dispose() {
		// Empty default.
	}

	@Before
	public void before() {
		setUp();
	}

	@Test
	public void test() throws Exception {

		for (final Map.Entry<File, File[]> entry : entailFilesPerJustFile_
				.entrySet()) {

			final Set<? extends Set<? extends A>> justifications = getActualJustifications(
					entry.getKey());

			final Set<? extends Set<? extends A>> expected = getExpectedJustifications(
					entry.getValue());

			// @formatter:off
			final String inputsMessage =
					"computation: " + factory_.getClass() + "\n"
					+ "ontology: " + ontoFile_ + "\n"
					+ "entailment: " + entry.getKey() + "\n";
			// @formatter:on

			Assert.assertEquals(
					"Wrong number of justifications!\n" + inputsMessage,
					expected.size(), justifications.size());
			Assert.assertEquals(inputsMessage, expected, justifications);

		}

	}

	@After
	public void after() {
		dispose();
	}

	public static final String OWL_EXTENSION = ".owl";
	public static final String ENTAILMENT_EXTENSION = ".entailment";
	public static final String JUSTIFICATION_EXTENSION = ".justification";
	public static final String JUSTIFICATION_DIR_NAME = "justifications";

	public static Collection<Object[]> collectJustificationTestInputFiles(
			final String testInputDir, final Class<?> srcClass)
			throws URISyntaxException {

		final List<Object[]> result = new ArrayList<>();

		final URI inputDirURI = srcClass.getClassLoader()
				.getResource(testInputDir).toURI();

		// Assume it's not in JAR :-P
		final File rootDir = new File(inputDirURI);

		// For every ontology
		final File[] ontoFiles = rootDir.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(final File dir, final String name) {
				return name.endsWith(OWL_EXTENSION);
			}

		});
		if (ontoFiles == null) {
			throw new RuntimeException("Cannot list files in " + rootDir);
		}
		for (final File ontoFile : ontoFiles) {

			// For every entailment
			final String baseName = Utils.dropExtension(ontoFile.getName());
			final File[] entailFiles = rootDir.listFiles(new FilenameFilter() {

				@Override
				public boolean accept(final File dir, final String name) {
					return name.startsWith(baseName)
							&& name.endsWith(ENTAILMENT_EXTENSION);
				}

			});
			if (entailFiles == null) {
				throw new RuntimeException(
						"Cannot list files in " + entailFiles);
			}
			final Map<File, File[]> entailFilesPerJustFile = new HashMap<>();
			for (final File entailDir : entailFiles) {

				if (!entailDir.isDirectory()) {
					throw new RuntimeException("Not a directory: " + entailDir);
				}

				final File entailFile = new File(entailDir,
						baseName + ENTAILMENT_EXTENSION);
				if (!entailFile.exists()) {
					throw new RuntimeException(
							"No entailment file: " + entailFile);
				}

				// Collect justification files
				final File justDir = new File(entailDir,
						JUSTIFICATION_DIR_NAME);
				if (!justDir.exists()) {
					// Ignore!
					continue;
				}
				if (!justDir.isDirectory()) {
					throw new RuntimeException("Not a directory: " + justDir);
				}
				final File[] justFiles = justDir.listFiles();
				if (justFiles == null) {
					throw new RuntimeException(
							"Cannot list files in " + justFiles);
				}

				entailFilesPerJustFile.put(entailFile, justFiles);
			}

			result.add(new Object[] { ontoFile, entailFilesPerJustFile });
		}

		return result;
	}

}
