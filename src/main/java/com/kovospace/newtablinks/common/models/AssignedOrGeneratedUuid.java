package com.kovospace.newtablinks.common.models;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

/**
 * Marks an identifier that the application generates only when the caller did not supply one.
 *
 * <p>Replaces {@code @GeneratedValue(strategy = GenerationType.UUID)} on
 * {@link AbstractAuditableEntity}. The standard annotation is unconditional: Hibernate's
 * {@code UuidGenerator} declares {@code allowAssignedIdentifiers() == false}, so an identifier
 * already present on a transient entity is silently thrown away and replaced. That is the right
 * behaviour for every path this application had until synchronization arrived, and the wrong one
 * for the single path that has to keep an identifier the browser extension chose.</p>
 *
 * <p>See {@link AssignedOrGeneratedUuidGenerator} for what the generator actually does and why
 * the extension is allowed to name a row.</p>
 *
 * @since 0.0.6
 */
@IdGeneratorType(AssignedOrGeneratedUuidGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AssignedOrGeneratedUuid {
}
