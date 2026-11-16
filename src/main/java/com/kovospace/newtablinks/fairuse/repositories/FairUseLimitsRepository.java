package com.kovospace.newtablinks.fairuse.repositories;

import com.kovospace.newtablinks.fairuse.models.FairUseLimitsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the one row of global Fair Use limits ({@link FairUseLimitsEntity#SINGLE_ROW_ID}).
 *
 * @since 0.0.20
 */
public interface FairUseLimitsRepository extends JpaRepository<FairUseLimitsEntity, Short> {
}
