package com.kovospace.newtablinks.user.models;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One word for how much of what an installation holds synchronises, for the devices list.
 *
 * <p>Derived from the installation's last inventory report; example data is ignored, because it
 * never synchronises by design and says nothing about the account's limits.</p>
 *
 * @since 0.0.18
 */
@Schema(description = "How much of what an installation holds synchronises")
public enum DeviceSyncSummary {

    /** Every reported profile and workspace synchronises - or there is nothing but examples. */
    SYNCHRONISED,

    /** Some reported profiles or workspaces synchronise and some stay local only. */
    PARTIAL,

    /** Nothing reported synchronises - every profile and workspace stays local only. */
    NOT_SYNCHRONISED,

    /** The installation has not reported yet, or never will (the website). */
    UNKNOWN
}
