package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadAndSaveByOwlapi {

	private static final Logger LOG = LoggerFactory
			.getLogger(LoadAndSaveByOwlapi.class);

	public static void main(final String[] args) {

		if (args.length < 2) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}

		final File inputFile = new File(args[0]);
		final File outputFile = new File(args[1]);
		if (outputFile.exists()) {
			Utils.recursiveDelete(outputFile);
		}

		final OWLOntologyManager manager = OWLManager
				.createOWLOntologyManager();

		OutputStream output = null;

		try {

			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager
					.loadOntologyFromOntologyDocument(inputFile);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start) / 1000.0);
			LOG.info("Loaded ontology: {}", ont);

			output = new FileOutputStream(outputFile);
			manager.saveOntology(ont, new FunctionalSyntaxDocumentFormat(),
					output);

		} catch (final OWLOntologyCreationException e) {
			LOG.error("Could not load the ontology!", e);
			System.exit(2);
		} catch (final FileNotFoundException e) {
			LOG.error("File not found!", e);
			System.exit(2);
		} catch (final OWLOntologyStorageException e) {
			LOG.error("Could not save the ontology!", e);
			System.exit(2);
		} finally {
			Utils.closeQuietly(output);
		}

	}

}
