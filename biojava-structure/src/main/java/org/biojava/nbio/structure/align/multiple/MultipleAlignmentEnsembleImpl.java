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
 */
package org.biojava.nbio.structure.align.multiple;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Matrix4d;

import org.biojava.nbio.structure.Atom;
import org.biojava.nbio.structure.Calc;
import org.biojava.nbio.structure.StructureException;
import org.biojava.nbio.structure.StructureIdentifier;
import org.biojava.nbio.structure.align.client.StructureName;
import org.biojava.nbio.structure.align.helper.AlignUtils;
import org.biojava.nbio.structure.align.model.AFPChain;
import org.biojava.nbio.structure.align.multiple.util.MultipleAlignmentScorer;
import org.biojava.nbio.structure.align.util.AtomCache;
import org.biojava.nbio.structure.jama.Matrix;

/**
 * A general implementation of a {@link MultipleAlignmentEnsemble}.
 *
 * @author Aleix Lafita
 * @since 4.1.0
 *
 */
public class MultipleAlignmentEnsembleImpl extends AbstractScoresCache
		implements MultipleAlignmentEnsemble, Serializable, Cloneable {

	private static final long serialVersionUID = -5732485866623431898L;

	// Creation Properties
	private String algorithmName;
	private String version;
	private Long ioTime;
	private Long calculationTime;

	// Structure Identifiers
	private List<StructureIdentifier> structureIdentifiers;
	private List<Atom[]> atomArrays;
	private List<Matrix> distanceMatrix;

	private List<MultipleAlignment> multipleAlignments;

	/**
	 * Default Constructor. Empty ensemble, no structures assigned.
	 *
	 */
	public MultipleAlignmentEnsembleImpl() {

		algorithmName = null;
		version = null;
		ioTime = null;
		calculationTime = null;

		structureIdentifiers = null;
		atomArrays = null;
		distanceMatrix = null;
		multipleAlignments = null;
	}

	/**
	 * Constructor using structure identifiers.
	 *
	 * @param structureIdentifiers
	 *            List of Structure names, that can be parsed by
	 *            {@link AtomCache}.
	 */
	public MultipleAlignmentEnsembleImpl(
			List<StructureIdentifier> structureIdentifiers) {
		this();
		setStructureIdentifiers(structureIdentifiers);
	}

	/**
	 * Copy constructor. This copies recursively all member variables, including
	 * MultipleAlignments, Atom arrays and cached variables.
	 *
	 * @param e
	 *            MultipleAlignmentEnsemble to copy.
	 */
	public MultipleAlignmentEnsembleImpl(MultipleAlignmentEnsembleImpl e) {

		super(e); // Copy the scores
		algorithmName = e.algorithmName;
		version = e.version;
		ioTime = e.ioTime;
		calculationTime = e.calculationTime;

		distanceMatrix = null;
		if (e.distanceMatrix != null) {
			// Make a deep copy of everything
			distanceMatrix = new ArrayList<>();
			for (Matrix mat : e.distanceMatrix) {
				distanceMatrix.add((Matrix) mat.clone());
			}
		}

		multipleAlignments = null;
		if (e.multipleAlignments != null) {
			// Make a deep copy of everything
			multipleAlignments = new ArrayList<>();
			for (MultipleAlignment msa : e.multipleAlignments) {
				MultipleAlignment newMSA = msa.clone();
				newMSA.setEnsemble(this);
				multipleAlignments.add(newMSA);
			}
		}

		if (e.atomArrays != null) {
			atomArrays = new ArrayList<>(e.atomArrays);
		}
		if (e.structureIdentifiers != null) {
			structureIdentifiers = new ArrayList<>(
					e.structureIdentifiers);
		}
	}

	/**
	 * Constructor from an AFPChain instance. Creates an equivalent pairwise
	 * alignment, but in the MultipleAlignment format.
	 *
	 * @param afp
	 *            pairwise alignment
	 * @param ca1
	 *            Atoms of the first strcuture
	 * @param ca2
	 *            Atoms of the second structure
	 * @param flexible
	 *            true if the alignment is flexible (use BlockSets)
	 */
	public MultipleAlignmentEnsembleImpl(AFPChain afp, Atom[] ca1, Atom[] ca2,
			boolean flexible) {

		this();
		// Copy all the creation and algorithm information
		atomArrays = Arrays.asList(ca1, ca2);
		if (afp.getName1() != null && !afp.getName1().isEmpty() &&
				afp.getName2() != null && !afp.getName2().isEmpty()) {
			structureIdentifiers = Arrays.<StructureIdentifier> asList(
					new StructureName(afp.getName1()),
					new StructureName(afp.getName2()));
		}
		algorithmName = afp.getAlgorithmName();
		version = afp.getVersion();
		calculationTime = afp.getCalculationTime();

		MultipleAlignment msa = new MultipleAlignmentImpl(this);
		setMultipleAlignments(Arrays.asList(msa));

		// Convert the rotation and translation to a Matrix4D and set it
		Matrix4d ident = new Matrix4d();
		ident.setIdentity();
		Matrix[] rot = afp.getBlockRotationMatrix();
		Atom[] shift = afp.getBlockShiftVector();

		// Create a BlockSet for every block in AFPChain if flexible
		if (flexible) {
			for (int bs = 0; bs < afp.getBlockNum(); bs++) {
				BlockSet blockSet = new BlockSetImpl(msa);
				Matrix4d blockTr = null;
				try {
					blockTr = Calc.getTransformation(rot[bs], shift[bs]);
				} catch (IndexOutOfBoundsException e) {
					blockTr = ident;
				} catch (NullPointerException e) {
					blockTr = ident;
				}
				blockSet.setTransformations(Arrays.asList(ident, blockTr));
				Block block = new BlockImpl(blockSet);
				block.setAlignRes(new ArrayList<List<Integer>>());
				block.getAlignRes().add(new ArrayList<Integer>());
				block.getAlignRes().add(new ArrayList<Integer>());

				// Set the transformation of the BlockSet
				Matrix rotB = afp.getBlockRotationMatrix()[bs];
				Atom shiftB = afp.getBlockShiftVector()[bs];
				Matrix4d transformB = Calc.getTransformation(rotB, shiftB);
				blockSet.setTransformations(Arrays.asList(ident, transformB));

				// Convert the optimal alignment to a Block
				for (int i = 0; i < afp.getOptAln()[bs][0].length; i++) {
					block.getAlignRes().get(0).add(afp.getOptAln()[bs][0][i]);
					block.getAlignRes().get(1).add(afp.getOptAln()[bs][1][i]);
				}
			}
		} // Create a Block for every block in AFPChain if not flexible
		else {
			BlockSet blockSet = new BlockSetImpl(msa);
			Matrix4d blockTr = null;
			try {
				blockTr = Calc.getTransformation(rot[0], shift[0]);
			} catch (IndexOutOfBoundsException e) {
				blockTr = ident;
			} catch (NullPointerException e) {
				blockTr = ident;
			}
			blockSet.setTransformations(Arrays.asList(ident, blockTr));
			for (int bs = 0; bs < afp.getBlockNum(); bs++) {
				Block block = new BlockImpl(blockSet);
				block.setAlignRes(new ArrayList<List<Integer>>());
				block.getAlignRes().add(new ArrayList<Integer>());
				block.getAlignRes().add(new ArrayList<Integer>());

				// Convert the optimal alignment to a Block
				for (int i = 0; i < afp.getOptAln()[bs][0].length; i++) {
					block.getAlignRes().get(0).add(afp.getOptAln()[bs][0][i]);
					block.getAlignRes().get(1).add(afp.getOptAln()[bs][1][i]);
				}
			}
		}

		// Copy the scores stored in the AFPChain
		msa.putScore(MultipleAlignmentScorer.PROBABILITY, afp.getProbability());
		msa.putScore(MultipleAlignmentScorer.AVGTM_SCORE, afp.getTMScore());
		msa.putScore(MultipleAlignmentScorer.CE_SCORE, afp.getAlignScore());
		msa.putScore(MultipleAlignmentScorer.RMSD, afp.getTotalRmsdOpt());
	}

	@Override
	public MultipleAlignmentEnsembleImpl clone() {
		return new MultipleAlignmentEnsembleImpl(this);
	}

	@Override
	public String getAlgorithmName() {
		return algorithmName;
	}

	@Override
	public void setAlgorithmName(String algorithmName) {
		this.algorithmName = algorithmName;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public void setVersion(String version) {
		this.version = version;
	}

	@Override
	public Long getIoTime() {
		return ioTime;
	}

	@Override
	public void setIoTime(Long millis) {
		this.ioTime = millis;
	}

	@Override
	public Long getCalculationTime() {
		return calculationTime;
	}

	@Override
	public void setCalculationTime(Long millis) {
		this.calculationTime = millis;
	}

	@Override
	public List<StructureIdentifier> getStructureIdentifiers() {
		return structureIdentifiers;
	}

	@Override
	public void setStructureIdentifiers(List<StructureIdentifier> structureNames) {
		this.structureIdentifiers = structureNames;
	}

	@Override
	public List<Atom[]> getAtomArrays() {
		if (atomArrays == null) {
			try {
				updateAtomArrays();
			} catch (IOException e) {
				throw new NullPointerException(e.getMessage());
			} catch (StructureException e) {
				throw new NullPointerException(e.getMessage());
			}
		}
		return atomArrays;
	}

	@Override
	public void setAtomArrays(List<Atom[]> atomArrays) {
		this.atomArrays = atomArrays;
	}

	/**
	 * Force the atom arrays to regenerate based on
	 * {@link #getStructureIdentifiers()}.
	 *
	 * @throws IOException
	 * @throws StructureException
	 */
	public void updateAtomArrays() throws IOException, StructureException {
		AtomCache cache = new AtomCache();
		atomArrays = new ArrayList<>();
		for (StructureIdentifier name : getStructureIdentifiers()) {
			Atom[] array = cache.getRepresentativeAtoms(name);
			atomArrays.add(array);
		}
	}

	@Override
	public List<Matrix> getDistanceMatrix() {
		if (distanceMatrix == null)
			updateDistanceMatrix();
		return distanceMatrix;
	}

	/**
	 * Force recalculation of the distance matrices.
	 */
	public void updateDistanceMatrix() {

		// Reset the distance Matrix variable
		distanceMatrix = new ArrayList<>();

		for (int s = 0; s < size(); s++) {
			Atom[] ca = atomArrays.get(s);
			Matrix distMat = AlignUtils.getDistanceMatrix(ca, ca);
			distanceMatrix.add(distMat);
		}
	}

	@Override
	public List<MultipleAlignment> getMultipleAlignments() {

		if (multipleAlignments == null) {
			multipleAlignments = new ArrayList<>();
		}
		return multipleAlignments;
	}

	@Override
	public MultipleAlignment getMultipleAlignment(int index) {
		return multipleAlignments.get(index);
	}

	@Override
	public void setMultipleAlignments(List<MultipleAlignment> alignments) {
		this.multipleAlignments = alignments;
	}

	@Override
	public void addMultipleAlignment(MultipleAlignment alignment) {
		if (multipleAlignments == null) {
			multipleAlignments = new ArrayList<>();
		}
		multipleAlignments.add(alignment);
		alignment.setEnsemble(this);
	}

	@Override
	public int size() {
		if (structureIdentifiers != null)
			return structureIdentifiers.size();
		else if (atomArrays != null)
			return atomArrays.size();
		else {
			throw new IndexOutOfBoundsException(
					"Empty ensemble: names == null && atoms == null");
		}
	}

	@Override
	public void clear() {
		super.clear();
		distanceMatrix = null;
		for (MultipleAlignment a : getMultipleAlignments())
			a.clear();
	}
}
