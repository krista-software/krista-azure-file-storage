/*
 * Azure File Storage Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.krista.extension.azure.files.service;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.ksdk.telemetry.TelemetryMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic telemetry service for operation tracking.
 *
 * This is a REUSABLE TEMPLATE service that can be used by any project to track operations.
 * It encapsulates all telemetry logic:
 * - Metrics recording (counters, histograms)
 * - Attribute building
 * - Duration tracking
 *
 * Follows OpenTelemetry best practices:
 * - LOW-CARDINALITY attributes in metrics (operation, custom attributes)
 * - Configurable metric prefix for different projects/operations
 * - Workspace/invoker context added automatically by telemetry contract
 *
 * Usage Example:
 * <pre>
 * // Start tracking
 * TelemetryContext ctx = telemetryService.recordRequestStart("azure.files", "list_files");
 *
 * // On success
 * telemetryService.recordSuccess(ctx);
 *
 * // On failure
 * telemetryService.recordFailure(ctx, response);
 *
 * // On exception
 * telemetryService.recordException(ctx, exception);
 * </pre>
 *
 * Metrics Generated (where {prefix} is the metricPrefix parameter):
 * - {prefix}.requests - Total requests counter
 * - {prefix}.success - Successful operations counter
 * - {prefix}.failure - Failed operations counter
 * - {prefix}.duration - Operation duration histogram (milliseconds)
 */
@Service
public class AzureFilesTelemetryService {

    private static final Logger LOG = LoggerFactory.getLogger(AzureFilesTelemetryService.class);

    private final TelemetryMetrics telemetryMetrics;

    @Inject
    public AzureFilesTelemetryService(TelemetryMetrics telemetryMetrics) {
        this.telemetryMetrics = telemetryMetrics;
        LOG.debug("AzureFilesTelemetryService initialized");
    }

    /**
     * Records the start of an operation.
     *
     * @param metricPrefix The metric prefix for this operation (e.g., "azure.files")
     * @param operation The operation name (e.g., "list_files", "list_directories", "get_file")
     * @return TelemetryContext containing start time, metric prefix, and attributes
     */
    public TelemetryContext recordRequestStart(String metricPrefix, String operation) {
        long startTime = System.currentTimeMillis();

        // Create metric attributes map (LOW-CARDINALITY ONLY)
        Map<String, String> metricAttributes = new HashMap<>();
        metricAttributes.put("operation", operation);

        // Increment request counter using dynamic metric name
        String requestMetric = metricPrefix + ".requests";
        telemetryMetrics.incrementCounter(requestMetric, 1, metricAttributes);

        return new TelemetryContext(startTime, metricPrefix, metricAttributes);
    }

    /**
     * Records the start of an operation with custom attributes.
     *
     * @param metricPrefix The metric prefix for this operation (e.g., "azure.files")
     * @param operation The operation name (e.g., "list_files", "list_directories", "get_file")
     * @param customAttributes Additional low-cardinality attributes to include in metrics
     * @return TelemetryContext containing start time, metric prefix, and attributes
     */
    public TelemetryContext recordRequestStart(String metricPrefix, String operation, Map<String, String> customAttributes) {
        long startTime = System.currentTimeMillis();

        // Create metric attributes map (LOW-CARDINALITY ONLY)
        Map<String, String> metricAttributes = new HashMap<>();
        metricAttributes.put("operation", operation);

        // Add custom attributes if provided
        if (customAttributes != null) {
            metricAttributes.putAll(customAttributes);
        }

        // Increment request counter using dynamic metric name
        String requestMetric = metricPrefix + ".requests";
        telemetryMetrics.incrementCounter(requestMetric, 1, metricAttributes);

        return new TelemetryContext(startTime, metricPrefix, metricAttributes);
    }

    /**
     * Increments a counter metric directly without creating a TelemetryContext.
     * Use this for simple counting scenarios where request tracking is not needed.
     *
     * @param metricName The full metric name (e.g., "azure.files.health.status")
     * @param attributes The metric attributes
     */
    public void incrementCounter(String metricName, Map<String, String> attributes) {
        telemetryMetrics.incrementCounter(metricName, 1, attributes);
    }

    /**
     * Records a successful operation.
     *
     * @param context The telemetry context from recordRequestStart
     */
    public void recordSuccess(TelemetryContext context) {
        long duration = System.currentTimeMillis() - context.getStartTime();

        // Build dynamic metric names using the prefix from context
        String successMetric = context.getMetricPrefix() + ".success";
        String durationMetric = context.getMetricPrefix() + ".duration";

        // Increment success counter
        telemetryMetrics.incrementCounter(successMetric, 1, context.getMetricAttributes());

        // Record duration histogram
        telemetryMetrics.recordDuration(durationMetric, duration, context.getMetricAttributes());

        LOG.info("Operation completed successfully - Duration: {}ms, Prefix: {}", duration, context.getMetricPrefix());
    }

    /**
     * Records a failed operation.
     *
     * @param context The telemetry context from recordRequestStart
     * @param response The failed ExtensionResponse (may be null)
     */
    public void recordFailure(TelemetryContext context, ExtensionResponse response) {
        long duration = System.currentTimeMillis() - context.getStartTime();

        // Build dynamic metric names using the prefix from context
        String failureMetric = context.getMetricPrefix() + ".failure";
        String durationMetric = context.getMetricPrefix() + ".duration";

        // Increment failure counter
        telemetryMetrics.incrementCounter(failureMetric, 1, context.getMetricAttributes());

        // Record duration histogram
        telemetryMetrics.recordDuration(durationMetric, duration, context.getMetricAttributes());

        LOG.warn("Operation failed - Duration: {}ms, Prefix: {}", duration, context.getMetricPrefix());
    }

    /**
     * Records an exception during operation.
     *
     * @param context The telemetry context from recordRequestStart
     * @param exception The exception that occurred
     */
    public void recordException(TelemetryContext context, Exception exception) {
        long duration = System.currentTimeMillis() - context.getStartTime();

        // Build dynamic metric names using the prefix from context
        String failureMetric = context.getMetricPrefix() + ".failure";
        String durationMetric = context.getMetricPrefix() + ".duration";

        // Increment failure counter
        telemetryMetrics.incrementCounter(failureMetric, 1, context.getMetricAttributes());

        // Record duration histogram
        telemetryMetrics.recordDuration(durationMetric, duration, context.getMetricAttributes());

        LOG.error("Operation failed with exception - Duration: {}ms, Prefix: {}", duration, context.getMetricPrefix(), exception);
    }

    /**
     * Telemetry context holder.
     * Contains start time, metric prefix, and attribute maps for a single operation.
     */
    public static class TelemetryContext {
        private final long startTime;
        private final String metricPrefix;
        private final Map<String, String> metricAttributes;

        public TelemetryContext(long startTime, String metricPrefix, Map<String, String> metricAttributes) {
            this.startTime = startTime;
            this.metricPrefix = metricPrefix;
            this.metricAttributes = metricAttributes;
        }

        public long getStartTime() {
            return startTime;
        }

        public String getMetricPrefix() {
            return metricPrefix;
        }

        public Map<String, String> getMetricAttributes() {
            return metricAttributes;
        }
    }
}
