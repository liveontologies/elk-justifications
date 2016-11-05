package org.semanticweb.elk.justifications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;

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
import org.semanticweb.elk.reasoner.stages.SimpleStageExecutor;
import org.semanticweb.elk.reasoner.tracing.Conclusion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class ClaspEncodingUsingElkCsvQuery {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(ClaspEncodingUsingElkCsvQuery.class);

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
			
			final AxiomLoader.Factory loader = new Owl2StreamLoader.Factory(
					new Owl2FunctionalStyleParserFactory(), ontologyIS);
			final Reasoner reasoner = new ReasonerFactory().createReasoner(
					loader, new SimpleStageExecutor());
			
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
			
			final ClassConclusion goalConclusion = Utils
					.getFirstDerivedConclusionForSubsumption(reasoner,
							elkGoalConclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner.explainConclusion(goalConclusion));
			
//			final int maximalDepth = computeMaximalDepth(expression,
//					inferenceSet);
			final Set<Conclusion> deepeningPremises =
					computeDeepeningPremises(goalConclusion, inferenceSet);
			final int maximalDepth = deepeningPremises.size();
			
			LOG.debug("maximalDepth: {}", maximalDepth);
			
			final Map<Conclusion, Integer> minDepths = new HashMap<>();
			final Map<Conclusion, Integer> maxDepths = new HashMap<>();
			// TODO: actually I can simply remember all the depths in sets ?!?
			assignDepths(goalConclusion, 0, maximalDepth, deepeningPremises,
					inferenceSet, minDepths, maxDepths);
			
			final Index<Conclusion> conclIndex = new Index<>();
			final Index<ElkAxiom> axiomIndex = new Index<>();
			final Index<Inference<Conclusion, ElkAxiom>> infIndex = new Index<>();
			final Index<String> literalIndex = new Index<>(2);// Gringo starts indexing from 2 !!!
			
			// Assert the goal conclusion
			
			final String cLit = getConclLiteral(goalConclusion, conclIndex, literalIndex);
			final int cl = literalIndex.get(cLit);
			final String cdLit = getConclDepthLiteral(goalConclusion, 0, conclIndex, literalIndex);
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
			 * write "cdl."
			 * 
			 * rule_type head body_size neg_size neg pos
			 * 1         cdl  0         0
			 */
			out.print(1);
			out.print(' ');
			out.print(cdl);
			out.print(' ');
			out.print(0);
			out.print(' ');
			out.print(0);
			out.println();
			
			// Everything else
			
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
			 * Inference I is used in the minimal proof and its label is L.
			 * 
			 * concl(C,L)
			 * Conclusion C is used in the minimal proof and its label is L.
			 * 
			 * axiom(A,L)
			 * Axiom A is used in the minimal proof and its label is L.
			 * 
			 * infDepth(I,D)
			 * Inference I is used in the minimal proof at the depth D.
			 * TODO: I can have only depths of conclusions.
			 * 
			 * conclDepth(C,D)
			 * Conclusion C is used in the minimal proof at the depth D.
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
			 * conclDepth(P,D) :- concl(P,_), infDepth(I,D).
			 * where P is a premise use only in the inference I.
			 * 
			 * conclDepth(P,D+1) :- concl(P,_), infDepth(I,D).
			 * where P is a premise of I and it is used in multiple inferences
			 * or it is the goal conclusion.
			 * 
			 * infDepth(I,D) :- inf(I,_), conclDepth(C,D).
			 * where C is the conclusion derived by I.
			 * 
			 * :- concl(P,_), infDepth(I,D).
			 * where P is a premise of I and it is used in multiple inferences
			 * or it is the goal conclusion,
			 * and D is the maximal depth.
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
			 * @formatter:on
			 */
			
			Utils.traverseProofs(goalConclusion, inferenceSet,
					new Function<Inference<Conclusion, ElkAxiom>, Void>(){
						@Override
						public Void apply(
								final Inference<Conclusion, ElkAxiom> inf) {
							
							final Conclusion conclusion = inf.getConclusion();
							final Integer minDepth = minDepths.get(conclusion);
							final Integer maxDepth = maxDepths.get(conclusion);
							
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
								
								final int depthIncrement = deepeningPremises.contains(premise) ? 1 : 0;
								
								for (int depth = minDepth; depth <= maxDepth; depth++) {
									
									final String idLit = getInfDepthLiteral(inf, depth, infIndex, literalIndex);
									final int idl = literalIndex.get(idLit);
									
									final int premiseDepth = depth + depthIncrement;
									if (premiseDepth > maximalDepth) {
										// write constraint
										
										/* 
										 * write ":- pl, idl."
										 * 
										 * rule_type head body_size neg_size neg pos
										 * 1         1    2         0            pl idl
										 */
										out.print(1);
										out.print(' ');
										out.print(1);
										out.print(' ');
										out.print(2);
										out.print(' ');
										out.print(0);
										out.print(' ');
										out.print(pl);
										out.print(' ');
										out.print(idl);
										out.println();
										
									} else {
										// propagate depth
										
										final String pdLit = getConclDepthLiteral(premise, premiseDepth, conclIndex, literalIndex);
										final int pdl = literalIndex.get(pdLit);
										
										/* 
										 * write "pdl :- pl, idl."
										 * 
										 * rule_type head body_size neg_size neg pos
										 * 1         pdl  2         0            pl idl
										 */
										out.print(1);
										out.print(' ');
										out.print(pdl);
										out.print(' ');
										out.print(2);
										out.print(' ');
										out.print(0);
										out.print(' ');
										out.print(pl);
										out.print(' ');
										out.print(idl);
										out.println();
										
									}
									
								}
								
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
							
							for (int depth = minDepth; depth <= maxDepth; depth++) {
								
								final String idLit = getInfDepthLiteral(inf, depth, infIndex, literalIndex);
								final int idl = literalIndex.get(idLit);
								
								final String cdLit = getConclDepthLiteral(conclusion, depth, conclIndex, literalIndex);
								final int cdl = literalIndex.get(cdLit);
								
								/* 
								 * write "idl :- il, cdl."
								 * 
								 * rule_type head body_size neg_size neg pos
								 * 1         idl  2         0            il cdl
								 */
								out.print(1);
								out.print(' ');
								out.print(idl);
								out.print(' ');
								out.print(2);
								out.print(' ');
								out.print(0);
								out.print(' ');
								out.print(il);
								out.print(' ');
								out.print(cdl);
								out.println();
								
							}
							
							/* 
							 * write "il :- pl1, ..., plm."
							 * 
							 * rule_type head body_size neg_size neg pos
							 * 1         il   m         0            pl1 ... plm
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
//							if (!inf.getPremises().isEmpty()) {
//								out.print(" :- ");
//								final Iterator<? extends Conclusion> iter = inf.getPremises().iterator();
//								Conclusion premise = iter.next();
//								String pLit = getConclLiteral(premise, conclIndex, literalIndex);
//								out.print(pLit);
//								while (iter.hasNext()) {
//									out.print(", ");
//									premise = iter.next();
//									pLit = getConclLiteral(premise, conclIndex, literalIndex);
//									out.print(pLit);
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
	
	private static <C, A> Set<C> computeDeepeningPremises(
			final C goalConclusion, final InferenceSet<C, A> inferenceSet) {
		
		final Multimap<C, Inference<C, A>> premiseToInf = HashMultimap.create();
		
		final Queue<C> toDo = new LinkedList<C>();
		final Set<C> done = new HashSet<C>();
		
		toDo.add(goalConclusion);
		done.add(goalConclusion);
		
		for (;;) {
			final C next = toDo.poll();
			if (next == null) {
				break;
			}
			
			for (final Inference<C, A> inf
					: inferenceSet.getInferences(next)) {
				for (final C premise : inf.getPremises()) {
					
					premiseToInf.put(premise, inf);
					
					if (done.add(premise)) {
						toDo.add(premise);
					}
				}
			}
			
		}
		
		final Set<C> result = new HashSet<>();
		result.add(goalConclusion);
		for (final Entry<C, Collection<Inference<C, A>>> e : premiseToInf.asMap().entrySet()) {
			if (e.getValue().size() > 1) {
				result.add(e.getKey());
			}
		}
		
		return result;
	}
	
	private static <C, A> void assignDepths(final C conclusion, final int depth,
			final int maximalDepth, final Set<C> deepeningPremises,
			final InferenceSet<C, A> inferenceSet,
			final Map<C, Integer> minDepths, final Map<C, Integer> maxDepths) {
		
		if (depth > maximalDepth) {
			return;
		}
		
		boolean update = false;
		
		final Integer minDepth = minDepths.get(conclusion);
		if (minDepth == null) {
			minDepths.put(conclusion, depth);
			update = true;
		} else {
			if (depth < minDepth) {
				minDepths.put(conclusion, depth);
				update = true;
			}
		}
		
		final Integer maxDepth = maxDepths.get(conclusion);
		if (maxDepth == null) {
			maxDepths.put(conclusion, depth);
			update = true;
		} else {
			if (depth > maxDepth) {
				maxDepths.put(conclusion, depth);
				update = true;
			}
		}
		
		if (update) {
			
			for (final Inference<C, A> inf : inferenceSet.getInferences(
					conclusion)) {
				for (final C premise : inf.getPremises()) {
					
					int nextDepth = depth;
					if (deepeningPremises.contains(premise)) {
						nextDepth++;
					}
					
					assignDepths(premise, nextDepth, maximalDepth,
							deepeningPremises, inferenceSet,
							minDepths, maxDepths);
				}
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
	
	private static String getInfDepthLiteral(
			final Inference<Conclusion, ElkAxiom> inf,
			final int depth,
			final Index<Inference<Conclusion, ElkAxiom>> infIndex,
			final Index<String> literalIndex) {
		final int i = infIndex.get(inf);
		return String.format("infDepth(%d,%d)", i, depth);
	}
	
	private static String getConclDepthLiteral(final Conclusion concl,
			final int depth,
			final Index<Conclusion> conclIndex,
			final Index<String> literalIndex) {
		final int c = conclIndex.get(concl);
		return String.format("conclDepth(%d,%d)", c, depth);
	}
	
}
