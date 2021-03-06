package edu.harvard.iq.policymodels.parser.exceptions;

import edu.harvard.iq.policymodels.parser.policyspace.ast.CompilationUnitLocationReference;

/**
 * Indicates an error in the semantics of a construct. For example, a
 * type that's defined more than once.
 * 
 * @author michael
 */
public class SemanticsErrorException extends DataTagsParseException {

	public SemanticsErrorException(CompilationUnitLocationReference where, String message) {
		super(where, message);
	}

	public SemanticsErrorException(CompilationUnitLocationReference where, String message, Throwable cause) {
		super(where, message, cause);
	}
	
}
