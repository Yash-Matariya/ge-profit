package com.geprofit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every tunable in one typed place, bound from application.yml.
 *
 * <p>Using a record tree rather than scattered {@code @Value} annotations means
 * the settings are validated at startup and discoverable in one file, and
 * nothing reads configuration by string key at runtime.
 */
@ConfigurationProperties(prefix = "geprofit")
public record GeProfitProperties(Wiki wiki, Ingest ingest, Tax tax, Items items) {

    public record Wiki(String baseUrl, String userAgent, int timeoutSeconds) {
    }

    public record Ingest(boolean enabled,
                         long latestIntervalMs,
                         long historyIntervalMs,
                         long mappingIntervalMs) {
    }

    /** See {@link com.geprofit.tax.GeTax} for why these are settings. */
    public record Tax(double rate, long cap, int exemptBelow) {
    }

    /** Item ids the processing calculators need to look up by name-independent id. */
    public record Items(int natureRune, int vialOfWater) {
    }
}
