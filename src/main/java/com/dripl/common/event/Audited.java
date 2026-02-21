package com.dripl.common.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for inclusion in domain event change tracking.
 * The framework auto-diffs annotated fields between old and new entity snapshots.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    /** Display name for the field in event changes. Defaults to the field name. */
    String displayName() default "";
}
