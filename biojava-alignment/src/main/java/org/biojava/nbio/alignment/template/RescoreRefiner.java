/*
 *                    BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  If you do not have a copy,
 * see:
 *
 *      http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors.  These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 *      http://www.biojava.org/
 *
 * Created on June 7, 2010
 * Author: Mark Chapman
 */

package org.biojava.nbio.alignment.template;

import org.biojava.nbio.alignment.Alignments.PairInProfileScorerType;
import org.biojava.nbio.alignment.Alignments.ProfileProfileAlignerType;
import org.biojava.nbio.core.sequence.template.Compound;
import org.biojava.nbio.core.sequence.template.Sequence;

/**
 * Defines an algorithm which computes a new alignment profile by rescoring all pairs in an alignment and
 * realigning.
 *
 * @author Mark Chapman
 * @param <S> each element of the alignment profile is of type S
 * @param <C> each element of a sequence is a {@link Compound} of type C
 */
public interface RescoreRefiner<S extends Sequence<C>, C extends Compound> extends Aligner<S, C> {

	/**
	 * Returns class type of pairwise scoring subroutine.
	 *
	 * @return class type of pairwise scoring subroutine
	 */
	PairInProfileScorerType getPairInProfileScorer();

	/**
	 * Returns class type of profile-profile alignment subroutine.
	 *
	 * @return class type of profile-profile alignment subroutine
	 */
	ProfileProfileAlignerType getProfileProfileAligner();

}
