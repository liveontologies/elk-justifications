package org.semanticweb.elk.justifications.experiments;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CsvQueryIterator<Q> extends QueryIterator<Q> {

	private final BufferedReader queryReader_;
	private final Queue<Q> queriesToDo_ =
			new ConcurrentLinkedQueue<Q>();
	
	public CsvQueryIterator(final QueryFactory<Q> factory,
			final String queryFileName) throws ExperimentException {
		super(factory);
		
		try {
			queryReader_ = new BufferedReader(new FileReader(queryFileName));
		} catch (final FileNotFoundException e) {
			throw new ExperimentException(e);
		}
		
	}

	@Override
	public boolean hasNext() {
		if (!queriesToDo_.isEmpty()) {
			return true;
		}
		try {
			enqueueNextConclusion();
			return !queriesToDo_.isEmpty();
		} catch (final IOException e) {
			return false;
		}
	}

	@Override
	public Q next() throws ExperimentException {
		try {
			Q query = queriesToDo_.poll();
			if (query == null) {
				enqueueNextConclusion();
				query = queriesToDo_.poll();
			}
			return query;
		} catch (final IOException e) {
			throw new ExperimentException(e);
		}
	}

	private void enqueueNextConclusion() throws IOException {
		
		final String line = queryReader_.readLine();
		if (line == null) {
			return;
		}
		
		final String[] columns = line.split(",");
		if (columns.length < 2) {
			return;
		}
		
		final String subIri = strip(columns[0]);
		final String supIri = strip(columns[1]);
		
		final Q query = factory_.createQuery(subIri, supIri);
		
		queriesToDo_.add(query);
	}
	
	private static String strip(final String s) {
		final String trimmed = s.trim();
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

	@Override
	public void dispose() {
		try {
			queryReader_.close();
		} catch (final IOException e) {
			// Ignore.
		}
	}
	
}
