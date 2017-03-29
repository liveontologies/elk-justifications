package org.semanticweb.elk.justifications.experiments;

public abstract class ReasonerJustificationExperiment<C, A, R>
		extends BaseJustificationExperiment<C, A> {

	private R reasoner_;

	@Override
	public void init(final String[] args) throws ExperimentException {
		super.init(args);

		final int requiredArgCount = 2;

		if (args.length < requiredArgCount) {
			throw new ExperimentException("Insufficient arguments!");
		}

		final String ontologyFileName = args[1];

		reasoner_ = loadAndClassifyOntology(ontologyFileName);

	}

	protected abstract R loadAndClassifyOntology(String ontologyFileName)
			throws ExperimentException;

	public R getReasoner() {
		return reasoner_;
	}

}
