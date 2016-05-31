package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
import org.semanticweb.elk.proofs.adapters.DepthLimitInferenceSetAdapter;
import org.semanticweb.elk.proofs.adapters.DepthLimitInferenceSetAdapter.Wrap;
import org.semanticweb.elk.proofs.adapters.InferenceSets;
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

public class CheckOutDepths {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(CheckOutDepths.class);
	
	public static void main(final String[] args) {
		
		if (args.length < 5) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final File recordFile = new File(args[0]);
		if (recordFile.exists()) {
			Utils.recursiveDelete(recordFile);
		}
		final int depthLimit = Integer.parseInt(args[1]);
		final String ontologyFileName = args[2];
		final String subFullIri = args[3];
		final String supFullIri = args[4];
		
		final ElkObjectBaseFactory factory = new ElkObjectBaseFactory();
		
		InputStream ontologyIS = null;
		PrintWriter record = null;
		
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
			
			final ElkSubClassOfAxiom conclusion = factory.getSubClassOfAxiom(
					factory.getClass(new ElkFullIri(subFullIri)),
					factory.getClass(new ElkFullIri(supFullIri)));
			
			final ClassConclusion expression =
					reasoner.getConclusion(conclusion);
			final InferenceSet<Conclusion, ElkAxiom> inferenceSet =
					new TracingInferenceSetInferenceSetAdapter(
							reasoner.explainConclusion(expression));
			
			record = new PrintWriter(recordFile);

			checkOutDepths(inferenceSet, expression, depthLimit, record);
			
		} catch (final FileNotFoundException e) {
			LOG.error("File Not Found!", e);
			System.exit(2);
		} catch (final ElkInconsistentOntologyException e) {
			LOG.error("The ontology is inconsistent!", e);
			System.exit(2);
		} catch (final ElkException e) {
			LOG.error("Could not classify the ontology!", e);
			System.exit(2);
		} finally {
			if (ontologyIS != null) {
				try {
					ontologyIS.close();
				} catch (final IOException e) {}
			}
			if (record != null) {
				record.close();
			}
		}
		
	}
	
	public static <C, A> void checkOutDepths(final InferenceSet<C, A> original,
			final C goalConclusion, final int maxDepth,
			final PrintWriter record) {
		
		record.println("conclusion,nLeaves,nConclusionsInDepthLimit,nInferencesInDepthLimit,avgInfsOfConcl,mConclManyInfs,leafProductSum,minLeafProductSum,productSum,minProductSum,unusedLeaves,symbolicProductSum,symbolicMinProductSum,nonRedundantProductSum,nonRedundantMinProductSum");
		
		// collect conclusions
		final Set<C> conclusions = new HashSet<C>();
		Utils.traverseProofs(goalConclusion, original,
				Functions.<Inference<C, A>>identity(),
				new Function<C, Void>() {
					@Override
					public Void apply(final C conclusion) {
						conclusions.add(conclusion);
						return null;
					}
				},
				Functions.<A>identity());
		
		final JustificationComputation<C, A> computation =
				BottomUpJustificationComputation.<C, A>getFactory()
				.create(original, new DummyMonitor());
		
		LOG.info("computing justifications");
		computation.computeJustifications(goalConclusion);
		
		for (final C conclusion : conclusions) {
			LOG.info("For conclusion: {}", conclusion);
			record.print('"');
			record.print(conclusion);
			record.print('"');
			
			LOG.info("limiting depth");
			final InferenceSet<C, Wrap<C, A>> limited =
					InferenceSets.limitDepth(original, maxDepth, conclusion);
			
			LOG.info("collecting leaves");
			final Set<Inference<C, Wrap<C, A>>> infs = new HashSet<>();
			final Set<Wrap<C, A>> leaves = new HashSet<Wrap<C, A>>();
			final Set<C> conclsInDepthLimit = new HashSet<C>();
			final List<Integer> sumInfsPerConcl = Arrays.asList(0);
			final int infCountThreshold = 5;
			final List<Integer> nConclManyInfs = Arrays.asList(0);
			Utils.traverseProofs(conclusion, limited,
					new Function<Inference<C, Wrap<C, A>>, Void>() {
						@Override
						public Void apply(final Inference<C, Wrap<C, A>> inf) {
							infs.add(inf);
							return null;
						}
					},
					new Function<C, Void>() {
						@Override
						public Void apply(final C concl) {
							conclsInDepthLimit.add(concl);
							int infCount = 0;
							for (@SuppressWarnings("unused")
									final Inference<C, Wrap<C, A>> inf
									: limited.getInferences(concl)) {
								infCount++;
							}
							sumInfsPerConcl.set(0, sumInfsPerConcl.get(0) + infCount);
							if (infCount >= infCountThreshold) {
								nConclManyInfs.set(0, nConclManyInfs.get(0) + 1);
							}
							return null;
						}
					},
					new Function<Wrap<C, A>, Void>() {
						@Override
						public Void apply(final Wrap<C, A> axiom) {
							leaves.add(axiom);
							return null;
						}
					});
			LOG.info("{} = number of leaves", leaves.size());
			LOG.info("{} = number of conclusions within the depth limit", conclsInDepthLimit.size());
			LOG.info("{} = number of inferences within the depth limit", infs.size());
			LOG.info("{} = average number of inferences of a conclusion", ((double) sumInfsPerConcl.get(0)) / conclsInDepthLimit.size());
			LOG.info("{} = number of conclusions with at least {} inferences", nConclManyInfs.get(0), infCountThreshold);
			record.print(',');
			record.print(leaves.size());
			record.print(',');
			record.print(conclsInDepthLimit.size());
			record.print(',');
			record.print(infs.size());
			record.print(',');
			record.print(((double) sumInfsPerConcl.get(0)) / conclsInDepthLimit.size());
			record.print(',');
			record.print(nConclManyInfs.get(0));
			
//			if (infs.size() > 10 * maxDepth) {
//				LOG.info("Too many inferences, skipping");
//				record.println();
//				continue;
//			}
			
			// product of justifications of leaves
			
			// product of premise-minimal justifications of leaves
			
			// sum of products of justifications of all conclusions within the depth limit
			
			// sum of products of premise-minimal justifications of all conclusions within the depth limit
			long leafProductSum = 0;
			long minLeafProductSum = 0;
//			long sum = 0;
			long productSum = 0;
			long minProductSum = 0;
			for (final Inference<C, Wrap<C, A>> inf : infs) {
				
				final Collection<? extends Set<A>> conclJs =
						computation.computeJustifications(inf.getConclusion());
				
				long leafProduct = 1;
				long minLeafProduct = 1;
				long product = 1;
				long minProduct = 1;
				for (final C premise : inf.getPremises()) {
					
					final Collection<? extends Set<A>> js =
							computation.computeJustifications(premise);
					
					product *= js.size();
					
					long count = 0;
					for (final Set<A> just : js) {
						if (BottomUpJustificationComputation.isMinimal(
								new BloomSet<>(inf.getConclusion(), just, inf.getJustification()),
								conclJs)) {
							count++;
						}
					}
					minProduct *= count;
//					sum += count;
					
				}
				for (final Wrap<C, A> leaf : inf.getJustification()) {
					if (leaf instanceof DepthLimitInferenceSetAdapter.ConclusionWrap) {
						final C premise = ((DepthLimitInferenceSetAdapter.ConclusionWrap<C, A>) leaf).conclusion;
						
						final Collection<? extends Set<A>> js =
								computation.computeJustifications(premise);
						
						leafProduct *= js.size();
						product *= js.size();
						
						long count = 0;
						for (final Set<A> just : js) {
							if (BottomUpJustificationComputation.isMinimal(
									new BloomSet<>(inf.getConclusion(), just, inf.getJustification()),
									conclJs)) {
								count++;
							}
						}
						minLeafProduct *= count;
						minProduct *= count;
//						sum += count;
						
					}
					
				}
				
				leafProductSum += leafProduct;
				minLeafProductSum += minLeafProduct;
				productSum += product;
				minProductSum += minProduct;
			}
			
			LOG.info("{} = sum of products of justifications of leaves", leafProductSum);
			LOG.info("{} = sum of products of premise-minimal justifications of leaves", minLeafProductSum);
			LOG.info("{} = sum of products of justifications of all conclusions within the depth limit", productSum);
			LOG.info("{} = sum of products of premise-minimal justifications of all conclusions within the depth limit", minProductSum);
			record.print(',');
			record.print(leafProductSum);
			record.print(',');
			record.print(minLeafProductSum);
			record.print(',');
			record.print(productSum);
			record.print(',');
			record.print(minProductSum);
			
			final JustificationComputation<C, Wrap<C, A>> limitComputation =
					BottomUpJustificationComputation.<C, Wrap<C, A>>getFactory()
					.create(limited, new DummyMonitor());
			
			LOG.info("computing justifications in the limited inference set");
			final Collection<? extends Set<Wrap<C, A>>> limitJusts =
					limitComputation.computeJustifications(conclusion);
			
			for (final Set<Wrap<C, A>> justification : limitJusts) {
				leaves.removeAll(justification);
			}
			
			if (leaves.isEmpty()) {
				LOG.info("ALL LEAVES USED IN SOME JUSTIFICATION");
			} else {
				LOG.info("{} UNUSED LEAVES: {}", leaves.size(), leaves);
			}
			record.print(',');
			record.print(leaves.size());
			
			// sum of products of symbolic justifications of all conclusions
			
			// sum of products of premise-minimal symbolic justifications of all conclusions
			long symbolicProductSum = 0;
			long symbolicMinProductSum = 0;
			for (final Inference<C, Wrap<C, A>> inf : infs) {
				
				final Collection<? extends Set<Wrap<C, A>>> conclJs =
						limitComputation.computeJustifications(inf.getConclusion());
				
				long product = 1;
				long minProduct = 1;
				for (final C premise : inf.getPremises()) {
					
					final Collection<? extends Set<Wrap<C, A>>> js =
							limitComputation.computeJustifications(premise);
					
					product *= js.size();
					
					long count = 0;
					for (final Set<Wrap<C, A>> just : js) {
						if (BottomUpJustificationComputation.isMinimal(
								new BloomSet<>(inf.getConclusion(), just, inf.getJustification()),
								conclJs)) {
							count++;
						}
					}
					minProduct *= count;
					
				}
				
				symbolicProductSum += product;
				symbolicMinProductSum += minProduct;
			}
			
			LOG.info("{} = sum of products of symbolic justifications of all conclusions", symbolicProductSum);
			LOG.info("{} = sum of products of premise-minimal symbolic justifications of all conclusions", symbolicMinProductSum);
			record.print(',');
			record.print(symbolicProductSum);
			record.print(',');
			record.print(symbolicMinProductSum);
			
			// product of justifications of non-redundant leaves
			
			// product of premise-minimal justifications of non-redundant leaves
			final Collection<? extends Set<A>> conclJs =
					computation.computeJustifications(conclusion);
			long nonRedundantProductSum = 0;
			long nonRedundantMinProductSum = 0;
			for (final Set<Wrap<C, A>> justification : limitJusts) {
				
				final Set<A> axiomsInLeaves = new HashSet<A>();
				for (final Wrap<C, A> leaf : justification) {
					if (leaf instanceof DepthLimitInferenceSetAdapter.AxiomWrap) {
						axiomsInLeaves.add(((DepthLimitInferenceSetAdapter.AxiomWrap<C, A>) leaf).axiom);
					}
				}
				
				long product = 1;
				long minProduct = 1;
				for (final Wrap<C, A> leaf : justification) {
					if (leaf instanceof DepthLimitInferenceSetAdapter.ConclusionWrap) {
						final C concl = ((DepthLimitInferenceSetAdapter.ConclusionWrap<C, A>) leaf).conclusion;
						
						final Collection<? extends Set<A>> js =
								computation.computeJustifications(concl);
						
						product *= js.size();
						
						long count = 0;
						for (final Set<A> just : js) {
							if (BottomUpJustificationComputation.isMinimal(
									new BloomSet<>(concl, just, axiomsInLeaves),
									conclJs)) {
								count++;
							}
						}
						minProduct *= count;
						
					}
				}
				
				nonRedundantProductSum += product;
				nonRedundantMinProductSum += minProduct;
			}
			
			LOG.info("{} = sum of products of justifications of non-redundant leaves", nonRedundantProductSum);
			LOG.info("{} = sum of products of premise-minimal justifications of non-redundant leaves", nonRedundantMinProductSum);
			record.print(',');
			record.print(nonRedundantProductSum);
			record.print(',');
			record.print(nonRedundantMinProductSum);
			
			record.flush();
			record.println();
		}
		
	}
	
}
