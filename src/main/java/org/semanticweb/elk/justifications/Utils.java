package org.semanticweb.elk.justifications;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;

import com.google.common.base.Function;

public final class Utils {

	private Utils() {
		// Empty.
	}
	
	public static boolean cleanDir(final File dir) {
		boolean success = true;
		if (dir.exists()) {
			success = recursiveDelete(dir) && success;
		}
		return dir.mkdirs() && success;
	}
	
	public static boolean recursiveDelete(final File file) {
		boolean success = true;
		if (file.isDirectory()) {
			for (final File f : file.listFiles()) {
				success = recursiveDelete(f) && success;
			}
		}
		return file.delete() && success;
	}
	
	public static String toFileName(final Object obj) {
		return obj.toString().replaceAll("[^a-zA-Z0-9_.-]", "_");
	}
	
	public static <C, A, IO, CO, AO> void traverseProofs(final C expression,
			final InferenceSet<C, A> inferenceSet,
			final Function<Inference<C, A>, IO> perInference,
			final Function<C, CO> perConclusion,
			final Function<A, AO> perAxiom)
			throws ProofGenerationException {
		
		final LinkedList<C> toDo = new LinkedList<C>();
		final Set<C> done = new HashSet<C>();
		
		toDo.add(expression);
		
		for (;;) {
			final C next = toDo.poll();
			
			if (next == null) {
				break;
			}
			
			if (done.add(next)) {
				perConclusion.apply(next);
				
				for (final Inference<C, A> inf
						: inferenceSet.getInferences(next)) {
					perInference.apply(inf);
					
					for (final A axiom : inf.getJustification()) {
						perAxiom.apply(axiom);
					}
					
					for (final C premise : inf.getPremises()) {
						toDo.addFirst(premise);
					}
				}
			}
			
		}
	}
	
}
