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

package com.krista.extension.azure.files.catalog;

import app.krista.extension.executor.ExtensionResponse;
import com.krista.extension.azure.files.health.HealthCheck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SetupArea.
 * Tests constructor injection and healthCheck delegation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SetupArea Tests")
class SetupAreaTest {

    private static final Logger logger = LoggerFactory.getLogger(SetupAreaTest.class);

    @Mock
    private HealthCheck healthCheck;

    private SetupArea setupArea;

    @BeforeEach
    void setUp() {
        setupArea = new SetupArea(healthCheck);
    }

    // ==================== Constructor ====================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create SetupArea with injected HealthCheck")
        void shouldCreateSetupAreaWithHealthCheck() {
            SetupArea area = new SetupArea(healthCheck);
            assertNotNull(area);
            logger.info("SetupArea created successfully with injected HealthCheck");
        }
    }

    // ==================== Health Check ====================

    @Nested
    @DisplayName("Health Check")
    class HealthCheckTests {

        @Test
        @DisplayName("Should delegate to HealthCheck.checkHealth() and return its response")
        void shouldDelegateToHealthCheckAndReturnResponse() {
            ExtensionResponse expectedResponse = ExtensionResponseFactory.create(
                    Map.of("Is Healthy", true));
            when(healthCheck.checkHealth()).thenReturn(expectedResponse);

            ExtensionResponse result = setupArea.healthCheck();

            assertNotNull(result);
            assertSame(expectedResponse, result);
            verify(healthCheck, times(1)).checkHealth();
            logger.info("healthCheck() correctly delegated to HealthCheck.checkHealth()");
        }

        @Test
        @DisplayName("Should return unhealthy response when HealthCheck reports unhealthy")
        void shouldReturnUnhealthyResponse() {
            ExtensionResponse unhealthyResponse = ExtensionResponseFactory.create(
                    Map.of("Is Healthy", false));
            when(healthCheck.checkHealth()).thenReturn(unhealthyResponse);

            ExtensionResponse result = setupArea.healthCheck();

            assertNotNull(result);
            assertSame(unhealthyResponse, result);
            verify(healthCheck).checkHealth();
            logger.info("healthCheck() returned unhealthy response as expected");
        }

        @Test
        @DisplayName("Should propagate exception when HealthCheck throws")
        void shouldPropagateException_whenHealthCheckThrows() {
            when(healthCheck.checkHealth()).thenThrow(new RuntimeException("Health check failed"));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> setupArea.healthCheck());
            assertEquals("Health check failed", ex.getMessage());
            verify(healthCheck).checkHealth();
            logger.info("Exception propagated correctly: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should invoke HealthCheck exactly once per call")
        void shouldInvokeHealthCheckExactlyOnce() {
            ExtensionResponse response = ExtensionResponseFactory.create(
                    Map.of("Is Healthy", true));
            when(healthCheck.checkHealth()).thenReturn(response);

            setupArea.healthCheck();

            verify(healthCheck, times(1)).checkHealth();
            verifyNoMoreInteractions(healthCheck);
            logger.info("HealthCheck invoked exactly once with no extra interactions");
        }
    }
}
