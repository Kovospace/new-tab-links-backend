package com.kovospace.newtablinks.common.config;

import com.kovospace.newtablinks.common.models.FreePlanLimit;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The limits of the free plan, bound from {@code newtablinks.plan-limits.free.*}.
 *
 * <p>They apply only to an account that is not premium right now; a premium account is held to
 * the Fair Use Policy caps instead ({@link FairUseLimitProperties}). The defaults in
 * {@code application.properties} are what the website advertises, so a deployment sets none of
 * them; the variables exist so that a change of plan is a configuration change and so that tests
 * can reach a limit quickly.</p>
 *
 * @param workspaces most workspaces (environments) a free account may hold
 * @param profiles   most profiles a free account may hold
 * @param devices    most synchronised installations a free account may hold
 * @since 0.0.17
 */
@ConfigurationProperties(prefix = "newtablinks.plan-limits.free")
public record FreePlanLimitProperties(
        int workspaces,
        int profiles,
        int devices) {

    /**
     * Rejects a limit that could not work, at startup rather than on the first refused write.
     *
     * <p>A limit below one would refuse a free account its very first profile, workspace or
     * installation, which no plan intends; it is far more likely to be a mistyped variable.</p>
     */
    public FreePlanLimitProperties {
        requirePositive(workspaces, "workspaces");
        requirePositive(profiles, "profiles");
        requirePositive(devices, "devices");
    }

    /**
     * Returns the configured maximum of one limit.
     *
     * @param limit the limit
     * @return the most records of that kind a free account may hold
     */
    public int maximumFor(final FreePlanLimit limit) {
        return switch (limit) {
            case WORKSPACES -> workspaces;
            case PROFILES -> profiles;
            case DEVICES -> devices;
        };
    }

    /**
     * Fails startup when a limit is not positive.
     *
     * @param maximum      the configured value
     * @param propertyName the property's name below {@code newtablinks.plan-limits.free.}
     * @throws IllegalArgumentException when {@code maximum} is below one
     */
    private static void requirePositive(final int maximum, final String propertyName) {
        if (maximum <= 0) {
            throw new IllegalArgumentException(
                    "newtablinks.plan-limits.free." + propertyName + " must be positive");
        }
    }
}
