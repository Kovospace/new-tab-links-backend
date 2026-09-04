package com.kovospace.newtablinks.common.models;

import java.lang.reflect.Member;
import java.util.EnumSet;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

/**
 * Keeps the identifier an entity already carries, and invents one only when it carries none.
 *
 * <p>Every row this service creates on its own still gets a random version 4 UUID, exactly as
 * {@code @GeneratedValue(strategy = GenerationType.UUID)} produced before. The difference is the
 * one case that annotation makes impossible: the browser extension works offline and mints
 * identifiers for what the user creates while disconnected, so a pushed change has to be stored
 * <em>under the identifier the extension already uses locally</em>. Overwriting it would force
 * the extension to rewrite its own database after every push, and would break every reference it
 * has already handed to another record.</p>
 *
 * <p>Letting a caller name a row is safe here because it decides nothing but the key. Every
 * lookup in this application is scoped to the owner, so an identifier is only ever a name inside
 * one account; and the synchronization endpoint refuses to reuse an identifier that already
 * exists, handing the client a server-chosen one instead. Nothing about the row's ownership,
 * visibility or contents follows from who chose its key.</p>
 *
 * <p>Hibernate consults {@link #allowAssignedIdentifiers()} before insert: when it returns
 * {@code true}, the entity's current identifier is passed to {@link #generate} as
 * {@code currentValue} rather than being ignored.</p>
 *
 * @since 0.0.6
 */
public class AssignedOrGeneratedUuidGenerator implements BeforeExecutionGenerator {

    /**
     * Creates the generator.
     *
     * <p>This is the constructor signature Hibernate looks for on a generator referenced by an
     * {@link org.hibernate.annotations.IdGeneratorType} annotation. None of the arguments are
     * needed: the behaviour has nothing to configure.</p>
     *
     * @param annotation      the marker annotation found on the identifier
     * @param annotatedMember the field or getter it was found on
     * @param creationContext Hibernate's view of the mapping being built
     */
    public AssignedOrGeneratedUuidGenerator(
            final AssignedOrGeneratedUuid annotation,
            final Member annotatedMember,
            final GeneratorCreationContext creationContext) {
    }

    /**
     * Tells Hibernate to hand this generator the identifier the entity already holds.
     *
     * <p>The whole point of the class. With the default {@code false}, {@link #generate} would
     * always receive {@code null} as the current value and could not honour anything.</p>
     *
     * @return always {@code true}
     */
    @Override
    public boolean allowAssignedIdentifiers() {
        return true;
    }

    /**
     * Declares that identifiers are produced on insert only and never touched again.
     *
     * @return the singleton set containing {@link EventType#INSERT}
     */
    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }

    /**
     * Returns the identifier the row will be inserted with.
     *
     * @param session      session performing the insert, unused
     * @param owner        entity being inserted, unused
     * @param currentValue identifier the entity already carries, or {@code null} when it has none
     * @param eventType    lifecycle event asking for the value, always {@link EventType#INSERT}
     * @return {@code currentValue} when it is not {@code null}, otherwise a fresh random UUID
     */
    @Override
    public Object generate(
            final SharedSessionContractImplementor session,
            final Object owner,
            final Object currentValue,
            final EventType eventType) {

        if (currentValue != null) {
            return currentValue;
        }
        return UUID.randomUUID();
    }
}
