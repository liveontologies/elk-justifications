package org.semanticweb.elk.proofs.adapters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

public class DirectSatEncodingInferenceSetAdapter
		implements GenericInferenceSet<Integer, JustifiedInference<Integer, Integer>> {

	public static DirectSatEncodingInferenceSetAdapter load(
			final InputStream assumptions, final InputStream cnf)
					throws IOException, NumberFormatException {
		
		final Set<Integer> axioms = new HashSet<Integer>();
		
		final BufferedReader axiomReader =
				new BufferedReader(new InputStreamReader(assumptions));
		readAxioms(axiomReader, axioms);
//		String line;
//		while ((line = axiomReader.readLine()) != null) {
//			if (!line.isEmpty()) {
//				axioms.add(Integer.valueOf(line));
//			}
//		}
		
		final ListMultimap<Integer, JustifiedInference<Integer, Integer>> inferences =
				ArrayListMultimap.create();
		
		final BufferedReader cnfReader =
				new BufferedReader(new InputStreamReader(cnf));
		String line;
		while ((line = cnfReader.readLine()) != null) {
			
			if (line.isEmpty() || line.startsWith("c")
					|| line.startsWith("p")) {
				continue;
			}
			
			final String[] literals = line.split("\\s");
			final List<Integer> premises =
					new ArrayList<Integer>(literals.length - 2);
			final List<Integer> justifications =
					new ArrayList<Integer>(literals.length - 2);
			Integer conclusion = null;
			boolean terminated = false;
			for (int i = 0; i < literals.length; i++) {
				
				final int l = Integer.valueOf(literals[i]);
				if (l < 0) {
					final int premise = -l;
					if (axioms.contains(premise)) {
						justifications.add(premise);
					} else {
						premises.add(premise);
					}
				} else if (l > 0) {
					if (conclusion != null) {
						throw new IOException("Non-Horn clause! \"" + line + "\"");
					} else {
						conclusion = l;
					}
				} else {
					// l == 0
					if (i != literals.length - 1) {
						throw new IOException("Clause terminated before the end of line! \"" + line + "\"");
					} else {
						terminated = true;
					}
				}
				
			}
			if (conclusion == null) {
				throw new IOException("Clause has no positive literal! \"" + line + "\"");
			}
			if (!terminated) {
				throw new IOException("Clause not terminated at the end of line! \"" + line + "\"");
			}
			
			inferences.put(conclusion,
					new DirectSatEncodingInference(conclusion, premises,
							new HashSet<Integer>(justifications)));
		}
		
		return new DirectSatEncodingInferenceSetAdapter(inferences);
	}
	
	private static void readAxioms(final BufferedReader axiomReader,
			final Set<Integer> axioms) throws IOException {
		
		final StringBuilder number = new StringBuilder();
		
		boolean readingNumber = false;
		
		int ch;
		while((ch = axiomReader.read()) >= 0) {
			
			final int digit = Character.digit(ch, 10);
			if (digit < 0) {
				if (readingNumber) {
					// The number ended.
					final Integer n = Integer.valueOf(number.toString());
					if (n > 0) {
						axioms.add(n);
					}
					readingNumber = false;
				} else {
					// Still not reading any number.
				}
			} else {
				if (readingNumber) {
					// Have the next digit of a number.
					number.append(digit);
				} else {
					// The number started.
					number.setLength(0);
					number.append(digit);
					readingNumber = true;
				}
			}
			
		}
		
	}
	
	private final Multimap<Integer, JustifiedInference<Integer, Integer>> inferences_;
	
	private DirectSatEncodingInferenceSetAdapter(
			final Multimap<Integer, JustifiedInference<Integer, Integer>> inferences) {
		this.inferences_ = inferences;
	}

	@Override
	public Collection<JustifiedInference<Integer, Integer>> getInferences(
			final Integer conclusion) {
		return inferences_.get(conclusion);
	}

}
