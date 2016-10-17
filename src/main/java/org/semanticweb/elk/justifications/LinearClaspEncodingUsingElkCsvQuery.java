package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.semanticweb.elk.exceptions.ElkException;
import org.semanticweb.elk.loading.AxiomLoader;
import org.semanticweb.elk.loading.Owl2StreamLoader;
import org.semanticweb.elk.owl.implementation.ElkObjectBaseFactory;
import org.semanticweb.elk.owl.interfaces.ElkAxiom;
import org.semanticweb.elk.owl.interfaces.ElkSubClassOfAxiom;
import org.semanticweb.elk.owl.iris.ElkFullIri;
import org.semanticweb.elk.owl.parsing.javacc.Owl2FunctionalStyleParserFactory;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;
import org.semanticweb.elk.proofs.adapters.TracingInferenceSetInferenceSetAdapter;
import org.semanticweb.elk.reasoner.ElkInconsistentOntologyException;
import org.semanticweb.elk.reasoner.Reasoner;
import org.semanticweb.elk.reasoner.ReasonerFactory;
import org.semanticweb.elk.reasoner.saturation.conclusions.model.ClassConclusion;
import org.semanticweb.elk.reasoner.stages.RestartingStageExecutor;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class LinearClaspEncodingUsingElkCsvQuery {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(LinearClaspEncodingUsingElkCsvQuery.class);

	public static void main(final String[] args) {
		
		if (args.length < 3) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final String ontologyFileName = args[0];
		final String conclusionsFileName = args[1];
		final File outputDirectory = new File(args[2]);
		if (!Utils.cleanDir(outputDirectory)) {
			LOG.error("Could not prepare the output directory!");
			System.exit(2);
		}
		
		final ElkObjectBaseFactory factory = new ElkObjectBaseFactory();
		
		InputStream ontologyIS = null;
		BufferedReader conclusionReader = null;
		
		try {
			
			ontologyIS = new FileInputStream(ontologyFileName);
			
			final AxiomLoader ontologyLoader = new Owl2StreamLoader(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory().createReasoner(
					ontologyLoader, new RestartingStageExecutor());
			
			LOG.info("Classifying ...");
			long start = System.currentTimeMillis();
			reasoner.getTaxonomy();
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			
			int conclCount = 0;
			String line;
			while ((line = conclusionReader.readLine()) != null) {
				conclCount++;
			}
			conclusionReader.close();
			
			conclusionReader =
					new BufferedReader(new FileReader(conclusionsFileName));
			
			int conclIndex = 0;
			while ((line = conclusionReader.readLine()) != null) {
				
				final String[] columns = line.split(",");
				if (columns.length < 2) {
					return;
				}
				
				final String subIri = strip(columns[0]);
				final String supIri = strip(columns[1]);
				
				final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
						factory.getClass(new ElkFullIri(subIri)),
						factory.getClass(new ElkFullIri(supIri)));
				
				LOG.info("Encoding {} {}", conclIndex, conclusion);
				start = System.currentTimeMillis();
				encode(conclusion, reasoner, outputDirectory, conclCount,
						conclIndex++);
				LOG.info("... took {}s",
						(System.currentTimeMillis() - start)/1000.0);
				
			}
			
		} catch (final FileNotFoundException e) {
			LOG.error("File Not Found!", e);
			System.exit(2);
		} catch (final ElkInconsistentOntologyException e) {
			LOG.error("The ontology is inconsistent!", e);
			System.exit(2);
		} catch (final ElkException e) {
			LOG.error("Could not classify the ontology!", e);
			System.exit(2);
		} catch (final IOException e) {
			LOG.error("I/O error!", e);
			System.exit(2);
		} finally {
			if (ontologyIS != null) {
				try {
					ontologyIS.close();
				} catch (final IOException e) {}
			}
			if (conclusionReader != null) {
				try {
					conclusionReader.close();
				} catch (final IOException e) {}
			}
		}
		
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
	
	private static void encode(final ElkSubClassOfAxiom elkGoalConclusion,
			final Reasoner reasoner, final File outputDirectory,
			final int conclCount, final int conclusionIndex)
					throws ElkException, IOException {
		
//		final String conclName = Utils.toFileName(conclusion);
		final String conclName = String.format(
				"%0" + Integer.toString(conclCount).length() + "d", conclusionIndex);
		final File outDir = new File(outputDirectory, conclName);
		outDir.mkdirs();
		final File outFile = new File(outDir, "encoding.clasp");
		final File dotFile = new File(outDir, "inferenceGraph.dot");
		
		PrintWriter outWriter = null;
		PrintWriter dotWriter = null;
		
		try {
			
			outWriter = new PrintWriter(outFile);
			final PrintWriter out = outWriter;
			
			dotWriter = new PrintWriter(dotFile);
			final PrintWriter dot = dotWriter;
			
//			dot.println("digraph {");
			
			final ClassConclusion goalConclusion =
					reasoner.getConclusion(elkGoalConclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner.explainConclusion(goalConclusion));
			
			final Index<Conclusion> conclIndex = new Index<>();
			final Index<ElkAxiom> axiomIndex = new Index<>();
			final Index<Inference<Conclusion, ElkAxiom>> infIndex = new Index<>();
			final Index<String> literalIndex = new Index<>(2);// Gringo starts indexing from 2 !!!
			
			/* @formatter:off
			 * 
			 * Encoding:
			 * Inference implies all its premises.
			 * Conclusion implies at least one of its inferences.
			 * Each conclusion and inference has depth at which it is reached
			 * when traversing the proof from the goal conclusion.
			 * Depth increases only when traversing from inference to premise
			 * that is used as premise in multiple inferences
			 * or is the goal conclusion.
			 * Depth must not increase over the maximal depth,
			 * which is the number of premises used in multiple inferences.
			 * 
			 * inf(I,L)
			 * Inference I is used in the proof of justification
			 * and its label is L.
			 * 
			 * concl(C,L)
			 * Conclusion C is used in the proof of justification
			 * and its label is L.
			 * 
			 * axiom(A,L)
			 * Axiom A is used in the proof of justification
			 * and its label is L.
			 * 
			 * conclDerived(C)
			 * Conclusion C is derived from the justification.
			 * 
			 * % Generate justification proof.
			 * 
			 * concl(goalConclusion,_).
			 * where goalConclusion is the goal conclusion.
			 * 
			 * concl(P,_) :- inf(I,_).
			 * where P is a premise of I.
			 * 
			 * axiom(A,_) :- inf(I,_).
			 * where A is in justification of I.
			 * 
			 * inf(I1,_) | ... | inf(In,_) :- concl(C,_).
			 * where I1, ..., In are all inferences that derive C.
			 * 
			 * % Saturation to use ASP minimality
			 * 
			 * concl(C,_) :- inf(I,_).
			 * where C is the conclusion of I.
			 * 
			 * inf(I,_) :- concl(P1,_), ..., concl(Pm,_).
			 * where P1, ..., Pm are all premises of I.
			 * FIXME: I must have empty justification !!!
			 * 
			 * % Check that the goal conclusion is really derived.
			 * 
			 * :- not conclDerived(goalConclusion).
			 * where goalConclusion is the goal conclusion.
			 * 
			 * conclDerived(C) :- conclDerived(P1), ..., conclDerived(Pm),
			 * 		axiom(A1), ..., axiom(An).
			 * where C is the conclusion of an inference with premises P1, ...,
			 * Pm and justification A1, ..., An.
			 * 
			 * @formatter:on
			 */
			
			// Assert the goal conclusion
			
			final String cLit = getConclLiteral(goalConclusion, conclIndex, literalIndex);
			final int cl = literalIndex.get(cLit);
			final String cdLit = getConclDerivedLiteral(goalConclusion, conclIndex, literalIndex);
			final int cdl = literalIndex.get(cdLit);
			/* 
			 * write "cl."
			 * 
			 * rule_type head body_size neg_size neg pos
			 * 1         cl   0         0
			 */
			out.print(1);
			out.print(' ');
			out.print(cl);
			out.print(' ');
			out.print(0);
			out.print(' ');
			out.print(0);
			out.println();
//			out.print(cLit);
//			out.print('.');
//			out.println();
			/* 
			 * write ":- not cdl."
			 * 
			 * rule_type head body_size neg_size neg pos
			 * 1         1    1         1        cdl
			 */
			out.print(1);
			out.print(' ');
			out.print(1);
			out.print(' ');
			out.print(1);
			out.print(' ');
			out.print(1);
			out.print(' ');
			out.print(cdl);
			out.println();
//			out.print(":- not ");
//			out.print(cdLit);
//			out.print('.');
//			out.println();
			
			// Everything else
			
			Utils.traverseProofs(goalConclusion, inferenceSet,
					new Function<Inference<Conclusion, ElkAxiom>, Void>(){
						@Override
						public Void apply(
								final Inference<Conclusion, ElkAxiom> inf) {
							
							final Conclusion conclusion = inf.getConclusion();
							
							final int i = infIndex.get(inf);
							final String iLit = getInfLiteral(inf, infIndex, literalIndex);
							final int il = literalIndex.get(iLit);
							
							for (final Conclusion premise : inf.getPremises()) {
								
								final int p = conclIndex.get(premise);
								final String pLit = getConclLiteral(premise, conclIndex, literalIndex);
								final int pl = literalIndex.get(pLit);
								
								/* 
								 * write "pl :- il."
								 * 
								 * rule_type head body_size neg_size neg pos
								 * 1         pl   1         0            il
								 */
								out.print(1);
								out.print(' ');
								out.print(pl);
								out.print(' ');
								out.print(1);
								out.print(' ');
								out.print(0);
								out.print(' ');
								out.print(il);
								out.println();
//								out.print(pLit);
//								out.print(" :- ");
//								out.print(iLit);
//								out.print('.');
//								out.println();
								
								/* Write dot.
								 * 
								 * write: "i$(i) -> c$(p);"
								 */
								dot.print('i');
								dot.print(i);
								dot.print(" -> ");
								dot.print('c');
								dot.print(p);
								dot.println(";");
								
							}
							
							for (final ElkAxiom axiom : inf.getJustification()) {
								
								final int a = axiomIndex.get(axiom);
								final String aLit = getAxiomLiteral(axiom, axiomIndex, literalIndex);
								final int al = literalIndex.get(aLit);
								
								/* 
								 * write "al :- il."
								 * 
								 * rule_type head body_size neg_size neg pos
								 * 1         al   1         0            il
								 */
								out.print(1);
								out.print(' ');
								out.print(al);
								out.print(' ');
								out.print(1);
								out.print(' ');
								out.print(0);
								out.print(' ');
								out.print(il);
								out.println();
//								out.print(aLit);
//								out.print(" :- ");
//								out.print(iLit);
//								out.print('.');
//								out.println();
								
								/* Write dot.
								 * 
								 * write: "i$(i) -> a$(a);"
								 */
								dot.print('i');
								dot.print(i);
								dot.print(" -> ");
								dot.print('a');
								dot.print(a);
								dot.println(";");
								
							}
							
							final String cLit = getConclLiteral(conclusion, conclIndex, literalIndex);
							final int cl = literalIndex.get(cLit);
							/* 
							 * write "cl :- il."
							 * 
							 * rule_type head body_size neg_size neg pos
							 * 1         cl   1         0            il
							 */
							out.print(1);
							out.print(' ');
							out.print(cl);
							out.print(' ');
							out.print(1);
							out.print(' ');
							out.print(0);
							out.print(' ');
							out.print(il);
							out.println();
//							out.print(cLit);
//							out.print(" :- ");
//							out.print(iLit);
//							out.print('.');
//							out.println();
							
							/* 
							 * write "il :- pl1, ..., plm, a1, ..., an."
							 * 
							 * rule_type head body_size neg_size neg pos
							 * 1         il   m+n       0            pl1 ... plm a1 ... an
							 */
							out.print(1);
							out.print(' ');
							out.print(il);
							out.print(' ');
							out.print(inf.getPremises().size() + inf.getJustification().size());
							out.print(' ');
							out.print(0);
							for (final Conclusion premise : inf.getPremises()) {
								
								final String pLit = getConclLiteral(premise, conclIndex, literalIndex);
								final int pl = literalIndex.get(pLit);
								
								out.print(' ');
								out.print(pl);
								
							}
							for (final ElkAxiom axiom : inf.getJustification()) {
								
								final String aLit = getAxiomLiteral(axiom, axiomIndex, literalIndex);
								final int al = literalIndex.get(aLit);
								
								out.print(' ');
								out.print(al);
								
							}
							out.println();
//							out.print(iLit);
//							if (!inf.getPremises().isEmpty() || !inf.getJustification().isEmpty()) {
//								out.print(" :- ");
//								boolean firstWrite = true;
//								for (final Conclusion premise : inf.getPremises()) {
//									if (firstWrite) {
//										firstWrite = false;
//									} else {
//										out.print(", ");
//									}
//									final String pLit = getConclLiteral(premise, conclIndex, literalIndex);
//									out.print(pLit);
//								}
//								for (final ElkAxiom axiom : inf.getJustification()) {
//									if (firstWrite) {
//										firstWrite = false;
//									} else {
//										out.print(", ");
//									}
//									final String aLit = getAxiomLiteral(axiom, axiomIndex, literalIndex);
//									out.print(aLit);
//								}
//							}
//							out.print('.');
//							out.println();
							
							/* Write dot.
							 * 
							 * write: "i$(i) [shape=square];"
							 */
							dot.print('i');
							dot.print(i);
							dot.println(" [shape=square];");
							
							final String cdLit = getConclDerivedLiteral(conclusion, conclIndex, literalIndex);
							final int cdl = literalIndex.get(cdLit);
							
							/* 
							 * write "cdl :- pdl1, ..., pdlm, a1, ..., an."
							 * 
							 * rule_type head body_size neg_size neg pos
							 * 1         cdl  m+n       0            pdl1 ... pdlm a1 ... an
							 */
							out.print(1);
							out.print(' ');
							out.print(cdl);
							out.print(' ');
							out.print(inf.getPremises().size() + inf.getJustification().size());
							out.print(' ');
							out.print(0);
							for (final Conclusion premise : inf.getPremises()) {
								
								final String pdLit = getConclDerivedLiteral(premise, conclIndex, literalIndex);
								final int pdl = literalIndex.get(pdLit);
								
								out.print(' ');
								out.print(pdl);
								
							}
							for (final ElkAxiom axiom : inf.getJustification()) {
								
								final String aLit = getAxiomLiteral(axiom, axiomIndex, literalIndex);
								final int al = literalIndex.get(aLit);
								
								out.print(' ');
								out.print(al);
								
							}
							out.println();
//							out.print(cdLit);
//							if (!inf.getPremises().isEmpty() || !inf.getJustification().isEmpty()) {
//								out.print(" :- ");
//								boolean firstWrite = true;
//								for (final Conclusion premise : inf.getPremises()) {
//									if (firstWrite) {
//										firstWrite = false;
//									} else {
//										out.print(", ");
//									}
//									final String pdLit = getConclDerivedLiteral(premise, conclIndex, literalIndex);
//									out.print(pdLit);
//								}
//								for (final ElkAxiom axiom : inf.getJustification()) {
//									if (firstWrite) {
//										firstWrite = false;
//									} else {
//										out.print(", ");
//									}
//									final String aLit = getAxiomLiteral(axiom, axiomIndex, literalIndex);
//									out.print(aLit);
//								}
//							}
//							out.print('.');
//							out.println();
							
							return null;
						}
					},
					new Function<Conclusion, Void>(){
						@Override
						public Void apply(final Conclusion conclusion) {
							
							final Iterable<Inference<Conclusion, ElkAxiom>> infs =
									inferenceSet.getInferences(conclusion);
							int nInfs = 0;
							for (@SuppressWarnings("unused")
									final Inference<Conclusion, ElkAxiom> inf
									: infs) {
								nInfs++;
							}
							
							final int c = conclIndex.get(conclusion);
							final String cLit = getConclLiteral(conclusion, conclIndex, literalIndex);
							final int cl = literalIndex.get(cLit);
							
							/* 
							 * write "il1 | il2 | ... | iln :- cl."
							 * 
							 * rule_type head_size head            body_size neg_size neg pos
							 * 8         nInfs     il1 il2 ... iln 1         0            cl
							 */
							out.print(8);
							out.print(' ');
							out.print(nInfs);
							for (final Inference<Conclusion, ElkAxiom> inf
									: infs) {
								
								final String iLit = getInfLiteral(inf, infIndex, literalIndex);
								final int il = literalIndex.get(iLit);
								
								out.print(' ');
								out.print(il);
								
							}
							out.print(' ');
							out.print(1);
							out.print(' ');
							out.print(0);
							out.print(' ');
							out.print(cl);
							out.println();
//							final Iterator<Inference<Conclusion, ElkAxiom>> iter = infs.iterator();
//							Inference<Conclusion, ElkAxiom> infe = iter.next();
//							String iLit = getInfLiteral(infe, infIndex, literalIndex);
//							out.print(iLit);
//							while (iter.hasNext()) {
//								out.print(" | ");
//								infe = iter.next();
//								iLit = getInfLiteral(infe, infIndex, literalIndex);
//								out.print(iLit);
//							}
//							out.print(" :- ");
//							out.print(cLit);
//							out.print('.');
//							out.println();
							
							
							// Write dot.
							
							for (final Inference<Conclusion, ElkAxiom> inf
									: infs) {
								
								final int i = infIndex.get(inf);
								
								/* 
								 * write: "c$(c) -> i$(i);"
								 */
								dot.print('c');
								dot.print(c);
								dot.print(" -> ");
								dot.print('i');
								dot.print(i);
								dot.println(";");
								
							}
							
							return null;
						}
					},
					Functions.<ElkAxiom>identity()
			);
			
//			dot.println("}");
			
			LOG.debug("number of literals: {}", literalIndex.getIndex().size());
			
			// delimiter
			out.println(0);
			
			// Write literal index
			
			for (final Entry<String, Integer> e : literalIndex.getIndex().entrySet()) {
//				if (e.getKey().startsWith("axiom")) {
					out.print(e.getValue());
					out.print(' ');
					out.print(e.getKey());
					out.println();
//				}
			}
			
			// delimiter
			out.println(0);
			
			// Write the magic at the bottom :-P
			out.println("B+");
			out.println(0);
			out.println("B-");
			out.println(1);
			out.println(0);
			out.println(1);
			
		} finally {
			if (outWriter != null) {
				outWriter.close();
			}
			if (dotWriter != null) {
				dotWriter.close();
			}
		}
		
	}
	
	private static class Counter {
		private int counter;
		public Counter() {
			this(0);
		}
		public Counter(final int first) {
			this.counter = first;
		}
		public int next() {
			return counter++;
		}
	}
	
	private static class Index<T> {
		
		private final Counter counter;
		
		private final Map<T, Integer> index = new HashMap<>();
		
		public Index(final int firstIndex) {
			this.counter = new Counter(firstIndex);
		}
		
		public Index() {
			this(0);
		}
		
		public int get(final T arg) {
			Integer result = index.get(arg);
			if (result == null) {
				result = counter.next();
				index.put(arg, result);
			}
			return result;
		}
		
		public Map<T, Integer> getIndex() {
			return Collections.unmodifiableMap(index);
		}
		
	}
	
	private static String getInfLiteral(final Inference<Conclusion, ElkAxiom> inf,
			final Index<Inference<Conclusion, ElkAxiom>> infIndex,
			final Index<String> literalIndex) {
		final int i = infIndex.get(inf);
		return String.format("inf(%d,\"%s\")", i, inf);
//		return String.format("inf(%d)", i);
	}
	
	private static String getConclLiteral(final Conclusion concl,
			final Index<Conclusion> conclIndex,
			final Index<String> literalIndex) {
		final int c = conclIndex.get(concl);
		return String.format("concl(%d,\"%s\")", c, concl);
//		return String.format("concl(%d)", c);
	}
	
	private static String getAxiomLiteral(final ElkAxiom axiom,
			final Index<ElkAxiom> axiomIndex,
			final Index<String> literalIndex) {
		final int a = axiomIndex.get(axiom);
		return String.format("axiom(%d,\"%s\")", a, axiom);
//		return String.format("axiom(%d)", a);
	}
	
	private static String getConclDerivedLiteral(final Conclusion concl,
			final Index<Conclusion> conclIndex,
			final Index<String> literalIndex) {
		final int c = conclIndex.get(concl);
		return String.format("conclDerived(%d)", c);
	}
	
}
