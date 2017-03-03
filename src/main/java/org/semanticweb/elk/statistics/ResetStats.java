package org.semanticweb.elk.statistics;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a method that resets statistics. Calling all methods annotated with
 * this annotation should reset all statistics of that method's owner. The
 * method must have no parameter.
 * 
 * @author Peter Skocovsky
 */
@Retention(RUNTIME)
@Target({ METHOD })
public @interface ResetStats {

}
