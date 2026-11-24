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

import com.krista.extension.azure.files.krista.AzureFilesSettings;
import com.krista.extension.azure.files.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AzureFilesAttributes.
 * Tests attribute initialization, SAS token parsing, validation, and update behavior.
 */
@DisplayName("AzureFilesAttributes Integration Tests")
class AzureFilesAttributesTest {

    private static final Logger logger = LoggerFactory.getLogger(AzureFilesAttributesTest.class);

    private static final String VALID_SAS_TOKEN =
            "sv=2024-11-04&ss=f&srt=sco&sp=rwdlc&se=2030-07-01T03:49:20Z&st=2025-06-01T19:49:20Z&spr=https&sig=testSignature";

    private static final String VALID_EXPIRY = "2030-07-01T03:49:20Z";
    private static final String VALID_START = "2025-06-01T19:49:20Z";

    // ==================== Constructor & Initialization ====================

    @Nested
    @DisplayName("Constructor and Initialization")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize all attributes from map")
        void shouldInitializeAllAttributesFromMap() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            attrs.put(Constants.SAS_TOKEN_EXPIRY, VALID_EXPIRY);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertEquals("myaccount", attributes.getStorageAccountName());
            assertEquals("myshare", attributes.getFileShareName());
            assertEquals(VALID_SAS_TOKEN, attributes.getSasToken());
            assertEquals(VALID_EXPIRY, attributes.getSasTokenExpiry());
            logger.info("All attributes initialized correctly from map");
        }

        @Test
        @DisplayName("Should initialize with AzureFilesSettings values")
        void shouldInitializeWithAzureFilesSettings() {
            AzureFilesSettings settings = new AzureFilesSettings();
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, settings.getStorageAccountName());
            attrs.put(Constants.FILE_SHARE_NAME, settings.getFileShareName());
            attrs.put(Constants.SAS_TOKEN, settings.getSasToken());

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertEquals(settings.getStorageAccountName(), attributes.getStorageAccountName());
            assertEquals(settings.getFileShareName(), attributes.getFileShareName());
            assertEquals(settings.getSasToken(), attributes.getSasToken());
            assertTrue(attributes.hasSasToken());
            logger.info("Attributes initialized correctly from AzureFilesSettings");
        }

        @Test
        @DisplayName("Should use empty defaults when attributes are missing")
        void shouldUseEmptyDefaultsWhenMissing() {
            Map<String, Object> attrs = new HashMap<>();

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertEquals("", attributes.getStorageAccountName());
            assertEquals("", attributes.getFileShareName());
            assertEquals("", attributes.getSasToken());
            assertEquals("", attributes.getSasTokenExpiry());
            assertFalse(attributes.hasSasToken());
            assertFalse(attributes.hasSasTokenExpiry());
            logger.info("Empty defaults applied correctly for missing attributes");
        }

        @Test
        @DisplayName("Should handle null values in attribute map")
        void shouldHandleNullValuesInMap() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, null);
            attrs.put(Constants.FILE_SHARE_NAME, null);
            attrs.put(Constants.SAS_TOKEN, null);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertEquals("", attributes.getStorageAccountName());
            assertEquals("", attributes.getFileShareName());
            assertEquals("", attributes.getSasToken());
            assertFalse(attributes.hasSasToken());
            logger.info("Null values handled correctly, defaults applied");
        }

        @Test
        @DisplayName("Should auto-populate expiry from SAS token when expiry field is empty")
        void shouldAutoPopulateExpiryFromSasToken() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            // SAS_TOKEN_EXPIRY not set - should auto-populate

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertTrue(attributes.hasSasTokenExpiry());
            assertEquals(VALID_EXPIRY, attributes.getSasTokenExpiry());
            logger.info("Auto-populated SAS Token Expiry: {}", attributes.getSasTokenExpiry());
        }

        @Test
        @DisplayName("Should not auto-populate expiry when user provides expiry")
        void shouldNotAutoPopulateWhenUserProvidesExpiry() {
            String userExpiry = "2030-07-01T03:49:20Z";
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            attrs.put(Constants.SAS_TOKEN_EXPIRY, userExpiry);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertEquals(userExpiry, attributes.getSasTokenExpiry());
            logger.info("User-provided expiry preserved: {}", attributes.getSasTokenExpiry());
        }
    }

    // ==================== SAS Token Parsing ====================

    @Nested
    @DisplayName("SAS Token Parsing")
    class SasTokenParsingTests {

        @Test
        @DisplayName("Should parse expiry (se) parameter from SAS token")
        void shouldParseExpiryFromSasToken() {
            String expiry = AzureFilesAttributes.parseSasTokenExpiry(VALID_SAS_TOKEN);

            assertEquals(VALID_EXPIRY, expiry);
            logger.info("Parsed SAS Token Expiry: {}", expiry);
        }

        @Test
        @DisplayName("Should parse start (st) parameter from SAS token")
        void shouldParseStartFromSasToken() {
            String start = AzureFilesAttributes.parseSasTokenStart(VALID_SAS_TOKEN);

            assertEquals(VALID_START, start);
            logger.info("Parsed SAS Token Start: {}", start);
        }

        @Test
        @DisplayName("Should handle SAS token with leading question mark")
        void shouldHandleTokenWithLeadingQuestionMark() {
            String tokenWithQuestionMark = "?" + VALID_SAS_TOKEN;

            String expiry = AzureFilesAttributes.parseSasTokenExpiry(tokenWithQuestionMark);
            String start = AzureFilesAttributes.parseSasTokenStart(tokenWithQuestionMark);

            assertEquals(VALID_EXPIRY, expiry);
            assertEquals(VALID_START, start);
            logger.info("Token with leading '?' parsed correctly");
        }

        @Test
        @DisplayName("Should handle URL-encoded SAS token expiry")
        void shouldHandleUrlEncodedExpiry() {
            // URL-encoded colon (%3A) in the date
            String encodedToken = "sv=2024-11-04&se=2030-07-01T03%3A49%3A20Z&st=2025-06-01T19%3A49%3A20Z&sig=test";

            String expiry = AzureFilesAttributes.parseSasTokenExpiry(encodedToken);

            assertEquals(VALID_EXPIRY, expiry);
            logger.info("URL-encoded token expiry decoded correctly: {}", expiry);
        }

        @Test
        @DisplayName("Should return empty string for null SAS token")
        void shouldReturnEmptyForNullToken() {
            assertEquals("", AzureFilesAttributes.parseSasTokenExpiry(null));
            assertEquals("", AzureFilesAttributes.parseSasTokenStart(null));
        }

        @Test
        @DisplayName("Should return empty string for empty SAS token")
        void shouldReturnEmptyForEmptyToken() {
            assertEquals("", AzureFilesAttributes.parseSasTokenExpiry(""));
            assertEquals("", AzureFilesAttributes.parseSasTokenStart(""));
        }

        @Test
        @DisplayName("Should return empty string for whitespace-only SAS token")
        void shouldReturnEmptyForWhitespaceToken() {
            assertEquals("", AzureFilesAttributes.parseSasTokenExpiry("   "));
            assertEquals("", AzureFilesAttributes.parseSasTokenStart("   "));
        }

        @Test
        @DisplayName("Should return empty string when se parameter is missing")
        void shouldReturnEmptyWhenSeParameterMissing() {
            String tokenWithoutSe = "sv=2024-11-04&ss=f&srt=sco&sp=rwdlc&sig=test";

            assertEquals("", AzureFilesAttributes.parseSasTokenExpiry(tokenWithoutSe));
            logger.info("Correctly returned empty for token without 'se' parameter");
        }

        @Test
        @DisplayName("Should return empty string when st parameter is missing")
        void shouldReturnEmptyWhenStParameterMissing() {
            String tokenWithoutSt = "sv=2024-11-04&ss=f&se=2030-07-01T03:49:20Z&sig=test";

            assertEquals("", AzureFilesAttributes.parseSasTokenStart(tokenWithoutSt));
            logger.info("Correctly returned empty for token without 'st' parameter");
        }
    }

    // ==================== hasSasToken & hasSasTokenExpiry ====================

    @Nested
    @DisplayName("Has Token / Has Expiry Checks")
    class HasTokenTests {

        @Test
        @DisplayName("hasSasToken should return true when token is present")
        void hasSasToken_returnsTrue() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertTrue(attributes.hasSasToken());
        }

        @Test
        @DisplayName("hasSasToken should return false for empty token")
        void hasSasToken_returnsFalseForEmpty() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, "");

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertFalse(attributes.hasSasToken());
        }

        @Test
        @DisplayName("hasSasToken should return false for whitespace-only token")
        void hasSasToken_returnsFalseForWhitespace() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, "   ");

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertFalse(attributes.hasSasToken());
        }

        @Test
        @DisplayName("hasSasTokenExpiry should return true when expiry is auto-populated")
        void hasSasTokenExpiry_returnsTrueWhenAutoPopulated() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            // No explicit expiry - should auto-populate from token

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertTrue(attributes.hasSasTokenExpiry());
        }

        @Test
        @DisplayName("hasSasTokenExpiry should return false when no token and no expiry")
        void hasSasTokenExpiry_returnsFalseWhenNoTokenOrExpiry() {
            Map<String, Object> attrs = new HashMap<>();

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertFalse(attributes.hasSasTokenExpiry());
        }
    }

    // ==================== Update ====================

    @Nested
    @DisplayName("Update Attributes")
    class UpdateTests {

        @Test
        @DisplayName("Should update all attributes when update is called")
        void shouldUpdateAllAttributes() {
            Map<String, Object> initialAttrs = new HashMap<>();
            initialAttrs.put(Constants.STORAGE_ACCOUNT_NAME, "oldaccount");
            initialAttrs.put(Constants.FILE_SHARE_NAME, "oldshare");
            initialAttrs.put(Constants.SAS_TOKEN, "oldtoken");

            AzureFilesAttributes attributes = new AzureFilesAttributes(initialAttrs);
            assertEquals("oldaccount", attributes.getStorageAccountName());

            Map<String, Object> newAttrs = new HashMap<>();
            newAttrs.put(Constants.STORAGE_ACCOUNT_NAME, "newaccount");
            newAttrs.put(Constants.FILE_SHARE_NAME, "newshare");
            newAttrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            attributes.update(newAttrs);

            assertEquals("newaccount", attributes.getStorageAccountName());
            assertEquals("newshare", attributes.getFileShareName());
            assertEquals(VALID_SAS_TOKEN, attributes.getSasToken());
            assertTrue(attributes.hasSasToken());
            logger.info("Attributes updated successfully");
        }

        @Test
        @DisplayName("Should auto-populate expiry after update with new SAS token")
        void shouldAutoPopulateExpiryAfterUpdate() {
            Map<String, Object> initialAttrs = new HashMap<>();
            AzureFilesAttributes attributes = new AzureFilesAttributes(initialAttrs);
            assertFalse(attributes.hasSasTokenExpiry());

            Map<String, Object> newAttrs = new HashMap<>();
            newAttrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            attributes.update(newAttrs);

            assertTrue(attributes.hasSasTokenExpiry());
            assertEquals(VALID_EXPIRY, attributes.getSasTokenExpiry());
            logger.info("Expiry auto-populated after update: {}", attributes.getSasTokenExpiry());
        }
    }

    // ==================== Validate Configuration ====================

    @Nested
    @DisplayName("Validate Configuration")
    class ValidateConfigurationTests {

        @Test
        @DisplayName("Should pass validation with all required fields")
        void shouldPassValidationWithAllFields() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertDoesNotThrow(attributes::validateConfiguration);
            logger.info("Validation passed with all required fields");
        }

        @Test
        @DisplayName("Should throw when storage account name is missing")
        void shouldThrowWhenStorageAccountMissing() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    attributes::validateConfiguration);
            assertTrue(ex.getMessage().contains("Storage Account Name is required"));
            logger.info("Correctly rejected missing storage account: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw when storage account name is empty")
        void shouldThrowWhenStorageAccountEmpty() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "  ");
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertThrows(IllegalArgumentException.class, attributes::validateConfiguration);
        }

        @Test
        @DisplayName("Should throw when file share name is missing")
        void shouldThrowWhenFileShareMissing() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    attributes::validateConfiguration);
            assertTrue(ex.getMessage().contains("File Share Name is required"));
            logger.info("Correctly rejected missing file share: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw when file share name is empty")
        void shouldThrowWhenFileShareEmpty() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.FILE_SHARE_NAME, "");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertThrows(IllegalArgumentException.class, attributes::validateConfiguration);
        }

        @Test
        @DisplayName("Should throw when SAS token is missing")
        void shouldThrowWhenSasTokenMissing() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    attributes::validateConfiguration);
            assertTrue(ex.getMessage().contains("SAS Token is required"));
            logger.info("Correctly rejected missing SAS token: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw when SAS token is empty")
        void shouldThrowWhenSasTokenEmpty() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");
            attrs.put(Constants.SAS_TOKEN, "   ");

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertThrows(IllegalArgumentException.class, attributes::validateConfiguration);
        }
    }

    // ==================== Validate SAS Token Expiry ====================

    @Nested
    @DisplayName("Validate SAS Token Expiry")
    class ValidateSasTokenExpiryTests {

        @Test
        @DisplayName("Should pass validation when SAS token has future expiry")
        void shouldPassWhenExpiryInFuture() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            attrs.put(Constants.SAS_TOKEN_EXPIRY, VALID_EXPIRY);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertDoesNotThrow(attributes::validateSasTokenExpiry);
            logger.info("Validation passed for future expiry date");
        }

        @Test
        @DisplayName("Should skip validation when SAS token is absent")
        void shouldSkipWhenNoSasToken() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertDoesNotThrow(attributes::validateSasTokenExpiry);
            logger.info("Validation skipped correctly when no SAS token present");
        }

        @Test
        @DisplayName("Should throw when user-provided expiry does not match SAS token expiry")
        void shouldThrowWhenUserExpiryMismatch() {
            String mismatchedExpiry = "2029-01-01T00:00:00Z";
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            attrs.put(Constants.SAS_TOKEN_EXPIRY, mismatchedExpiry);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    attributes::validateSasTokenExpiry);
            assertTrue(ex.getMessage().contains("does not match"));
            logger.info("Correctly rejected mismatched expiry: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw when user-provided expiry has invalid format")
        void shouldThrowWhenUserExpiryHasInvalidFormat() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            attrs.put(Constants.SAS_TOKEN_EXPIRY, "not-a-date");

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    attributes::validateSasTokenExpiry);
            assertTrue(ex.getMessage().contains("Invalid SAS Token Expiry date format"));
            logger.info("Correctly rejected invalid expiry format: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw when SAS token is expired")
        void shouldThrowWhenSasTokenExpired() {
            // Build a token with an expiry date in the past
            String expiredToken = "sv=2024-11-04&ss=f&se=2020-01-01T00:00:00Z&st=2019-01-01T00:00:00Z&sig=test";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, expiredToken);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    attributes::validateSasTokenExpiry);
            assertTrue(ex.getMessage().contains("has expired"));
            logger.info("Correctly detected expired SAS token: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should throw when SAS token expiry is before start time")
        void shouldThrowWhenExpiryBeforeStart() {
            // Expiry is before start date, but both are in the future
            String futureYear = String.valueOf(ZonedDateTime.now().getYear() + 10);
            String badToken = "REDACTED_SECRET" + futureYear + "-01-01T00:00:00Z&st=" + futureYear + "-06-01T00:00:00Z&sig=test";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, badToken);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    attributes::validateSasTokenExpiry);
            assertTrue(ex.getMessage().contains("must be after start time"));
            logger.info("Correctly detected expiry before start: {}", ex.getMessage());
        }

        @Test
        @DisplayName("Should not throw when token expires within 7 days and expiry field is empty (logs warning only)")
        void shouldNotThrowWhenTokenExpiresWithin7Days() {
            // Build a token that expires in 3 days from now (use UTC and ISO_INSTANT for Z-suffix format)
            ZonedDateTime nearExpiry = ZonedDateTime.now(java.time.ZoneOffset.UTC).plusDays(3);
            ZonedDateTime startDate = ZonedDateTime.now(java.time.ZoneOffset.UTC).minusDays(30);
            String nearExpiryStr = nearExpiry.toInstant().toString();
            String startStr = startDate.toInstant().toString();

            String expiringToken = "REDACTED_SECRET" + nearExpiryStr + "&st=" + startStr + "&sig=test";

            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, expiringToken);
            // No SAS_TOKEN_EXPIRY set - triggers the warning path (log only, no exception)

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertDoesNotThrow(attributes::validateSasTokenExpiry);
            logger.info("Near-expiry token logs warning but does not throw");
        }

        @Test
        @DisplayName("Should not throw when token expires in more than 7 days without user-provided expiry")
        void shouldNotThrowWhenTokenExpiresInMoreThan7Days() {
            // Token expires far in the future
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            // No SAS_TOKEN_EXPIRY - but token expires in 2030, well beyond 7 days

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertDoesNotThrow(attributes::validateSasTokenExpiry);
            logger.info("No warning for token expiring far in the future");
        }

        @Test
        @DisplayName("Should pass when user-provided expiry matches SAS token expiry exactly")
        void shouldPassWhenUserExpiryMatchesTokenExpiry() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);
            attrs.put(Constants.SAS_TOKEN_EXPIRY, VALID_EXPIRY);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);

            assertDoesNotThrow(attributes::validateSasTokenExpiry);
            logger.info("Matching user-provided expiry accepted");
        }
    }

    // ==================== toString ====================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Should include storage account, file share, and token presence in toString")
        void shouldIncludeAllFieldsInToString() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");
            attrs.put(Constants.FILE_SHARE_NAME, "myshare");
            attrs.put(Constants.SAS_TOKEN, VALID_SAS_TOKEN);

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);
            String result = attributes.toString();

            assertTrue(result.contains("myaccount"));
            assertTrue(result.contains("myshare"));
            assertTrue(result.contains("hasSasToken=true"));
            assertFalse(result.contains(VALID_SAS_TOKEN), "toString should not expose SAS token value");
            logger.info("toString output: {}", result);
        }

        @Test
        @DisplayName("Should show hasSasToken=false when no token")
        void shouldShowNoTokenInToString() {
            Map<String, Object> attrs = new HashMap<>();
            attrs.put(Constants.STORAGE_ACCOUNT_NAME, "myaccount");

            AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);
            String result = attributes.toString();

            assertTrue(result.contains("hasSasToken=false"));
        }
    }
}
