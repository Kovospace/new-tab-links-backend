package com.kovospace.newtablinks.common.config;

import com.kovospace.newtablinks.common.models.FairUseLimit;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The caps of the published Fair Use Policy, bound from {@code newtablinks.fair-use.*}.
 *
 * <p>They apply to every plan - they are what "unlimited" on the paid plan means in practice. The
 * defaults in {@code application.properties} <em>are</em> the policy, so a deployment sets none
 * of them; the variables exist so that a change of policy is a configuration change and so that
 * tests can reach a cap quickly.</p>
 *
 * @param linksPerWorkspace most links one workspace (environment) may hold
 * @param workspaces        most workspaces (environments) one account may hold
 * @param profiles          most profiles one account may hold
 * @param closedTabHistory  most closed-tab history entries one account may hold
 * @since 0.0.16
 */
@ConfigurationProperties(prefix = "newtablinks.fair-use")
public record FairUseLimitProperties(
        int linksPerWorkspace,
        int workspaces,
        int profiles,
        int closedTabHistory) {

    /**
     * Rejects a cap that could not work, at startup rather than on the first refused write.
     *
     * <p>A cap below one would refuse the very first record of its kind, which no policy
     * intends; it is far more likely to be a missing or mistyped variable.</p>
     */
    public FairUseLimitProperties {
        requirePositive(linksPerWorkspace, "links-per-workspace");
        requirePositive(workspaces, "workspaces");
        requirePositive(profiles, "profiles");
        requirePositive(closedTabHistory, "closed-tab-history");
    }

    /**
     * Returns the configured maximum of one cap.
     *
     * @param limit the cap
     * @return the most records of that kind the cap allows
     */
    public int maximumFor(final FairUseLimit limit) {
        return switch (limit) {
            case LINKS_PER_WORKSPACE -> linksPerWorkspace;
            case WORKSPACES -> workspaces;
            case PROFILES -> profiles;
            case CLOSED_TAB_HISTORY -> closedTabHistory;
        };
    }

    /**
     * Fails startup when a cap is not positive.
     *
     * @param maximum      the configured value
     * @param propertyName the property's name below {@code newtablinks.fair-use.}
     * @throws IllegalArgumentException when {@code maximum} is below one
     */
    private static void requirePositive(final int maximum, final String propertyName) {
        if (maximum <= 0) {
            throw new IllegalArgumentException(
                    "newtablinks.fair-use." + propertyName + " must be positive");
        }
    }
}
