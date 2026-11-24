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

package com.krista.extension.azure.files;

import app.krista.extension.executor.Invoker;
import com.krista.extension.azure.files.client.AzureFileRestClient;
import com.krista.extension.azure.files.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AzureFilesExtension.
 * Tests constructor injection, customTabs, validateAttribute, and testConnection methods.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AzureFilesExtension Tests")
class AzureFilesExtensionTest {

    private static final Logger logger = LoggerFactory.getLogger(AzureFilesExtensionTest.class);

    private static final String VALID_SAS_TOKEN =
            "sv=2024-11-04&ss=f&srt=sco&sp=rwdlc&se=2030-07-01T03:49:20Z&st=2025-06-01T19:49:20Z&spr=https&sig=testSignature";

    private static final String VALID_EXPIRY = "2030-07-01T03:49:20Z";

    @Mock
    private Invoker invoker;

    private AzureFilesExtension extension;

    @BeforeEach
    void setUp() {
        extension = new AzureFilesExtension(invoker);
    }

    private Map<String, Object> buildAttributes(String storageAccount, String fileShare,
                                                 String sasToken, String sasTokenExpiry) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(Constants.STORAGE_ACCOUNT_NAME, storageAccount);
        attrs.put(Constants.FILE_SHARE_NAME, fileShare);
        attrs.put(Constants.SAS_TOKEN, sasToken);
        if (sasTokenExpiry != null) {
            attrs.put(Constants.SAS_TOKEN_EXPIRY, sasTokenExpiry);
        }
        return attrs;
    }

    private Map<String, Object> buildValidAttributes() {
        return buildAttributes("teststorage", "testshare", VALID_SAS_TOKEN, VALID_EXPIRY);
    }

    private Map<String, Object> buildValidAttributesWithoutExpiry() {
        return buildAttributes("teststorage", "testshare", VALID_SAS_TOKEN, null);
    }

    private String futureExpiry(int daysFromNow) {
        return ZonedDateTime.now().plusDays(daysFromNow)
                .format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private String pastExpiry(int daysAgo) {
        return ZonedDateTime.now().minusDays(daysAgo)
                .format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private String buildSasToken(String expiry, String start) {
        return "sv=2024-11-04&ss=f&srt=sco&sp=rwdlc&se=" + expiry + "&st=" + start + "&spr=https&sig=testSig";
    }

    // ==================== Constructor ====================

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create extension with injected invoker")
        void shouldCreateExtensionWithInvoker() {
            AzureFilesExtension ext = new AzureFilesExtension(invoker);
            assertNotNull(ext);
            logger.info("Extension created successfully with injected invoker");
        }
    }

    // ==================== Custom Tabs ====================

    @Nested
    @DisplayName("Custom Tabs")
    class CustomTabsTests {

        @Test
        @DisplayName("Should return documentation tab")
        void shouldReturnDocumentationTab() {
            Map<String, String> tabs = extension.customTabs();

            assertNotNull(tabs);
            assertEquals(1, tabs.size());
            assertTrue(tabs.containsKey("Documentation"));
            assertEquals("static/docs", tabs.get("Documentation"));
            logger.info("Custom tabs returned correctly: {}", tabs);
        }
    }

    // ==================== Validate Attribute ====================

    @Nested
    @DisplayName("Validate Attribute")
    class ValidateAttributeTests {

        @Test
        @DisplayName("Should validate successfully with valid attributes and successful connection")
        void shouldValidateSuccessfully_whenAttributesValidAndConnectionSucceeds() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> mockedClient =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenReturn(true))) {

                assertDoesNotThrow(() -> extension.validateAttribute(attrs));
                assertEquals(1, mockedClient.constructed().size());
                verify(mockedClient.constructed().get(0)).testConnection();
                logger.info("validateAttribute passed with valid attributes");
            }
        }

        @Test
        @DisplayName("Should auto-populate SAS Token Expiry when not provided by user")
        void shouldAutoPopulateExpiry_whenNotProvidedByUser() {
            Map<String, Object> attrs = buildValidAttributesWithoutExpiry();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> ignored =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenReturn(true))) {

                extension.validateAttribute(attrs);

                // After validation, the expiry should be auto-populated from the token's 'se' param
                assertTrue(attrs.containsKey(Constants.SAS_TOKEN_EXPIRY));
                assertEquals(VALID_EXPIRY, attrs.get(Constants.SAS_TOKEN_EXPIRY));
                logger.info("SAS Token Expiry auto-populated: {}", attrs.get(Constants.SAS_TOKEN_EXPIRY));
            }
        }

        @Test
        @DisplayName("Should not overwrite user-provided SAS Token Expiry")
        void shouldNotOverwriteExpiry_whenProvidedByUser() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> ignored =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenReturn(true))) {

                extension.validateAttribute(attrs);
                assertEquals(VALID_EXPIRY, attrs.get(Constants.SAS_TOKEN_EXPIRY));
                logger.info("User-provided expiry preserved: {}", attrs.get(Constants.SAS_TOKEN_EXPIRY));
            }
        }

        @Test
        @DisplayName("Should throw RuntimeException when connection test fails")
        void shouldThrowException_whenConnectionTestFails() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> ignored =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenReturn(false))) {

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> extension.validateAttribute(attrs));
                assertTrue(ex.getMessage().contains("Connection test failed"));
                logger.info("Connection failure correctly threw exception: {}", ex.getMessage());
            }
        }

        @Test
        @DisplayName("Should throw RuntimeException when storage account name is empty")
        void shouldThrowException_whenStorageAccountNameEmpty() {
            Map<String, Object> attrs = buildAttributes("", "testshare", VALID_SAS_TOKEN, VALID_EXPIRY);
            when(invoker.getAttributes()).thenReturn(attrs);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extension.validateAttribute(attrs));
            assertTrue(ex.getMessage().contains("Storage Account Name is required"));
            logger.info("Empty storage account name correctly threw exception: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw RuntimeException when file share name is empty")
        void shouldThrowException_whenFileShareNameEmpty() {
            Map<String, Object> attrs = buildAttributes("teststorage", "", VALID_SAS_TOKEN, VALID_EXPIRY);
            when(invoker.getAttributes()).thenReturn(attrs);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extension.validateAttribute(attrs));
            assertTrue(ex.getMessage().contains("File Share Name is required"));
            logger.info("Empty file share name correctly threw exception: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw RuntimeException when SAS token is empty")
        void shouldThrowException_whenSasTokenEmpty() {
            Map<String, Object> attrs = buildAttributes("teststorage", "testshare", "", VALID_EXPIRY);
            when(invoker.getAttributes()).thenReturn(attrs);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extension.validateAttribute(attrs));
            assertTrue(ex.getMessage().contains("SAS Token is required"));
            logger.info("Empty SAS token correctly threw exception: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw RuntimeException when SAS token has expired")
        void shouldThrowException_whenSasTokenExpired() {
            String expiredExpiry = pastExpiry(10);
            String expiredStart = pastExpiry(20);
            String expiredToken = buildSasToken(expiredExpiry, expiredStart);
            Map<String, Object> attrs = buildAttributes("teststorage", "testshare", expiredToken, null);
            when(invoker.getAttributes()).thenReturn(attrs);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extension.validateAttribute(attrs));
            assertTrue(ex.getMessage().contains("expired"));
            logger.info("Expired SAS token correctly threw exception: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw RuntimeException when user-provided expiry mismatches token expiry")
        void shouldThrowException_whenExpiryMismatch() {
            String mismatchExpiry = "2031-01-01T00:00:00Z";
            Map<String, Object> attrs = buildAttributes("teststorage", "testshare", VALID_SAS_TOKEN, mismatchExpiry);
            when(invoker.getAttributes()).thenReturn(attrs);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extension.validateAttribute(attrs));
            assertTrue(ex.getMessage().contains("does not match"));
            logger.info("Expiry mismatch correctly threw exception: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should wrap unexpected exception from REST client in RuntimeException")
        void shouldWrapUnexpectedException_fromRestClient() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> ignored =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenThrow(new RuntimeException("Network error")))) {

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> extension.validateAttribute(attrs));
                assertTrue(ex.getMessage().contains("Connection test failed"));
                assertTrue(ex.getMessage().contains("Network error"));
                logger.info("Unexpected exception wrapped correctly: {}", ex.getMessage());
            }
        }
    }

    // ==================== Test Connection ====================

    @Nested
    @DisplayName("Test Connection")
    class TestConnectionTests {

        @Test
        @DisplayName("Should test connection successfully with valid attributes")
        void shouldTestConnectionSuccessfully() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> mockedClient =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenReturn(true))) {

                assertDoesNotThrow(() -> extension.testConnection());
                assertEquals(1, mockedClient.constructed().size());
                verify(mockedClient.constructed().get(0)).testConnection();
                logger.info("testConnection passed successfully");
            }
        }

        @Test
        @DisplayName("Should throw RuntimeException when test connection fails")
        void shouldThrowException_whenTestConnectionFails() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> ignored =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenReturn(false))) {

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> extension.testConnection());
                assertTrue(ex.getMessage().contains("Connection test failed"));
                logger.info("testConnection failure correctly threw exception: {}", ex.getMessage());
            }
        }

        @Test
        @DisplayName("Should throw RuntimeException when attributes are missing for testConnection")
        void shouldThrowException_whenAttributesMissingForTestConnection() {
            Map<String, Object> attrs = buildAttributes("", "", "", null);
            when(invoker.getAttributes()).thenReturn(attrs);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extension.testConnection());
            assertTrue(ex.getMessage().contains("Storage Account Name is required"));
            logger.info("Missing attributes for testConnection correctly threw exception: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should use invoker.getAttributes() for test connection (not injected field)")
        void shouldUseInvokerGetAttributes_forTestConnection() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> ignored =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenReturn(true))) {

                extension.testConnection();
                verify(invoker).getAttributes();
                logger.info("testConnection correctly used invoker.getAttributes()");
            }
        }

        @Test
        @DisplayName("Should throw when REST client throws an unexpected exception during testConnection")
        void shouldThrowException_whenRestClientThrowsDuringTestConnection() {
            Map<String, Object> attrs = buildValidAttributes();
            when(invoker.getAttributes()).thenReturn(attrs);

            try (MockedConstruction<AzureFileRestClient> ignored =
                         mockConstruction(AzureFileRestClient.class, (mock, context) ->
                                 when(mock.testConnection()).thenThrow(new RuntimeException("Timeout")))) {

                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> extension.testConnection());
                assertTrue(ex.getMessage().contains("Connection test failed"));
                assertTrue(ex.getMessage().contains("Timeout"));
                logger.info("REST client exception during testConnection correctly handled: {}", ex.getMessage());
            }
        }

        @Test
        @DisplayName("Should throw when SAS token is expired during testConnection")
        void shouldThrowException_whenSasTokenExpiredDuringTestConnection() {
            String expiredExpiry = pastExpiry(5);
            String expiredStart = pastExpiry(15);
            String expiredToken = buildSasToken(expiredExpiry, expiredStart);
            Map<String, Object> attrs = buildAttributes("teststorage", "testshare", expiredToken, null);
            when(invoker.getAttributes()).thenReturn(attrs);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> extension.testConnection());
            assertTrue(ex.getMessage().contains("expired"));
            logger.info("Expired SAS token during testConnection correctly threw exception: {}", ex.getMessage());
        }
    }
}
