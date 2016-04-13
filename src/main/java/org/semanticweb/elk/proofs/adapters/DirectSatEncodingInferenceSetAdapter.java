package org.semanticweb.elk.proofs.adapters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

public class DirectSatEncodingInferenceSetAdapter
		implements InferenceSet<Integer, Integer> {

	public static DirectSatEncodingInferenceSetAdapter load(
			final InputStream assumptions, final InputStream cnf)
					throws IOException, NumberFormatException {
		
		final Set<Integer> axioms = new HashSet<Integer>();
		
		final BufferedReader axiomReader =
				new BufferedReader(new InputStreamReader(assumptions));
		String line;
		while ((line = axiomReader.readLine()) != null) {
			if (!line.isEmpty()) {
				axioms.add(Integer.valueOf(line));
			}
		}
		
		final ListMultimap<Integer, Inference<Integer, Integer>> inferences =
				ArrayListMultimap.create();
		
		final BufferedReader cnfReader =
				new BufferedReader(new InputStreamReader(cnf));
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
					if (axioms.contains(premises)) {
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
	
	private final Multimap<Integer, Inference<Integer, Integer>> inferences_;
	
	private DirectSatEncodingInferenceSetAdapter(
			final Multimap<Integer, Inference<Integer, Integer>> inferences) {
		this.inferences_ = inferences;
	}

	@Override
	public Iterable<Inference<Integer, Integer>> getInferences(
			final Integer conclusion) {
		return inferences_.get(conclusion);
	}

}
