package com.kovospace.newtablinks.user.dtos;

/**
 * How large an inventory report may be - it comes from a client and is stored as sent, so it is
 * bounded before it is accepted.
 *
 * <p>The bounds are deliberately well above anything the plans allow, because local-only data is
 * exactly what exceeds them: an installation that was premium may hold 50 profiles of 50
 * workspaces. They exist to stop an abusive or broken client from storing megabytes, not to judge
 * an honest one.</p>
 *
 * @since 0.0.18
 */
public final class DeviceInventoryBounds {

    /** Most profiles one report may list. */
    public static final int MAXIMUM_PROFILES = 200;

    /** Most workspaces one profile of a report may list. */
    public static final int MAXIMUM_WORKSPACES_PER_PROFILE = 200;

    /** Most workspaces one report may list across all of its profiles. */
    public static final int MAXIMUM_WORKSPACES = 5000;

    /** Longest name, matching the profile and environment name columns. */
    public static final int MAXIMUM_NAME_LENGTH = 120;

    /** Largest group, subgroup or link count of one workspace. */
    public static final int MAXIMUM_COUNT = 1_000_000;

    /**
     * Not instantiable; constants only.
     */
    private DeviceInventoryBounds() {
    }
}
