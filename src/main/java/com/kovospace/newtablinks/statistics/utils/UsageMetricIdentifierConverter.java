package com.kovospace.newtablinks.statistics.utils;

import com.kovospace.newtablinks.statistics.models.UsageMetric;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Reads a request parameter such as {@code metric=new_tabs} as a {@link UsageMetric}.
 *
 * <p>Needed because the API names metrics by their stored identifier, not by the enum constant.
 * Registered with Spring MVC by being a bean. An identifier naming no metric fails the conversion;
 * Spring then still tries the constant's own name (so {@code NEW_TABS} is accepted too) before
 * answering 400.</p>
 *
 * @since 0.0.11
 */
@Component
public class UsageMetricIdentifierConverter implements Converter<String, UsageMetric> {

    /**
     * Converts an identifier to its metric.
     *
     * @param identifier the identifier as sent
     * @return the metric it names
     * @throws IllegalArgumentException when it names no metric
     */
    @Override
    public UsageMetric convert(final String identifier) {
        return UsageMetric.fromIdentifier(identifier)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No usage metric is named " + identifier));
    }
}
