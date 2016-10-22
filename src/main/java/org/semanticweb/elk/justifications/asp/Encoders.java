package org.semanticweb.elk.justifications.asp;

import org.semanticweb.elk.justifications.Utils;
import org.semanticweb.elk.proofs.Inference;
import org.semanticweb.elk.proofs.InferenceSet;

import com.google.common.base.Function;

public class Encoders {
	
	private Encoders() {
		// Forbid instantiation.
	}
	
	@SafeVarargs
	public static <C, A> Encoder<C, A> combine(
			final Encoder<C, A>... encoders) {
		
		return new Encoder<C, A>() {

			@Override
			public void encodeCommon(final C goalConclusion) {
				for (final Encoder<C, A> encoder : encoders) {
					encoder.encodeCommon(goalConclusion);
				}
			}

			@Override
			public void encodeInference(final Inference<C, A> inference) {
				for (final Encoder<C, A> encoder : encoders) {
					encoder.encodeInference(inference);
				}
			}

			@Override
			public void encodeConclusion(final C conclusion) {
				for (final Encoder<C, A> encoder : encoders) {
					encoder.encodeConclusion(conclusion);
				}
			}

			@Override
			public void encodeAxiom(final A axiom) {
				for (final Encoder<C, A> encoder : encoders) {
					encoder.encodeAxiom(axiom);
				}
			}
			
			@Override
			public void encodeSuffix() {
				for (final Encoder<C, A> encoder : encoders) {
					encoder.encodeSuffix();
				}
			}
			
		};
		
	}
	
	public static <C, A> void encode(
			final InferenceSet<C, A> inferenceSet,
			final C goalConclusion,
			final Encoder<C, A> encoder) {
		
		encoder.encodeCommon(goalConclusion);
		
		Utils.traverseProofs(goalConclusion, inferenceSet,
				new Function<Inference<C, A>, Void>(){
					@Override
					public Void apply(final Inference<C, A> inference) {
						encoder.encodeInference(inference);
						return null;
					}
				},
				new Function<C, Void>(){
					@Override
					public Void apply(final C conclusion) {
						encoder.encodeConclusion(conclusion);
						return null;
					}
				},
				new Function<A, Void>(){
					@Override
					public Void apply(final A axiom) {
						encoder.encodeAxiom(axiom);
						return null;
					}
				}
		);
		
		encoder.encodeSuffix();
		
	}
	
}
