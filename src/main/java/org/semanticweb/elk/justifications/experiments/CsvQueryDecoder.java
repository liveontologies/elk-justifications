package org.semanticweb.elk.justifications.experiments;

import com.google.common.base.Preconditions;

public class CsvQueryDecoder {

	public static interface Factory<Q> {
		Q createQuery(String subIri, String supIri);
	}

	public static <Q> Q decode(final String query, final Factory<Q> factory) {
		Preconditions.checkNotNull(query);

		final String[] columns = query.split(" ");
		if (columns.length < 2) {
			throw new IllegalArgumentException(
					"Invalie query format: " + query);
		}

		return factory.createQuery(columns[0], columns[1]);
	}

}
