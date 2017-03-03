package org.semanticweb.elk.justifications.experiments;

import org.liveontologies.puli.Util;

public class CsvQueryDecoder {

	public static interface Factory<Q> {
		Q createQuery(String subIri, String supIri);
	}

	public static <Q> Q decode(final String query, final Factory<Q> factory) {
		Util.checkNotNull(query);

		final String[] columns = query.split(",");
		if (columns.length < 2) {
			throw new IllegalArgumentException(
					"Invalie query format: " + query);
		}

		final String subIri = strip(columns[0]);
		final String supIri = strip(columns[1]);

		return factory.createQuery(subIri, supIri);
	}

	public static String strip(final String s) {
		final String trimmed = s.trim();
		// TODO: would be nice to have proper parsing :-P
		int start = 0;
		if (trimmed.charAt(0) == '"') {
			start = 1;
		}
		int end = trimmed.length();
		if (trimmed.charAt(trimmed.length() - 1) == '"') {
			end = trimmed.length() - 1;
		}
		return trimmed.substring(start, end);
	}

}
