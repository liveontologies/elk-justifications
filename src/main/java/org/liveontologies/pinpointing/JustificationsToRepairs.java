package org.liveontologies.pinpointing;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

public class JustificationsToRepairs {

	public static void main(final String[] args)
			throws OWLOntologyCreationException, IOException,
			OWLOntologyStorageException {

		final File justDir = new File(args[0]);
		final File outDir = new File(args[1]);
		Utils.cleanDir(outDir);

		final OWLOntologyManager manager = OWLManager
				.createOWLOntologyManager();

		// Load justifications.
		final Set<Set<? extends OWLAxiom>> justs = new HashSet<>();
		final File[] justFiles = justDir.listFiles();
		if (justFiles == null) {
			throw new RuntimeException("Cannot list files in " + justDir);
		}
		String name = "DeFaUlT";
		for (final File justFile : justFiles) {
			final OWLOntology justOnto = manager
					.loadOntologyFromOntologyDocument(justFile);
			justs.add(justOnto.getLogicalAxioms());

			final String fileName = justFile.getName();
			final int dotIndex = fileName.indexOf('.');
			name = dotIndex < 0 ? fileName : fileName.substring(0, dotIndex);
		}

		// Make product and minimize.
		Set<Set<OWLAxiom>> repairs = new HashSet<>();
		repairs.add(Collections.<OWLAxiom> emptySet());
		for (final Set<? extends OWLAxiom> just : justs) {
			// Join with repairs so far.
			final Set<Set<OWLAxiom>> newRepairs = new HashSet<>();
			for (final Set<OWLAxiom> repair : repairs) {
				for (final OWLAxiom axiom : just) {
					final Set<OWLAxiom> newRepair = new HashSet<>();
					newRepair.addAll(repair);
					newRepair.add(axiom);
					// Minimize.
					Utils.merge(newRepair, newRepairs);
				}
			}
			repairs = newRepairs;
		}

		// Save the repairs.
		final int maxIndex = repairs.size() <= 1 ? repairs.size()
				: repairs.size() - 1;
		int index = 0;
		for (final Set<OWLAxiom> repair : repairs) {
			final OWLOntology repairOnt = manager.createOntology(repair);
			final File repairFile = new File(outDir,
					String.format(
							"%s.%0" + Utils.digitCount(maxIndex) + "d.repair",
							name, index));
			OutputStream outputStream = null;
			try {
				outputStream = new FileOutputStream(repairFile);
				manager.saveOntology(repairOnt,
						new FunctionalSyntaxDocumentFormat(), outputStream);
			} finally {
				if (outputStream != null) {
					outputStream.close();
				}
			}
			index++;
		}

	}

}
