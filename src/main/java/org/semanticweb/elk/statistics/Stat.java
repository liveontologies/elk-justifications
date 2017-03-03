package org.semanticweb.elk.statistics;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks one statistical value. If a method is annotated, the value is what it
 * returns. The method must have no parameter.
 * 
 * @author Peter Skocovsky
 */
@Retention(RUNTIME)
@Target({ FIELD, METHOD })
public @interface Stat {

}
