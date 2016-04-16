package org.semanticweb.elk.justifications;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapitools.proofs.ExplainingOWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtractDirectSubsumptions {
	
	private static final Logger LOG =
			LoggerFactory.getLogger(ExtractDirectSubsumptions.class);

	public static void main(final String[] args) {
		
		if (args.length < 2) {
			LOG.error("Insufficient arguments!");
			System.exit(1);
		}
		
		final String ontologyFileName = args[0];
		final File outputFile = new File(args[1]);
		if (outputFile.exists()) {
			Utils.recursiveDelete(outputFile);
		}
		
		final OWLOntologyManager manager =
				OWLManager.createOWLOntologyManager();
		final OWLDataFactory factory = manager.getOWLDataFactory();
		
		PrintWriter output = null;

		try {
			
			LOG.info("Loading ontology ...");
			long start = System.currentTimeMillis();
			final OWLOntology ont = manager.loadOntologyFromOntologyDocument(
					new File(ontologyFileName));
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Loaded ontology: {}", ont.getOntologyID());
			
			final OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
			final ExplainingOWLReasoner reasoner =
					(ExplainingOWLReasoner) reasonerFactory.createReasoner(ont);
			
			LOG.info("Classifying ...");
			start = System.currentTimeMillis();
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);

			LOG.info("Extracting direct subsumptions ...");
			start = System.currentTimeMillis();
			final List<OWLSubClassOfAxiom> subsumptions =
					extractSubsumptions(reasoner, factory);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			LOG.info("Number of direct subsumptions: {}", subsumptions.size());
			
			LOG.info("Sorting direct subsumptions ...");
			start = System.currentTimeMillis();
			Collections.sort(subsumptions);
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
			LOG.info("Printing direct subsumptions ...");
			start = System.currentTimeMillis();
			output = new PrintWriter(outputFile);
			for (final OWLSubClassOfAxiom subsumption : subsumptions) {
				output.print("\"");
				output.print(subsumption.getSubClass().asOWLClass().getIRI());
				output.print("\",\"");
				output.print(subsumption.getSuperClass().asOWLClass().getIRI());
				output.println("\"");
			}
			LOG.info("... took {}s",
					(System.currentTimeMillis() - start)/1000.0);
			
		} catch (final OWLOntologyCreationException e) {
			LOG.error("Could not load the ontology!", e);
			System.exit(2);
		} catch (final FileNotFoundException e) {
			LOG.error("File Not Found!", e);
			System.exit(2);
		} finally {
			if (output != null) {
				output.close();
			}
		}
		
	}

	private static List<OWLSubClassOfAxiom> extractSubsumptions(
			final OWLReasoner reasoner, final OWLDataFactory factory) {
		
		final Set<Node<OWLClass>> done = new HashSet<Node<OWLClass>>();
		final Queue<Node<OWLClass>> toDo = new LinkedList<Node<OWLClass>>();
		final List<OWLSubClassOfAxiom> result =
				new ArrayList<OWLSubClassOfAxiom>();
		
		toDo.add(reasoner.getTopClassNode());
		done.add(reasoner.getTopClassNode());
		
		Node<OWLClass> node;
		while ((node = toDo.poll()) != null) {
			
			if (!node.isBottomNode()) {
				
				// Put the equivalences into the conclusion queue.
				final ArrayList<OWLClass> entities =
						new ArrayList<OWLClass>(node.getEntities());
				for (int i = 0; i < entities.size() - 1; i++) {
					for (int j = 1; j < entities.size(); j++) {
						final OWLClass first = entities.get(i);
						final OWLClass second = entities.get(j);
						
						if (first.equals(second)) {
							continue;
						}
						
						if (!second.equals(factory.getOWLThing())
								&& !first.equals(factory.getOWLNothing())) {
							result.add(factory
									.getOWLSubClassOfAxiom(first, second));
						}

						if (!first.equals(factory.getOWLThing())
								&& !second.equals(factory.getOWLNothing())) {
							result.add(factory
									.getOWLSubClassOfAxiom(second, first));
						}
						
					}
				}
				
				// Put the subclasses into the conclusion queue.
				for (final OWLClass sup : entities) {
					if (sup.equals(factory.getOWLThing())) {
						continue;
					}
					for (final Node<OWLClass> subNode : reasoner.getSubClasses(
							node.getRepresentativeElement(), true)) {
						if (subNode.isBottomNode()) {
							continue;
						}
						for (final OWLClass sub : subNode) {
							result.add(factory
									.getOWLSubClassOfAxiom(sub, sup));
						}
					}
				}
				
			}
			
			// Queue up the subnodes.
			for (final Node<OWLClass> subNode : reasoner.getSubClasses(
					node.getRepresentativeElement(), true)) {
				if (done.add(subNode)) {
					toDo.add(subNode);
				}
			}
			
		}
		
		return result;
	}
	
}
