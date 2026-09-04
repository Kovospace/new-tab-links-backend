package com.kovospace.newtablinks.common.models;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for every persisted entity in this application.
 *
 * <p>It supplies the identifier and the two audit timestamps that all entities share, so that
 * concrete entities describe only what makes them different. The timestamps are maintained by
 * JPA lifecycle callbacks rather than by callers, which guarantees they are set even when an
 * entity is persisted from a path that forgot about them.</p>
 *
 * <p>{@code updatedAt} exists specifically to support synchronization with the browser
 * extension: it is the field a client compares against to discover what changed since its last
 * successful sync.</p>
 *
 * @since 0.0.1
 */
@MappedSuperclass
public abstract class AbstractAuditableEntity {

    /**
     * Surrogate primary key.
     *
     * <p>Generated on first save when the entity carries none, and kept as it is when it already
     * does - which is how a change pushed by the browser extension keeps the identifier the
     * extension minted for it while offline. See {@link AssignedOrGeneratedUuidGenerator}.</p>
     */
    @Id
    @AssignedOrGeneratedUuid
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Moment the row was first written. Never changes afterwards.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Moment the row was last written, including its very first write.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Stamps both timestamps immediately before the entity is inserted.
     */
    @PrePersist
    protected void fillTimestampsBeforeInsert() {
        final Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Refreshes the modification timestamp immediately before the entity is updated.
     */
    @PreUpdate
    protected void refreshTimestampBeforeUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the primary key, or {@code null} while the entity has never been persisted.
     *
     * @return the identifier, or {@code null} for a transient entity
     */
    public UUID getId() {
        return id;
    }

    /**
     * Overwrites the primary key.
     *
     * <p>Two callers are intended: tests, and the synchronization applier, which stores a change
     * pushed by the browser extension under the identifier the extension already uses. Ordinary
     * creation paths must leave this alone and let the identifier be generated.</p>
     *
     * @param id the identifier to set
     */
    public void setId(final UUID id) {
        this.id = id;
    }

    /**
     * Returns the creation timestamp.
     *
     * @return when the row was inserted, or {@code null} for a transient entity
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the last modification timestamp.
     *
     * @return when the row was last written, or {@code null} for a transient entity
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
