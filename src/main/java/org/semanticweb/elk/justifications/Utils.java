package org.semanticweb.elk.justifications;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.semanticweb.owlapitools.proofs.OWLInference;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpressionVisitor;

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
	
	public static <IO, EO> void traverseProofs(final OWLExpression expression,
			final boolean justOne,
			final Function<OWLInference, IO> perInference,
			final OWLExpressionVisitor<EO> perExpression)
			throws ProofGenerationException {
		
		final LinkedList<OWLExpression> toDo = new LinkedList<OWLExpression>();
		final Set<OWLExpression> done = new HashSet<OWLExpression>();
		
		toDo.add(expression);
		
		for (;;) {
			final OWLExpression next = toDo.poll();
			
			if (next == null) {
				break;
			}
			
			if (done.add(next)) {
				next.accept(perExpression);
				
				for (final OWLInference inf : next.getInferences()) {
					perInference.apply(inf);
					
					for (final OWLExpression premise : inf.getPremises()) {
						toDo.addFirst(premise);
					}
					
					if (justOne) {
						// if only interested in one inference per derived expression (that is sufficient to reconstruct one proof)
						break;
					}
				}
			}
			
		}
	}
	
}
