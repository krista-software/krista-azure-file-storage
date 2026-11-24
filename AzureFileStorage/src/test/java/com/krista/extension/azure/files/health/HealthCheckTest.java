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

package com.krista.extension.azure.files.health;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import com.krista.extension.azure.files.client.AzureFileRestClient;
import com.krista.extension.azure.files.service.AzureFilesTelemetryService;
import com.krista.extension.azure.files.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HealthCheck service.
 * Covers: healthy, expired, expiring-soon (7 days), expiring-warning (30 days),
 * connection failure, NOT_CONFIGURED, and DateTimeParseException handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Azure Files HealthCheck Tests")
class HealthCheckTest {

    @Mock
    private Invoker invoker;

    @Mock
    private AzureFilesTelemetryService telemetryService;

    private HealthCheck healthCheck;

    private final AzureFilesTelemetryService.TelemetryContext mockContext =
            new AzureFilesTelemetryService.TelemetryContext(
                    System.currentTimeMillis(), "azure.files.health", Map.of("operation", "health_check"));

    @BeforeEach
    void setUp() {
        when(telemetryService.recordRequestStart(anyString(), anyString())).thenReturn(mockContext);
        healthCheck = new HealthCheck(invoker, telemetryService);
    }

    private Map<String, Object> buildAttributes(String sasToken, String sasTokenExpiry) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(Constants.STORAGE_ACCOUNT_NAME, "teststorage");
        attrs.put(Constants.FILE_SHARE_NAME, "testshare");
        attrs.put(Constants.SAS_TOKEN, sasToken);
        if (sasTokenExpiry != null) {
            attrs.put(Constants.SAS_TOKEN_EXPIRY, sasTokenExpiry);
        }
        return attrs;
    }

    private String futureExpiry(int daysFromNow) {
        return ZonedDateTime.now().plusDays(daysFromNow).format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private String pastExpiry(int daysAgo) {
        return ZonedDateTime.now().minusDays(daysAgo).format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private String buildSasToken(String expiry, String start) {
        StringBuilder sb = new StringBuilder("sv=2021-06-08");
        if (expiry != null) {
            sb.append("&se=").append(expiry);
        }
        if (start != null) {
            sb.append("&st=").append(start);
        }
        sb.append("&sp=rl&sig=testsig");
        return sb.toString();
    }

    // ==================== NOT_CONFIGURED ====================

    @Nested
    @DisplayName("NOT_CONFIGURED scenario")
    class NotConfigured {

        @Test
        @DisplayName("Should return NOT_CONFIGURED when no SAS token is provided")
        void checkHealth_noSasToken() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "teststorage");
            attrs.put(Constants.FILE_SHARE_NAME, "testshare");
            // No SAS token
            when(invoker.getAttributes()).thenReturn(attrs);

            ExtensionResponse response = healthCheck.checkHealth();

            assertNotNull(response);
            verify(telemetryService).recordRequestStart("azure.files.health", "health_check");
            verify(telemetryService).recordSuccess(mockContext);
            verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                    "NOT_CONFIGURED".equals(map.get("status"))));
        }

        @Test
        @DisplayName("Should return NOT_CONFIGURED when SAS token is blank")
        void checkHealth_blankSasToken() {
            Map<String, Object> attrs = buildAttributes("   ", null);
            when(invoker.getAttributes()).thenReturn(attrs);

            ExtensionResponse response = healthCheck.checkHealth();

            assertNotNull(response);
            verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                    "NOT_CONFIGURED".equals(map.get("status"))));
        }
    }

    // ==================== HEALTHY scenario ====================

    @Nested
    @DisplayName("HEALTHY scenario")
    class Healthy {

        @Test
        @DisplayName("Should return HEALTHY when token is valid and connection succeeds")
        void checkHealth_healthy() {
            String expiry = futureExpiry(90);
            String sasToken = buildSasToken(expiry, null);
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, expiry));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenReturn(true))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).recordSuccess(mockContext);
                verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                        "HEALTHY".equals(map.get("status"))));
                // Token should be valid — no token_expired counter
                verify(telemetryService, never()).incrementCounter(eq("azure.files.health.token_expired"), anyMap());
            }
        }
    }

    // ==================== EXPIRED token ====================

    @Nested
    @DisplayName("Expired token scenario")
    class ExpiredToken {

        @Test
        @DisplayName("Should return UNHEALTHY when token has expired")
        void checkHealth_expiredToken() {
            String expiry = pastExpiry(5);
            String sasToken = buildSasToken(expiry, null);
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, expiry));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenReturn(true))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).recordSuccess(mockContext);
                verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                        "UNHEALTHY".equals(map.get("status"))));
                verify(telemetryService).incrementCounter(eq("azure.files.health.token_expired"), anyMap());
            }
        }
    }

    // ==================== Expiring within 7 days ====================

    @Nested
    @DisplayName("Token expiring within 7 days")
    class ExpiringWithin7Days {

        @Test
        @DisplayName("Should return UNHEALTHY when token expires in 3 days")
        void checkHealth_expiringIn3Days() {
            String expiry = futureExpiry(3);
            String sasToken = buildSasToken(expiry, null);
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, expiry));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenReturn(true))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                        "UNHEALTHY".equals(map.get("status"))));
            }
        }
    }

    // ==================== Expiring within 30 days ====================

    @Nested
    @DisplayName("Token expiring within 30 days")
    class ExpiringWithin30Days {

        @Test
        @DisplayName("Should return HEALTHY when token expires in 20 days and connection succeeds")
        void checkHealth_expiringIn20Days() {
            String expiry = futureExpiry(20);
            String sasToken = buildSasToken(expiry, null);
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, expiry));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenReturn(true))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                        "HEALTHY".equals(map.get("status"))));
            }
        }
    }

    // ==================== Connection failure ====================

    @Nested
    @DisplayName("Connection failure scenarios")
    class ConnectionFailure {

        @Test
        @DisplayName("Should return UNHEALTHY when connection test returns false")
        void checkHealth_connectionFailed() {
            String expiry = futureExpiry(90);
            String sasToken = buildSasToken(expiry, null);
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, expiry));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenReturn(false))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                        "UNHEALTHY".equals(map.get("status"))));
            }
        }

        @Test
        @DisplayName("Should return THIRD PARTY SYSTEM OFFLINE on network error")
        void checkHealth_networkError() {
            String expiry = futureExpiry(90);
            String sasToken = buildSasToken(expiry, null);
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, expiry));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenThrow(new RuntimeException("connection timeout")))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                        "THIRD PARTY SYSTEM OFFLINE".equals(map.get("status"))));
                verify(telemetryService).incrementCounter(eq("azure.files.health.connection_failed"), argThat(map ->
                        "offline".equals(map.get("reason"))));
            }
        }

        @Test
        @DisplayName("Should return UNHEALTHY on non-network error")
        void checkHealth_nonNetworkError() {
            String expiry = futureExpiry(90);
            String sasToken = buildSasToken(expiry, null);
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, expiry));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenThrow(new RuntimeException("invalid signature")))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).incrementCounter(eq("azure.files.health.status"), argThat(map ->
                        "UNHEALTHY".equals(map.get("status"))));
                // Should NOT record connection_failed for non-network errors
                verify(telemetryService, never()).incrementCounter(eq("azure.files.health.connection_failed"), anyMap());
            }
        }
    }

    // ==================== DateTimeParseException ====================

    @Nested
    @DisplayName("DateTimeParseException handling")
    class DateTimeParsing {

        @Test
        @DisplayName("Should handle unparseable expiry date gracefully")
        void checkHealth_unparseableExpiry() {
            String sasToken = "sv=2021-06-08&se=not-a-date&sp=rl&sig=testsig";
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, "not-a-date"));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenReturn(true))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                // Should not throw — should handle gracefully
                verify(telemetryService).recordSuccess(mockContext);
            }
        }

        @Test
        @DisplayName("Should handle missing expiry parameter in SAS token")
        void checkHealth_noExpiryInToken() {
            String sasToken = "sv=2021-06-08&sp=rl&sig=testsig"; // no 'se' parameter
            when(invoker.getAttributes()).thenReturn(buildAttributes(sasToken, null));

            try (MockedConstruction<AzureFileRestClient> mocked = mockConstruction(AzureFileRestClient.class,
                    (mock, context) -> when(mock.testConnection()).thenReturn(true))) {

                ExtensionResponse response = healthCheck.checkHealth();

                assertNotNull(response);
                verify(telemetryService).recordSuccess(mockContext);
            }
        }
    }

    // ==================== Telemetry ====================

    @Nested
    @DisplayName("Telemetry recording")
    class TelemetryRecording {

        @Test
        @DisplayName("Should always record request start and outcome")
        void checkHealth_telemetryAlwaysRecorded() {
            when(invoker.getAttributes()).thenReturn(buildAttributes("", null));

            ExtensionResponse response = healthCheck.checkHealth();

            assertNotNull(response);
            verify(telemetryService).recordRequestStart("azure.files.health", "health_check");
            verify(telemetryService).recordSuccess(mockContext);
        }

        @Test
        @DisplayName("Should propagate exception when invoker.getAttributes() fails before try block")
        void checkHealth_invokerFailure_throwsException() {
            when(invoker.getAttributes()).thenThrow(new RuntimeException("Invoker failure"));

            assertThrows(RuntimeException.class, () -> healthCheck.checkHealth());
        }
    }
}
