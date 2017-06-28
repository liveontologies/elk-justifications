package org.liveontologies.pinpointing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLogicalAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.SetOntologyID;

import com.google.common.base.Optional;

public class ReorganizeTests {

	public static final String ENTAILMENT_EXTENSION = ".entailment";
	public static final String JUSTIFICATION_EXTENSION = ".justification";
	public static final String JUSTIFICATION_DIR_NAME = "justifications";
	public static final String DEFAULT_IRI = "http://example.com/";

	private static final OWLOntologyManager MANAGER = OWLManager
			.createOWLOntologyManager();

	public static void main(final String[] args) throws Exception {

		final String prefix = "/Users/petesko/git/elk-justifications/src/test/resources/";

		reorganizeTests(prefix + "real_world/go_cel.owl",
				prefix + "real_world/gene.query.random.owl",
				prefix + "real_world/gene.random.justifications",
				prefix + "test_input/real_world/go");

	}

	private static void reorganizeTests(final String ontoFilePath,
			final String entailFilePath, final String justDirPath,
			final String outDirPath) throws OWLOntologyCreationException,
			IOException, OWLOntologyStorageException {

		final File outDir = new File(outDirPath);
		Utils.cleanDir(outDir);

		final File ontoFile = new File(ontoFilePath);
		final OWLOntology ontology = MANAGER
				.loadOntologyFromOntologyDocument(ontoFile);

		System.out.println(
				"ontology.getOntologyID(): " + ontology.getOntologyID());
		final Optional<IRI> opt = ontology.getOntologyID().getOntologyIRI();
		if (opt.isPresent()) {
			final IRI ontoIri = opt.get();
			System.out.println("ontoIri: " + ontoIri);
			System.out.println(
					"ontoIri.getNamespace(): " + ontoIri.getNamespace());
			System.out.println(
					"ontoIri.getRemainder(): " + ontoIri.getRemainder());
		}

		// Save the ontology to the output.
		saveOntology(ontology, outDir, ontoFile.getName());

		// For every entailment ...
		final OWLOntology entailOnto = MANAGER
				.loadOntologyFromOntologyDocument(new File(entailFilePath));
		final Set<OWLLogicalAxiom> entailments = entailOnto.getLogicalAxioms();
		int index = 0;
		for (final OWLAxiom entailment : entailments) {
			handleEntailment(entailment, index++, entailments.size() - 1,
					ontology, justDirPath, outDir,
					Utils.dropExtension(ontoFile.getName()));
		}

	}

	private static void handleEntailment(final OWLAxiom entailment,
			final int index, final int maxIndex, final OWLOntology inputOnto,
			final String justDirPath, final File outDir,
			final String outputName) throws OWLOntologyCreationException,
			IOException, OWLOntologyStorageException {

		final int indexWidth = maxIndex <= 0 ? 1 : Utils.digitCount(maxIndex);
		System.out.println(String.format("entailment: %0" + indexWidth + "d %s",
				index, entailment));

		final File entailDir = new File(outDir,
				outputName + String.format(".%0" + indexWidth + "d%s", index,
						ENTAILMENT_EXTENSION));
		entailDir.mkdirs();

		final Optional<IRI> opt = inputOnto.getOntologyID().getOntologyIRI();
		final IRI entailIri;
		if (opt.isPresent()) {
			final IRI ontoIri = opt.get();
			entailIri = IRI.create(ontoIri.getNamespace(),
					ontoIri.getRemainder().or("") + "/" + entailDir.getName());
		} else {
			entailIri = IRI.create(DEFAULT_IRI, entailDir.getName());
		}
		final OWLOntology entailOnto = MANAGER.createOntology(new OWLOntologyID(
				entailIri.asIRI(), inputOnto.getOntologyID().getVersionIRI()));
		MANAGER.addAxiom(entailOnto, entailment);

		saveOntology(entailOnto, entailDir, outputName + ENTAILMENT_EXTENSION);

		// Move the justifications.
		final File justOutDir = new File(entailDir, JUSTIFICATION_DIR_NAME);
		justOutDir.mkdirs();
		final File justDir = new File(new File(justDirPath),
				Utils.toFileName(entailment));
		final File[] justFiles = justDir.listFiles();
		if (justFiles == null) {
			throw new IOException("Cannot list files in " + justDir);
		}
		final int justIndexWidth = justFiles.length <= 1 ? 1
				: Utils.digitCount(justFiles.length - 1);
		int justIndex = 0;
		for (final File justFile : justFiles) {

			final OWLOntology justOnt = MANAGER
					.loadOntologyFromOntologyDocument(justFile);
			MANAGER.applyChange(new SetOntologyID(justOnt,
					IRI.create(entailIri.getNamespace(),
							entailIri.getRemainder().or("") + String.format(
									"/justification%0" + justIndexWidth
											+ "d",
									justIndex))));

			saveOntology(justOnt, justOutDir,
					outputName + "." + justIndex + JUSTIFICATION_EXTENSION);

			justIndex++;
		}

	}

	private static void saveOntology(final OWLOntology ontology,
			final File outDir, final String fileName)
			throws FileNotFoundException, OWLOntologyStorageException {

		final File ontoOutFile = new File(outDir, fileName);
		OutputStream output = null;
		try {
			output = new FileOutputStream(ontoOutFile);
			MANAGER.saveOntology(ontology, new FunctionalSyntaxDocumentFormat(),
					output);
		} finally {
			Utils.closeQuietly(output);
		}

	}

}
