package org.liveontologies.proofs;

import java.util.Set;

import org.liveontologies.pinpointing.experiments.ExperimentException;
import org.liveontologies.puli.Inference;
import org.liveontologies.puli.InferenceJustifier;
import org.liveontologies.puli.Proof;

/**
 * The proof returned by {@link #getProof()} with the justifier returned by
 * {@link #getJustifier()} is complete w.r.t. justifications of the conclusion
 * returned by {@link #getQuery()}.
 * 
 * @author Peter Skocovsky
 * 
 * @param <C>
 *            The type of conclusions over which are the inferences in the
 *            proof.
 * @param <I>
 *            The type of inferences in the proof.
 * @param <A>
 *            The type of objects by sets of which are the inferences justified.
 */
public interface JustificationCompleteProof<C, I extends Inference<? extends C>, A> {

	/**
	 * @return The proof returned by {@link #getProof()} with the justifier
	 *         returned by {@link #getJustifier()} must be complete w.r.t.
	 *         justifications of this conclusion.
	 */
	C getQuery();

	/**
	 * @return Proof that, with the justifier returned by
	 *         {@link #getJustifier()}, is complete w.r.t. justifications of the
	 *         conclusion returned by {@link #getQuery()}.
	 * @throws ExperimentException
	 */
	Proof<? extends I> getProof() throws ExperimentException;

	/**
	 * @return The justifier with which the proof returned by
	 *         {@link #getProof()} must be complete w.r.t. justifications of the
	 *         conclusion returned by {@link #getQuery()}.
	 */
	InferenceJustifier<? super I, ? extends Set<? extends A>> getJustifier();

}
