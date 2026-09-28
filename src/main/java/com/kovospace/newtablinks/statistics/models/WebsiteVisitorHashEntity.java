package com.kovospace.newtablinks.statistics.models;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Marks that one website visitor has already been counted today.
 *
 * <p>Nothing here can be reversed to a visitor: the key is a keyed hash of the address, the
 * browser and the day, and rows before today are deleted nightly, so a hash is never matched
 * across days. Like {@link DailyMetricEntity} it does not extend
 * {@code AbstractAuditableEntity}, because the table has none of its columns, and it is written
 * only by native SQL in
 * {@link com.kovospace.newtablinks.statistics.repositories.WebsiteVisitorHashRepository}; the
 * class exists so that {@code ddl-auto=validate} checks the table.</p>
 *
 * @since 0.0.11
 */
@Entity
@Table(name = "website_visitor_hash")
public class WebsiteVisitorHashEntity {

    /** The day and the visitor's hash for it. */
    @EmbeddedId
    private WebsiteVisitorHashKey key;

    /**
     * Required by JPA.
     */
    protected WebsiteVisitorHashEntity() {
    }
}
