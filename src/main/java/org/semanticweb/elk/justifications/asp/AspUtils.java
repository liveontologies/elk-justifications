package org.semanticweb.elk.justifications.asp;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.semanticweb.elk.proofs.Inference;

public class AspUtils {
	
	private AspUtils() {
		// Forbid instantiation.
	}
	
	public static <C, A> String getInfLiteral( final Inference<C, A> inf,
			final Index<Inference<C, A>> infIndex) {
		final int i = infIndex.get(inf);
		return String.format("inf(i%d,\"%s\")", i, inf);
	}
	
	public static <C> String getConclLiteral(final C concl,
			final Index<C> conclIndex) {
		final int c = conclIndex.get(concl);
		return String.format("concl(c%d,\"%s\")", c, concl);
	}
	
	public static <A> String getAxiomLiteral(final A axiom,
			final Index<A> axiomIndex) {
		final int a = axiomIndex.get(axiom);
		return String.format("axiom(a%d,\"%s\")", a, axiom);
	}
	
	public static <C> String getConclDerivedLiteral(final C concl,
			final Index<C> conclIndex) {
		final int c = conclIndex.get(concl);
		return String.format("conclDerived(c%d)", c);
	}
	
	/**
	 * <pre>
	 * rule_type head body_size                               neg_size            neg          pos
	 * 1         head positiveBody.size()+negativeBody.size() negativeBody.size() negativeBody positiveBody
	 * </pre>
	 * 
	 * @param output
	 * @param head
	 * @param positiveBody
	 * @param negativeBody
	 */
	public static void writeNormalClaspRule(final PrintWriter output,
			final int head, final Collection<Integer> positiveBody,
			final Collection<Integer> negativeBody) {
		output.print(1);
		output.print(' ');
		output.print(head);
		output.print(' ');
		output.print(positiveBody.size() + negativeBody.size());
		output.print(' ');
		output.print(negativeBody.size());
		for (final Integer nl : negativeBody) {
			output.print(' ');
			output.print(nl);
		}
		for (final Integer pl : positiveBody) {
			output.print(' ');
			output.print(pl);
		}
		output.println();
	}
	
	public static void writeNormalClaspRule(final PrintWriter output,
			final int head, final Collection<Integer> positiveBody) {
		writeNormalClaspRule(output, head, positiveBody,
				Collections.<Integer>emptyList());
	}
	
	public static void writeNormalClaspRule(final PrintWriter output,
			final int head, final Collection<Integer> positiveBody,
			final Integer... negativeBody) {
		writeNormalClaspRule(output, head, positiveBody,
				Arrays.asList(negativeBody));
	}
	
	public static void writeNormalClaspRule(final PrintWriter output,
			final int head, final Integer... positiveBody) {
		writeNormalClaspRule(output, head, Arrays.asList(positiveBody));
	}
	
	/**
	 * <pre>
	 * rule_type head body_size                               neg_size            neg          pos
	 * 1         1    positiveBody.size()+negativeBody.size() negativeBody.size() negativeBody positiveBody
	 * </pre>
	 * 
	 * @param output
	 * @param positiveBody
	 * @param negativeBody
	 */
	public static void writeClaspConstraint(final PrintWriter output,
			final Collection<Integer> positiveBody,
			final Collection<Integer> negativeBody) {
		writeNormalClaspRule(output, 1, positiveBody, negativeBody);
	}
	
	public static void writeClaspConstraint(final PrintWriter output,
			final Collection<Integer> positiveBody) {
		writeClaspConstraint(output, positiveBody,
				Collections.<Integer>emptyList());
	}
	
	public static void writeClaspConstraint(final PrintWriter output,
			final Collection<Integer> positiveBody,
			final Integer... negativeBody) {
		writeClaspConstraint(output, positiveBody, Arrays.asList(negativeBody));
	}
	
	public static void writeClaspConstraint(final PrintWriter output,
			final Integer... positiveBody) {
		writeClaspConstraint(output, Arrays.asList(positiveBody));
	}
	
	/**
	 * <pre>
	 * rule_type head_size   head body_size                               neg_size            neg          pos
	 * 8         head.size() head positiveBody.size()+negativeBody.size() negativeBody.size() negativeBody positiveBody
	 * </pre>
	 * 
	 * @param output
	 * @param head
	 * @param positiveBody
	 * @param negativeBody
	 */
	public static void writeDisjunctiveClaspRule(final PrintWriter output,
			final Collection<Integer> head,
			final Collection<Integer> positiveBody,
			final Collection<Integer> negativeBody) {
		output.print(8);
		output.print(' ');
		output.print(head.size());
		for (final Integer h : head) {
			output.print(' ');
			output.print(h);
		}
		output.print(' ');
		output.print(positiveBody.size() + negativeBody.size());
		output.print(' ');
		output.print(negativeBody.size());
		for (final Integer nl : negativeBody) {
			output.print(' ');
			output.print(nl);
		}
		for (final Integer pl : positiveBody) {
			output.print(' ');
			output.print(pl);
		}
		output.println();
	}
	
	public static void writeDisjunctiveClaspRule(final PrintWriter output,
			final Collection<Integer> head,
			final Collection<Integer> positiveBody) {
		writeDisjunctiveClaspRule(output, head, positiveBody,
				Collections.<Integer>emptyList());
	}
	
	public static void writeDisjunctiveClaspRule(final PrintWriter output,
			final Collection<Integer> head,
			final Collection<Integer> positiveBody,
			final Integer... negativeBody) {
		writeDisjunctiveClaspRule(output, head, positiveBody,
				Arrays.asList(negativeBody));
	}
	
	public static void writeDisjunctiveClaspRule(final PrintWriter output,
			final Collection<Integer> head, final Integer... positiveBody) {
		writeDisjunctiveClaspRule(output, head, Arrays.asList(positiveBody));
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final Iterator<String> head,
			final Iterator<String> positiveBody,
			final Iterator<String> negativeBody) {
		
		// head
		if (head.hasNext()) {
			String lit = head.next();
			output.print(lit);
			output.print(' ');
			while (head.hasNext()) {
				output.print("| ");
				lit = head.next();
				output.print(lit);
				output.print(' ');
			}
		}
		
		// body
		if (positiveBody.hasNext() || negativeBody.hasNext()) {
			
			output.print(":-");
			
			if (positiveBody.hasNext()) {
				String lit = positiveBody.next();
				output.print(' ');
				output.print(lit);
				while (positiveBody.hasNext()) {
					output.print(", ");
					lit = positiveBody.next();
					output.print(lit);
				}
				if (negativeBody.hasNext()) {
					output.print(',');
				}
			}
			
			if (negativeBody.hasNext()) {
				String lit = negativeBody.next();
				output.print(" not ");
				output.print(lit);
				while (negativeBody.hasNext()) {
					output.print(", not ");
					lit = negativeBody.next();
					output.print(lit);
				}
			}
			
		}
		
		output.print('.');
		output.println();
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final Iterable<String> head,
			final Iterable<String> positiveBody,
			final Iterable<String> negativeBody) {
		writeGringoRule(output, head.iterator(), positiveBody.iterator(),
				negativeBody.iterator());
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final Iterator<String> head,
			final Iterator<String> positiveBody) {
		writeGringoRule(output, head, positiveBody,
				Collections.<String>emptyIterator());
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final Iterator<String> head,
			final String... positiveBody) {
		writeGringoRule(output, head, Arrays.asList(positiveBody).iterator());
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final String head,
			final Iterator<String> positiveBody) {
		writeGringoRule(output, Collections.singleton(head).iterator(),
				positiveBody);
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final Iterable<String> head,
			final Iterable<String> positiveBody) {
		writeGringoRule(output, head, positiveBody,
				Collections.<String>emptyList());
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final String head,
			final Iterable<String> positiveBody) {
		writeGringoRule(output, Collections.singleton(head), positiveBody);
	}
	
	public static void writeGringoRule(final PrintWriter output,
			final String head,
			final String... positiveBody) {
		writeGringoRule(output, head, Arrays.asList(positiveBody));
	}
	
	public static void writeGringoFact(final PrintWriter output,
			final Iterator<String> head) {
		writeGringoRule(output, head, Collections.<String>emptyIterator());
	}
	
	public static void writeGringoFact(final PrintWriter output,
			final Iterable<String> head) {
		writeGringoRule(output, head, Collections.<String>emptyList());
	}
	
	public static void writeGringoFact(final PrintWriter output,
			final String... head) {
		writeGringoFact(output, Arrays.asList(head));
	}
	
	public static void writeGringoConstraint(final PrintWriter output,
			final Iterator<String> positiveBody,
			final Iterator<String> negativeBody) {
		writeGringoRule(output, Collections.<String>emptyIterator(),
				positiveBody, negativeBody);
	}
	
	public static void writeGringoConstraint(final PrintWriter output,
			final Iterator<String> positiveBody,
			final String... negativeBody) {
		writeGringoRule(output, positiveBody,
				Arrays.asList(negativeBody).iterator());
	}
	
	public static void writeGringoConstraint(final PrintWriter output,
			final Iterable<String> positiveBody,
			final Iterable<String> negativeBody) {
		writeGringoRule(output, Collections.<String>emptyList(), positiveBody,
				negativeBody);
	}
	
	public static void writeGringoConstraint(final PrintWriter output,
			final Iterator<String> positiveBody) {
		writeGringoConstraint(output, positiveBody,
				Collections.<String>emptyIterator());
	}
	
	public static void writeGringoConstraint(final PrintWriter output,
			final Iterable<String> positiveBody) {
		writeGringoConstraint(output, positiveBody,
				Collections.<String>emptyList());
	}
	
}
