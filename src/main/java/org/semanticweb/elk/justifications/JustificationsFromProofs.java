package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.proofs.adapters.OWLExpressionInferenceSetAdapter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.semanticweb.owlapitools.proofs.exception.ProofGenerationException;
import org.semanticweb.owlapitools.proofs.expressions.OWLExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

public class JustificationsFromProofs {
	
	private static final Logger LOG = LoggerFactory.getLogger(JustificationsFromProofs.class);
	
	public static void main(String[] args) {
		
		if (args.length < 5) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final String ontologyFileName = args[0];
		final String conclusionsFileName = args[1];
		final long timeOut = Long.parseLong(args[2]);
		final File outputDirectory = new File(args[3]);
		if (!Utils.cleanDir(outputDirectory)) {
			LOG.error("Could not prepare the output directory!");
			System.exit(2);
		}
		final File recordFile = new File(args[4]);
		if (recordFile.exists()) {
			Utils.recursiveDelete(recordFile);
		}
		
		PrintWriter record = null;
		
		final OWLOntologyManager manager =
				OWLManager.createOWLOntologyManager();
		
		try {
			
			record = new PrintWriter(recordFile);
			record.println("conclusion, didTimeOut, time, nJust");
			
			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager.loadOntologyFromOntologyDocument(
					new File(ontologyFileName));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Loaded ontology: {}", ont.getOntologyID());
			
			LOG.info("Loading conclusions ...");
			start = System.currentTimeMillis();
			final OWLOntology conclusionsOnt =
					manager.loadOntologyFromOntologyDocument(
							new File(conclusionsFileName));
			final Set<OWLSubClassOfAxiom> conclusions =
					conclusionsOnt.getAxioms(AxiomType.SUBCLASS_OF);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Number of conclusions: {}", conclusions.size());
			
			final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			final ExplainingOWLReasoner reasoner =
					(ExplainingOWLReasoner) reasonerFactory.createReasoner(ont);
			
			LOG.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			LOG.info("Warm Up ...");
			int count = 30;
			for (final OWLSubClassOfAxiom conclusion
					: Iterables.cycle(conclusions)) {
				
				final JustificationComputation<OWLExpression, OWLAxiom> computation = new BottomUpJustificationComputation<OWLExpression, OWLAxiom>(
						new OWLExpressionInferenceSetAdapter());
				
				LOG.info("... {} ...", count);
				withTimeout(20000, new Runnable() {
					@Override
					public void run() {
						
						try {
							computation.computeJustifications(reasoner.getDerivedExpression(conclusion));
						} catch (final ProofGenerationException e) {
							throw new RuntimeException(e);
						} catch (final InterruptedException e) {
							// Do nothing.
						}
						
					}
				});
				
				if (--count <= 0) {
					break;
				}
				
			}
			LOG.info("... that's enough");
			
			for (final OWLSubClassOfAxiom conclusion : conclusions) {
				
				record.print("\"");
				record.print(conclusion);
				record.print("\",");
				record.flush();
				
				final JustificationComputation<OWLExpression, OWLAxiom> computation = new BottomUpJustificationComputation<OWLExpression, OWLAxiom>(
						new OWLExpressionInferenceSetAdapter());
				
				final AtomicReference<Collection<Set<OWLAxiom>>> result =
						new AtomicReference<Collection<Set<OWLAxiom>>>(null);
				final AtomicLong time2 = new AtomicLong();
				
				LOG.info("Obtaining justifications for {} ...", conclusion);
				final boolean didTimeOut = withTimeout(timeOut, new Runnable() {
					@Override
					public void run() {
						LOG.info("start the worker ...");
						final long s = System.currentTimeMillis();
						
						try {
							
							final Collection<Set<OWLAxiom>> justifications =
									computation.computeJustifications(reasoner.getDerivedExpression(conclusion));
							final long t = System.currentTimeMillis() - s;
							time2.set(t);
							result.set(justifications);
							
						} catch (final ProofGenerationException e) {
							throw new RuntimeException(e);
						} catch (final InterruptedException e) {
							LOG.info("... interrupted ...");
						}
						
						LOG.info("... end the worker; took {}s",
								(System.currentTimeMillis() - s)/1000.0);
					}
				});
				
				record.print(didTimeOut?"TRUE":"FALSE");
				record.print(",");
				record.flush();
				
				if (didTimeOut) {
					LOG.info("... timeout");
					
					record.print(timeOut);
					record.print(",");
					record.print("0");
					record.println();
					
				} else {
					LOG.info("... took {}s", time2.get()/1000.0);
					
					final Collection<Set<OWLAxiom>> justifications = result.get();
					LOG.info("found {} justifications.", justifications.size());
					
					record.print(time2.get());
					record.print(",");
					record.print(justifications.size());
					record.println();
					
					final String conclName = Utils.toFileName(conclusion);
					final File outDir = new File(outputDirectory, conclName);
					outDir.mkdirs();
					int i = 0;
					for (final Set<OWLAxiom> justification : justifications) {
						
						final String fileName = String.format("%03d.owl", ++i);
						final OWLOntology outOnt = manager.createOntology(
								justification,
								IRI.create("Justification_" + i + "_for_" + conclName));
						manager.saveOntology(outOnt,
								new FunctionalSyntaxDocumentFormat(),
								new FileOutputStream(new File(outDir, fileName)));
						
					}
					
				}
				
				record.flush();
				
			}
			
		} catch (final OWLOntologyCreationException e) {
			LOG.error("Could not load the ontology!", e);
			System.exit(2);
		} catch (final OWLOntologyStorageException e) {
			LOG.error("Could not save the ontology!", e);
			System.exit(2);
		} catch (final FileNotFoundException e) {
			LOG.error("File not found!", e);
			System.exit(2);
		} finally {
			if (record != null) {
				record.close();
			}
		}
		
		LOG.debug("..caw");
	}

	private static boolean withTimeout(final long timeOut, final Runnable runnable) {
		
		final Thread worker = new Thread(runnable);
		
		boolean didTimeOut = false;
		
		worker.start();
		try {
			if (timeOut > 0) {
				worker.join(timeOut);
				if (worker.isAlive()) {
					didTimeOut = true;
					worker.interrupt();
				}
			}
			worker.join();
		} catch (final InterruptedException e) {
			LOG.warn("Waiting for the worker thread interruptet!", e);
		}
		
		return didTimeOut;
	}
	
}
