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
package org.biojava.nbio.structure;

/**
 * The type of entity (polymer, non-polymer, water, macrolide, branched)
 * as defined in the mmCIF dictionary: <a href="http://mmcif.wwpdb.org/dictionaries/mmcif_pdbx_v40.dic/Items/_entity.type.html"></a>
 * <p>
 * Entities are of four types:  polymer, non-polymer, macrolide and water.
 * <p>
 * Note that the water category includes only water;  ordered
 * solvent such as sulfate ion or acetone would be described as
 * individual non-polymer entities.
 * It is not clear what macrolides are, but they appear to be supported since mmCIF 4.0.
 *
 *
 * @author Anthony Bradley
 * @author Jose Duarte
 *
 */
public enum EntityType {

	/**
	 * Polymeric entities: poly-peptides and nucleotide chains
	 */
	POLYMER("polymer"),

	/**
	 * The 'branched' type use mainly to represent carbohydrates.
	 * The type was introduced in these versions of the mmcif dictionary:
	 * 5.101	2012-08-22
	 * 5.291	2017-09-10
	 * 5.304	2018-08-01
	 * The type will only be used for PDB-deposited files from July 2020, as part of
	 * the carbohydrate remediation project.
	 * @since 5.4.0
	 */
	BRANCHED("branched"),

	/**
	 * Non-polymeric entities: ligands, metal ions, buffer molecules, etc
	 */
	NONPOLYMER("non-polymer"),

	/**
	 * Water
	 */
	WATER("water"),

	/**
	 * Macrolide. Supported in mmCIF 4.0 dictionary. Not clear what it refers to.
	 */
	MACROLIDE("macrolide");

	private String entityType;

	/**
	 * @param entType the type of the Entity
	 */
	EntityType(String entType) {
		this.setEntityType(entType);
	}

	/**
	 * Returns the type of the Entity as a String
	 *
	 * @return String representation of the entity type.
	 */
	public String getEntityType() {
		return entityType;
	}


	private void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	/**
	 * Creates a new EntityType from a String value.
	 * Returns null if entityType is null or not one of the supported
	 * standard types.
	 *
	 * @param entityType should be one of "polymer", "non-polymer", "water", "macrolide", "branched" (case insensitive)
	 * @return an EntityType object or null if the input string doesn't correspond to a known entity
	 */
	public static EntityType entityTypeFromString(String entityType) {
		if (entityType == null)
			return null;

		for(EntityType et : EntityType.values()) {
			if (entityType.equalsIgnoreCase(et.entityType)) {
				return et;
			}
		}
		return null;
	}

}
