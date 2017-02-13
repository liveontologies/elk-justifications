package org.semanticweb.elk.justifications;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.liveontologies.puli.GenericInferenceSet;
import org.liveontologies.puli.JustifiedInference;
import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.exceptions.ElkRuntimeException;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.entailments.model.Entailment;
import org.semanticweb.elk.reasoner.entailments.model.EntailmentInference;
import org.semanticweb.elk.reasoner.entailments.model.EntailmentInferenceSet;
import org.semanticweb.elk.reasoner.entailments.model.HasReason;
import org.semanticweb.elk.reasoner.query.ElkQueryException;
import org.semanticweb.elk.reasoner.query.EntailmentQueryResult;
import org.semanticweb.elk.reasoner.query.ProperEntailmentQueryResult;
import org.semanticweb.elk.reasoner.query.UnsupportedIndexingEntailmentQueryResult;
import org.semanticweb.elk.reasoner.query.UnsupportedQueryTypeEntailmentQueryResult;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.semanticweb.elk.util.collections.ArrayHashSet;
import org.semanticweb.elk.util.concurrent.computation.InterruptMonitor;

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

	public static void closeQuietly(final Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (final IOException e) {
				// Ignore.
			}
		}
	}

	public static String dropExtension(final String fileName) {
		final int index = fileName.lastIndexOf('.');
		if (index < 0) {
			return fileName;
		} else {
			return fileName.substring(0, index);
		}
	}

	public static int digitCount(final int x) {
		return (int) Math.floor(Math.log10(x) + 1);
	}

	public static <C, A, IO, CO, AO> void traverseProofs(final C expression,
			final GenericInferenceSet<C, ? extends JustifiedInference<C, A>> inferenceSet,
			final Function<JustifiedInference<C, A>, IO> perInference,
			final Function<C, CO> perConclusion,
			final Function<A, AO> perAxiom) {

		final Queue<C> toDo = new LinkedList<C>();
		final Set<C> done = new HashSet<C>();

		toDo.add(expression);
		done.add(expression);

		for (;;) {
			final C next = toDo.poll();

			if (next == null) {
				break;
			}

			perConclusion.apply(next);

			for (final JustifiedInference<C, A> inf : inferenceSet
					.getInferences(next)) {
				perInference.apply(inf);

				for (final A axiom : inf.getJustification()) {
					perAxiom.apply(axiom);
				}

				for (final C premise : inf.getPremises()) {
					if (done.add(premise)) {
						toDo.add(premise);
					}
				}
			}

		}
	}

	/**
	 * Checks if the given justification has a subset in the given collection of
	 * justifications
	 * 
	 * @param just
	 * @param justs
	 * @return {@code true} if the given justification is not a superset of any
	 *         justification in the given collection
	 */
	public static <J extends Set<?>> boolean isMinimal(J just,
			Collection<? extends J> justs) {
		for (J other : justs) {
			if (just.containsAll(other)) {
				return false;
			}
		}
		// otherwise
		return true;
	}

	/**
	 * Merges a given justification into a given collection of justifications.
	 * The justification is added to the collection unless its subset is already
	 * contained in the collection. Furthermore, all proper supersets of the
	 * justification are removed from the collection.
	 * 
	 * @param just
	 * @param justs
	 * @return {@code true} if the collection is modified as a result of this
	 *         operation and {@code false} otherwise
	 */
	public static <J extends Set<?>> boolean merge(J just,
			Collection<J> justs) {
		int justSize = just.size();
		final Iterator<J> oldJustIter = justs.iterator();
		boolean isASubsetOfOld = false;
		while (oldJustIter.hasNext()) {
			final J oldJust = oldJustIter.next();
			if (justSize < oldJust.size()) {
				if (oldJust.containsAll(just)) {
					// new justification is smaller
					oldJustIter.remove();
					isASubsetOfOld = true;
				}
			} else if (!isASubsetOfOld & just.containsAll(oldJust)) {
				// is a superset of some old justification
				return false;
			}
		}
		// justification survived all tests, it is minimal
		justs.add(just);
		return true;
	}

	/**
	 * FIXME: The order of arguments matter !!! The conclusion is copied from
	 * the justifications in the first argument, but not from the second!
	 * 
	 * @param first
	 * @param second
	 * @return the list of all pairwise unions of the justifications in the
	 *         first and the second collections, minimized under set inclusion
	 */
	public static <C, T> List<Justification<C, T>> join(
			Collection<? extends Justification<C, T>> first,
			Collection<? extends Justification<C, T>> second) {
		if (first.isEmpty() || second.isEmpty()) {
			return Collections.emptyList();
		}
		List<Justification<C, T>> result = new ArrayList<Justification<C, T>>(
				first.size() * second.size());
		for (Justification<C, T> firstSet : first) {
			for (Justification<C, T> secondSet : second) {
				Justification<C, T> union = firstSet.addElements(secondSet);
				merge(union, result);
			}
		}
		return result;
	}

	/**
	 * FIXME: The order of arguments matter !!! The conclusion is copied from
	 * the justifications in the first argument, but not from the second!
	 * 
	 * @param first
	 * @param second
	 * @return the list of all pairwise unions of the justifications in the
	 *         first and the second collections, minimized under set inclusion
	 */
	public static <C, T> List<Justification<C, T>> joinCheckingSubsets(
			Collection<? extends Justification<C, T>> first,
			Collection<? extends Justification<C, T>> second) {
		if (first.isEmpty() || second.isEmpty()) {
			return Collections.emptyList();
		}

		List<Justification<C, T>> result = new ArrayList<Justification<C, T>>(
				first.size() * second.size());
		/*
		 * If some set from one argument is a superset of something in the other
		 * argument, it can be merged into the result without joining it with
		 * anything from the other argument.
		 */
		final C conclusion = first.iterator().next().getConclusion();
		final List<Justification<C, T>> minimalSecond = new ArrayList<Justification<C, T>>(
				second.size());
		for (final Justification<C, T> secondSet : second) {
			if (isMinimal(secondSet, first)) {
				minimalSecond.add(secondSet);
			} else {
				merge(secondSet.copyTo(conclusion), result);
			}
		}

		for (Justification<C, T> firstSet : first) {
			if (isMinimal(firstSet, minimalSecond)) {
				for (Justification<C, T> secondSet : minimalSecond) {
					Justification<C, T> union = firstSet.addElements(secondSet);
					merge(union, result);
				}
			} else {
				merge(firstSet, result);
			}
		}

		return result;
	}

	public static Conclusion getFirstDerivedConclusionForSubsumption(
			Reasoner reasoner, final ElkAxiom axiom) throws ElkException {
		final List<Conclusion> conclusions = new ArrayList<Conclusion>(1);

		final EntailmentQueryResult result = reasoner.isEntailed(axiom);
		result.accept(
				new EntailmentQueryResult.Visitor<Void, ElkQueryException>() {

					@Override
					public Void visit(final ProperEntailmentQueryResult result)
							throws ElkQueryException {
						try {
							collectReasons(result.getEntailment(),
									result.getEvidence(true), conclusions);
						} finally {
							result.unlock();
						}
						return null;
					}

					@Override
					public Void visit(
							final UnsupportedIndexingEntailmentQueryResult result) {
						throw new ElkRuntimeException(
								UnsupportedIndexingEntailmentQueryResult.class
										.getSimpleName());
					}

					@Override
					public Void visit(
							final UnsupportedQueryTypeEntailmentQueryResult result) {
						throw new ElkRuntimeException(
								UnsupportedQueryTypeEntailmentQueryResult.class
										.getSimpleName());
					}

				});

		return conclusions.get(0);
	}

	private static void collectReasons(final Entailment goal,
			final EntailmentInferenceSet evidence,
			final Collection<Conclusion> result) {

		final Set<Entailment> done = new ArrayHashSet<Entailment>();
		final Queue<Entailment> toDo = new LinkedList<Entailment>();

		if (done.add(goal)) {
			toDo.offer(goal);
		}

		Entailment entailment;
		while ((entailment = toDo.poll()) != null) {
			for (final EntailmentInference inf : evidence
					.getInferences(entailment)) {

				if (inf instanceof HasReason) {
					final Object reason = ((HasReason<?>) inf).getReason();
					if (reason instanceof Conclusion) {
						result.add((Conclusion) reason);
					}
				}

				for (final Entailment premise : inf.getPremises()) {
					if (done.add(premise)) {
						toDo.offer(premise);
					}
				}

			}
		}

	}

	public static InterruptMonitor getDummyInterruptMonitor() {
		return new InterruptMonitor() {
			
			@Override
			public boolean isInterrupted() {
				return false;
			}
		};
		
	}

}
