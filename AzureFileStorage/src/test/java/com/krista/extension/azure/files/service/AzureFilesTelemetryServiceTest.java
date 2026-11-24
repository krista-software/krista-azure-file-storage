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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AzureFilesTelemetryService.
 * Verifies that telemetry metrics are recorded correctly for all operation types.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Azure Files Telemetry Service Tests")
class AzureFilesTelemetryServiceTest {

    @Mock
    private TelemetryMetrics telemetryMetrics;

    @Mock
    private ExtensionResponse extensionResponse;

    private AzureFilesTelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = new AzureFilesTelemetryService(telemetryMetrics);
    }

    // --- recordRequestStart tests ---

    @Test
    @DisplayName("Should record request start and increment request counter")
    void recordRequestStart_shouldIncrementRequestCounter() {
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "list_files");

        assertNotNull(context);
        assertEquals("azure.files", context.getMetricPrefix());
        assertEquals("list_files", context.getMetricAttributes().get("operation"));

        verify(telemetryMetrics).incrementCounter(
                eq("azure.files.requests"),
                eq(1L),
                argThat(attrs -> "list_files".equals(attrs.get("operation")))
        );
    }

    @Test
    @DisplayName("Should set start time on context creation")
    void recordRequestStart_shouldSetStartTime() {
        long before = System.currentTimeMillis();
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "get_file");
        long after = System.currentTimeMillis();

        assertTrue(context.getStartTime() >= before);
        assertTrue(context.getStartTime() <= after);
    }

    @Test
    @DisplayName("Should record request start with custom attributes")
    void recordRequestStart_withCustomAttributes_shouldMergeAttributes() {
        Map<String, String> customAttributes = new HashMap<>();
        customAttributes.put("region", "eastus");

        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "list_directories", customAttributes);

        assertNotNull(context);
        assertEquals("list_directories", context.getMetricAttributes().get("operation"));
        assertEquals("eastus", context.getMetricAttributes().get("region"));

        verify(telemetryMetrics).incrementCounter(
                eq("azure.files.requests"),
                eq(1L),
                argThat(attrs ->
                        "list_directories".equals(attrs.get("operation")) &&
                        "eastus".equals(attrs.get("region"))
                )
        );
    }

    @Test
    @DisplayName("Should handle null custom attributes gracefully")
    void recordRequestStart_withNullCustomAttributes_shouldNotFail() {
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "list_files", null);

        assertNotNull(context);
        assertEquals(1, context.getMetricAttributes().size());
        assertEquals("list_files", context.getMetricAttributes().get("operation"));
    }

    // --- recordSuccess tests ---

    @Test
    @DisplayName("Should record success counter and duration on successful operation")
    void recordSuccess_shouldIncrementSuccessAndRecordDuration() {
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "list_files");

        telemetryService.recordSuccess(context);

        verify(telemetryMetrics).incrementCounter(
                eq("azure.files.success"),
                eq(1L),
                argThat(attrs -> "list_files".equals(attrs.get("operation")))
        );
        verify(telemetryMetrics).recordDuration(
                eq("azure.files.duration"),
                anyLong(),
                argThat(attrs -> "list_files".equals(attrs.get("operation")))
        );
    }

    @Test
    @DisplayName("Should record non-negative duration on success")
    void recordSuccess_shouldRecordNonNegativeDuration() {
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "get_file");

        telemetryService.recordSuccess(context);

        verify(telemetryMetrics).recordDuration(
                eq("azure.files.duration"),
                longThat(duration -> duration >= 0),
                any()
        );
    }

    // --- recordFailure tests ---

    @Test
    @DisplayName("Should record failure counter and duration on failed operation")
    void recordFailure_shouldIncrementFailureAndRecordDuration() {
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "list_directories");

        telemetryService.recordFailure(context, extensionResponse);

        verify(telemetryMetrics).incrementCounter(
                eq("azure.files.failure"),
                eq(1L),
                argThat(attrs -> "list_directories".equals(attrs.get("operation")))
        );
        verify(telemetryMetrics).recordDuration(
                eq("azure.files.duration"),
                anyLong(),
                argThat(attrs -> "list_directories".equals(attrs.get("operation")))
        );
    }

    @Test
    @DisplayName("Should handle null response in recordFailure without error")
    void recordFailure_withNullResponse_shouldNotFail() {
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "get_file");

        assertDoesNotThrow(() -> telemetryService.recordFailure(context, null));

        verify(telemetryMetrics).incrementCounter(eq("azure.files.failure"), eq(1L), any());
    }

    // --- recordException tests ---

    @Test
    @DisplayName("Should record failure counter and duration on exception")
    void recordException_shouldIncrementFailureAndRecordDuration() {
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart("azure.files", "get_file");
        RuntimeException exception = new RuntimeException("Connection timeout");

        telemetryService.recordException(context, exception);

        verify(telemetryMetrics).incrementCounter(
                eq("azure.files.failure"),
                eq(1L),
                argThat(attrs -> "get_file".equals(attrs.get("operation")))
        );
        verify(telemetryMetrics).recordDuration(
                eq("azure.files.duration"),
                anyLong(),
                argThat(attrs -> "get_file".equals(attrs.get("operation")))
        );
    }

    // --- Metric prefix tests ---

    @Test
    @DisplayName("Should use correct metric prefix for all metric names")
    void shouldUseCorrectMetricPrefix() {
        String prefix = "custom.prefix";
        AzureFilesTelemetryService.TelemetryContext context =
                telemetryService.recordRequestStart(prefix, "my_operation");

        telemetryService.recordSuccess(context);

        verify(telemetryMetrics).incrementCounter(eq("custom.prefix.requests"), eq(1L), any());
        verify(telemetryMetrics).incrementCounter(eq("custom.prefix.success"), eq(1L), any());
        verify(telemetryMetrics).recordDuration(eq("custom.prefix.duration"), anyLong(), any());
    }

    // --- TelemetryContext tests ---

    @Test
    @DisplayName("TelemetryContext should preserve all fields")
    void telemetryContext_shouldPreserveAllFields() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("operation", "test_op");
        attrs.put("extra", "value");

        AzureFilesTelemetryService.TelemetryContext context =
                new AzureFilesTelemetryService.TelemetryContext(12345L, "test.prefix", attrs);

        assertEquals(12345L, context.getStartTime());
        assertEquals("test.prefix", context.getMetricPrefix());
        assertEquals("test_op", context.getMetricAttributes().get("operation"));
        assertEquals("value", context.getMetricAttributes().get("extra"));
    }

    // --- Full lifecycle tests ---

    @Test
    @DisplayName("Should track full success lifecycle: start -> success")
    void fullLifecycle_success() {
        AzureFilesTelemetryService.TelemetryContext ctx =
                telemetryService.recordRequestStart("azure.files", "list_files");
        telemetryService.recordSuccess(ctx);

        // Verify: 1 request counter + 1 success counter = 2 incrementCounter calls
        verify(telemetryMetrics, times(2)).incrementCounter(anyString(), eq(1L), any());
        // Verify: 1 duration recording
        verify(telemetryMetrics, times(1)).recordDuration(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Should track full failure lifecycle: start -> failure")
    void fullLifecycle_failure() {
        AzureFilesTelemetryService.TelemetryContext ctx =
                telemetryService.recordRequestStart("azure.files", "list_directories");
        telemetryService.recordFailure(ctx, extensionResponse);

        // Verify: 1 request counter + 1 failure counter = 2 incrementCounter calls
        verify(telemetryMetrics, times(2)).incrementCounter(anyString(), eq(1L), any());
        verify(telemetryMetrics, times(1)).recordDuration(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Should track full exception lifecycle: start -> exception")
    void fullLifecycle_exception() {
        AzureFilesTelemetryService.TelemetryContext ctx =
                telemetryService.recordRequestStart("azure.files", "get_file");
        telemetryService.recordException(ctx, new RuntimeException("test error"));

        // Verify: 1 request counter + 1 failure counter = 2 incrementCounter calls
        verify(telemetryMetrics, times(2)).incrementCounter(anyString(), eq(1L), any());
        verify(telemetryMetrics, times(1)).recordDuration(anyString(), anyLong(), any());
    }
}
