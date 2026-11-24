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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ExtensionResponseFactory.
 * Tests the static factory method and utility class design.
 */
@DisplayName("ExtensionResponseFactory Tests")
class ExtensionResponseFactoryTest {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionResponseFactoryTest.class);

    // ==================== Create Method ====================

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("Should create ExtensionResponse with SUCCESS result")
        void shouldCreateResponseWithSuccessResult() {
            Map<String, Object> values = Map.of("key", "value");

            ExtensionResponse response = ExtensionResponseFactory.create(values);

            assertNotNull(response);
            assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
            logger.info("Created response with result: {}", response.getResult());
        }

        @Test
        @DisplayName("Should set response values from provided map")
        void shouldSetResponseValuesFromMap() {
            Map<String, Object> values = Map.of(
                    "Is Healthy", true,
                    "Health Status", "HEALTHY"
            );

            ExtensionResponse response = ExtensionResponseFactory.create(values);

            assertNotNull(response.getResponseValue());
            assertEquals(true, response.getResponseValue().get("Is Healthy"));
            assertEquals("HEALTHY", response.getResponseValue().get("Health Status"));
            logger.info("Response values set correctly: {}", response.getResponseValue());
        }

        @Test
        @DisplayName("Should set error to null")
        void shouldSetErrorToNull() {
            ExtensionResponse response = ExtensionResponseFactory.create(Map.of());

            assertNull(response.getError());
            logger.info("Error is null as expected");
        }

        @Test
        @DisplayName("Should set remediationActions to null")
        void shouldSetRemediationActionsToNull() {
            ExtensionResponse response = ExtensionResponseFactory.create(Map.of());

            assertNull(response.getRemediationActions());
            logger.info("RemediationActions is null as expected");
        }

        @Test
        @DisplayName("Should set state to null")
        void shouldSetStateToNull() {
            ExtensionResponse response = ExtensionResponseFactory.create(Map.of());

            assertNull(response.getState());
            logger.info("State is null as expected");
        }

        @Test
        @DisplayName("Should handle empty map")
        void shouldHandleEmptyMap() {
            ExtensionResponse response = ExtensionResponseFactory.create(Map.of());

            assertNotNull(response);
            assertEquals(ExtensionResponse.Result.SUCCESS, response.getResult());
            assertNotNull(response.getResponseValue());
            assertTrue(response.getResponseValue().isEmpty());
            logger.info("Empty map handled correctly");
        }

        @Test
        @DisplayName("Should handle map with multiple entries")
        void shouldHandleMapWithMultipleEntries() {
            Map<String, Object> values = new HashMap<>();
            values.put("stringKey", "stringValue");
            values.put("boolKey", false);
            values.put("numKey", 42);
            values.put("nullKey", null);

            ExtensionResponse response = ExtensionResponseFactory.create(values);

            assertNotNull(response);
            assertEquals(4, response.getResponseValue().size());
            assertEquals("stringValue", response.getResponseValue().get("stringKey"));
            assertEquals(false, response.getResponseValue().get("boolKey"));
            assertEquals(42, response.getResponseValue().get("numKey"));
            assertNull(response.getResponseValue().get("nullKey"));
            logger.info("Map with multiple entry types handled correctly");
        }
    }

    // ==================== Utility Class Design ====================

    @Nested
    @DisplayName("Utility Class Design")
    class UtilityClassTests {

        @Test
        @DisplayName("Should have private constructor that prevents instantiation")
        void shouldPreventInstantiation() throws NoSuchMethodException {
            Constructor<ExtensionResponseFactory> constructor =
                    ExtensionResponseFactory.class.getDeclaredConstructor();
            assertFalse(constructor.canAccess(null), "Constructor should not be publicly accessible");
            logger.info("Private constructor verified — not accessible by default");
        }
    }
}
