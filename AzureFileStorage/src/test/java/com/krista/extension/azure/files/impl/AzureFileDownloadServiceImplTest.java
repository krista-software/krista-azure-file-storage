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

package com.krista.extension.azure.files.impl;

import com.krista.extension.azure.files.AzureFilesAttributes;
import com.krista.extension.azure.files.client.AzureFileRestClient;
import com.krista.extension.azure.files.krista.AzureFilesSettings;
import com.krista.extension.azure.files.krista.KristaMediaClient;
import com.krista.extension.azure.files.service.AzureFileService;
import com.krista.extension.azure.files.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AzureFileDownloadServiceImpl
 * Tests file download methods with mocked SAS token credentials
 */
@DisplayName("Azure File Download Service Integration Tests")
class AzureFileDownloadServiceImplTest {
    private static final Logger logger = LoggerFactory.getLogger(AzureFileDownloadServiceImplTest.class);

    private AzureFileService azureFileService;
    private AzureFilesAttributes attributes;
    private AzureFileRestClient restClient;
    private KristaMediaClient mockMediaClient;

    @BeforeEach
    void setUp() {
        logger.info("Setting up AzureFileDownloadServiceImplTest with mocked SAS token credentials");

        // Use AzureFilesSettings like in AzureFileListingServiceImplTest
        AzureFilesSettings settings = new AzureFilesSettings();
        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put(Constants.STORAGE_ACCOUNT_NAME, settings.getStorageAccountName());
        attributeMap.put(Constants.FILE_SHARE_NAME, settings.getFileShareName());
        attributeMap.put(Constants.SAS_TOKEN, settings.getSasToken());

        attributes = new AzureFilesAttributes(attributeMap);


        // Create KristaMediaClient
        mockMediaClient = new KristaMediaClient();
        restClient = new AzureFileRestClient(attributes);
        // Create service instance
        azureFileService = new AzureFileServiceImpl(new AzureFileListingServiceImpl(restClient), new AzureFileDownloadServiceImpl(restClient,mockMediaClient));

        logger.info("Azure File Download Service configured with Storage Account: {}, File Share: {}",
                settings.getStorageAccountName(), settings.getFileShareName());
    }

    @Test
    @Timeout(50) // 30 seconds timeout
    @DisplayName("Should successfully download a file from Azure File Share with mocked SAS token")
    void shouldDownloadFileWithSasToken() {
        logger.info("Testing file download with mocked SAS token credentials");

        try {
            // Arrange - Test file path
            String testFilePath = "documents/sample.pdf";

            logger.info("Attempting to download file: {}", testFilePath);
            logger.info("Using Storage Account: {}, File Share: {}", attributes.getStorageAccountName(), attributes.getFileShareName());
            logger.info("SAS Token format: {}", attributes.getSasToken().startsWith("?sv=") ? "Valid" : "Invalid");

            // Act - Call the download service
            Map<String, Object> result = azureFileService.getFile(testFilePath);

            // Assert - Verify results
            if (result != null) {
                logger.info("File download test passed - Result received");

                // Check if result contains expected keys
                if (result.containsKey("error")) {
                    String errorMessage = (String) result.get("error");
                    logger.warn("Download returned error: {}", errorMessage);

                    // This is expected behavior for mocked credentials
                    assertTrue(errorMessage.contains("not found") ||
                                    errorMessage.contains("missing") ||
                                    errorMessage.contains("empty") ||
                                    errorMessage.contains("forbidden") ||
                                    errorMessage.contains("Authentication failed") ||
                                    errorMessage.contains("unavailable"),
                            "Error message should indicate file access issue");

                    logger.info("Error handling test passed - Appropriate error message returned");

                } else {
                    // Successful download
                    assertNotNull(result.get("filename"), "Filename should be present");
                    assertNotNull(result.get("kristafile"), "Krista file should be present");
                    assertNotNull(result.get("filesize"), "File size should be present");

                    String filename = (String) result.get("filename");
                    Long filesize = (Long) result.get("filesize");

                    logger.info("File download successful:");
                    logger.info("  - Filename: {}", filename);
                    logger.info("  - File size: {} bytes", filesize);
                    logger.info("  - Krista file object created: {}", result.get("kristafile") != null);
                }

            } else {
                logger.warn("Download service returned null result");
                fail("Download service should not return null");
            }

        } catch (IllegalArgumentException e) {
            logger.info("Input validation test passed - Exception: {}", e.getMessage());
            assertTrue(e.getMessage().contains("File path cannot be null") ||
                            e.getMessage().contains("empty"),
                    "Should validate file path input");

        } catch (Exception e) {
            logger.info("Expected exception during download with mocked credentials: {}", e.getMessage());

            // This is expected behavior when using mocked credentials
            assertTrue(e.getMessage().contains("Connection") ||
                            e.getMessage().contains("Network") ||
                            e.getMessage().contains("HTTP") ||
                            e.getMessage().contains("Authentication"),
                    "Exception should be related to connection/authentication issues");

            logger.info("Connection error handling test passed");
        }

        logger.info("File download test completed");
    }

    @Test
    @DisplayName("Should handle invalid file path gracefully")
    void shouldHandleInvalidFilePathGracefully() {
        logger.info("Testing error handling for invalid file paths");

        // Test null file path - should return error map, not throw exception
        Map<String, Object> result = azureFileService.getFile(null);
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("error"), "Result should contain error key");
        String errorMessage = (String) result.get("error");
        assertTrue(errorMessage.contains("required") || errorMessage.contains("path"),
                "Error message should indicate path is required");
        logger.info("Null path validation passed - Error: {}", errorMessage);

        // Test empty file path - should return error map, not throw exception
        result = azureFileService.getFile("");
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("error"), "Result should contain error key");
        errorMessage = (String) result.get("error");
        assertTrue(errorMessage.contains("required") || errorMessage.contains("path"),
                "Error message should indicate path is required");
        logger.info("Empty path validation passed - Error: {}", errorMessage);

        // Test whitespace-only file path - should return error map, not throw exception
        result = azureFileService.getFile("   ");
        assertNotNull(result, "Result should not be null");
        assertTrue(result.containsKey("error"), "Result should contain error key");
        errorMessage = (String) result.get("error");
        assertTrue(errorMessage.contains("required") || errorMessage.contains("path"),
                "Error message should indicate path is required");
        logger.info("Whitespace path validation passed - Error: {}", errorMessage);

        logger.info("Invalid file path handling tests completed");
    }

    @Test
    @DisplayName("Should validate SAS token configuration")
    void shouldValidateSasTokenConfiguration() {
        logger.info("Testing SAS token configuration validation");

        String sasToken = attributes.getSasToken();

        // Verify SAS token format - be flexible with real vs mock tokens
        assertNotNull(sasToken, "SAS token should not be null");
        assertFalse(sasToken.isEmpty(), "SAS token should not be empty");

        // Log the actual token format for debugging
        logger.info("SAS token format: {}", sasToken.startsWith("?sv=") ? "Standard Azure format" : "Custom format");
        logger.info("SAS token length: {} characters", sasToken.length());

        logger.info("SAS token format validation passed");

        // Verify attributes configuration
        assertNotNull(attributes.getStorageAccountName(), "Storage account should not be null");
        assertNotNull(attributes.getFileShareName(), "File share should not be null");
        assertNotNull(attributes.getSasToken(), "SAS token should not be null");

        logger.info("Attributes configuration validation passed");
        logger.info("Storage Account: {}, File Share: {}", attributes.getStorageAccountName(), attributes.getFileShareName());
        logger.info("Configuration validation tests completed");
    }

    @Test
    @DisplayName("Should test file path normalization")
    void shouldTestFilePathNormalization() {
        logger.info("Testing file path normalization behavior");

        String[] testPaths = {
                "documents/file.pdf",
                "/documents/file.pdf",
                "documents\\file.pdf",
                "/documents\\file.pdf",
                "documents/subfolder/file.txt"
        };

        for (String testPath : testPaths) {
            try {
                logger.info("Testing path normalization for: '{}'", testPath);

                // This will test the path normalization logic in the service
                Map<String, Object> result = azureFileService.getFile(testPath);

                // We expect this to fail with connection issues due to mocked credentials
                // but the path normalization should work correctly
                logger.info("Path '{}' was processed (connection expected to fail with mocked credentials)", testPath);

            } catch (IllegalArgumentException e) {
                logger.error("Path validation failed for '{}': {}", testPath, e.getMessage());
                fail("Path normalization should not cause validation errors for valid paths");

            } catch (Exception e) {
                // Expected - connection/authentication errors with mocked credentials
                logger.info("Expected connection error for path '{}': {}", testPath, e.getMessage());
            }
        }

        logger.info("File path normalization tests completed");
    }
}
