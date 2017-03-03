package org.semanticweb.elk.statistics;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a field that holds an object, or a method that returns an object, that
 * has {@link Stat}s that should be considered {@link Stat}s of owner of the
 * field/method. The method must have no parameter.
 * 
 * @author Peter Skocovsky
 */
@Retention(RUNTIME)
@Target({ FIELD, METHOD })
public @interface NestedStats {

}
