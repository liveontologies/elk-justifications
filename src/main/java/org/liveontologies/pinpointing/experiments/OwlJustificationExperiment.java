package org.liveontologies.pinpointing.experiments;

import java.io.File;

import org.liveontologies.proofs.CsvQueryProofProvider;
import org.liveontologies.proofs.OwlProofProvider;
import org.liveontologies.proofs.ProofProvider;
import org.liveontologies.puli.Inference;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;

public abstract class OwlJustificationExperiment<O extends OwlJustificationExperiment.Options>
		extends
		BaseJustificationExperiment<O, OWLAxiom, Inference<OWLAxiom>, OWLAxiom> {

	private static final Logger LOGGER_ = LoggerFactory
			.getLogger(OwlJustificationExperiment.class);

	public static final String ONTOLOGY_OPT = "ontology";

	public static class Options extends BaseJustificationExperiment.Options {
		@Arg(dest = ONTOLOGY_OPT)
		public File ontologyFile;
	}

	private File ontologyFile_;

	private OWLOntologyManager manager_ = null;
	private OWLDataFactory factory_ = null;

	private OWLOntologyManager getManager() {
		if (manager_ == null) {
			manager_ = OWLManager.createOWLOntologyManager();
		}
		return manager_;
	}

	private OWLDataFactory getFactory() {
		if (factory_ == null) {
			factory_ = getManager().getOWLDataFactory();
		}
		return factory_;
	}

	@Override
	protected void addArguments(final ArgumentParser parser) {
		parser.addArgument(ONTOLOGY_OPT)
				.type(Arguments.fileType().verifyExists().verifyCanRead())
				.help("ontology file");
	}

	@Override
	protected void init(final O options) throws ExperimentException {
		manager_ = null;
		LOGGER_.info("ontologyFile: {}", options.ontologyFile);
		this.ontologyFile_ = options.ontologyFile;
	}

	@Override
	protected ProofProvider<String, OWLAxiom, Inference<OWLAxiom>, OWLAxiom> newProofProvider()
			throws ExperimentException {
		manager_ = null;
		return new CsvQueryProofProvider<>(
				new CsvQueryDecoder.Factory<OWLAxiom>() {

					@Override
					public OWLAxiom createQuery(final String subIri,
							final String supIri) {
						return getFactory().getOWLSubClassOfAxiom(
								getFactory().getOWLClass(IRI.create(subIri)),
								getFactory().getOWLClass(IRI.create(supIri)));
					}

				}, new OwlProofProvider(ontologyFile_, getManager()));
	}

}
