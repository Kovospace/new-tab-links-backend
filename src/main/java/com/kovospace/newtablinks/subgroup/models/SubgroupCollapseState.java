package com.kovospace.newtablinks.subgroup.models;

/**
 * The two independent answers to "is this subgroup folded away".
 *
 * <p>They travel together everywhere a subgroup is created or synchronized, and they are easy to
 * swap by accident when passed as two loose booleans, which is the whole reason they are a type.
 * {@code collapsed} is live state - how the user last left the section. {@code defaultCollapsed}
 * is a property of the section itself - how it starts out on a freshly opened page.</p>
 *
 * @param collapsed        whether the subgroup is folded away right now
 * @param defaultCollapsed whether the subgroup starts folded away on a freshly opened page
 * @since 0.0.6
 */
public record SubgroupCollapseState(boolean collapsed, boolean defaultCollapsed) {
}
