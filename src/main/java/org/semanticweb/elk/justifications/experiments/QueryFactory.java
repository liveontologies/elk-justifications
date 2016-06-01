package org.semanticweb.elk.justifications.experiments;

public interface QueryFactory<Q> {

	Q createQuery(String subIri, String supIri);

}
