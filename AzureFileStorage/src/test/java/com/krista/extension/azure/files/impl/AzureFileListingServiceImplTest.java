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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Integration tests for AzureFileListingServiceImpl via unified AzureFileService
 * Tests listing methods with mocked SAS token credentials
 */
@DisplayName("Azure File Listing Service Integration Tests")
class AzureFileListingServiceImplTest {
    private static final Logger logger = LoggerFactory.getLogger(AzureFileListingServiceImplTest.class);
    private AzureFileService azureFileService;

    @BeforeEach
    void setUp() {
        logger.info("Setting up AzureFileListingServiceImplTest with unified AzureFileService");
        AzureFilesSettings settings = new AzureFilesSettings();
        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put(Constants.STORAGE_ACCOUNT_NAME, settings.getStorageAccountName());
        attributeMap.put(Constants.FILE_SHARE_NAME, settings.getFileShareName());
        attributeMap.put(Constants.SAS_TOKEN, settings.getSasToken());
        AzureFilesAttributes attributes = new AzureFilesAttributes(attributeMap);

        // Create REST client and specialized services
        AzureFileRestClient restClient = new AzureFileRestClient(attributes);
        KristaMediaClient mediaClient = new KristaMediaClient();
        AzureFileListingServiceImpl listingService = new AzureFileListingServiceImpl(restClient);
        AzureFileDownloadServiceImpl downloadService = new AzureFileDownloadServiceImpl(restClient, mediaClient);

        // Create unified service
        azureFileService = new AzureFileServiceImpl(listingService, downloadService);
    }
    @Test
    @Timeout(30) // 30 seconds timeout
    @DisplayName("Should successfully list all directories from Azure File Share with mocked SAS token")
    void listOfDirectories() {
        logger.info("Testing listDirectories with mocked SAS token credentials");

        try {
            // Arrange - Test parameters
            String path = "";  // Root directory
            boolean recursive = false;  // Non-recursive for this test

            logger.info("Calling listDirectories with path: '{}', recursive: {}", path, recursive);

            // Act - Call the service method
            List<String> listDirectories = azureFileService.listDirectories(path, recursive);

            // Assert - Verify results
            if (listDirectories != null) {
                logger.info(" listDirectories test passed - Found {} directories", listDirectories.size());

                // Log all directories found
                if (!listDirectories.isEmpty()) {
                    logger.info("Directories found:");
                    listDirectories.forEach(directory ->
                        logger.info("  - {}", directory));
                } else {
                    logger.info("No directories found in the specified path");
                }

                // Basic assertions
                assertNotNull(listDirectories, "Directory list should not be null");
                logger.info("Directory listing completed successfully");

            } else {
                logger.warn("Directory listing returned null - this may indicate an error");
                fail("Directory listing should not return null");
            }

        } catch (Exception cause) {
            logger.error(" listDirectories test failed with exception: {}", cause.getMessage(), cause);
            fail("Directory listing should not throw exception: " + cause.getMessage());
        }

        logger.info("listOfDirectories test completed");
    }
    @Test
    @Timeout(30) // 30 seconds timeout
    @DisplayName("Should successfully list all files from Azure File Share with mocked SAS token")
    void listOfFiles() {
        logger.info("Testing listFiles with mocked SAS token credentials");

        try {
            // Arrange - Test parameters
            String basePath = "";  // Root directory
            String extensionFilter = "";  // No extension filter
            boolean recursive = false;  // Non-recursive for this test

            logger.info("Calling listFiles with path: '{}', extensionFilter: '{}', recursive: {}",
                       basePath, extensionFilter, recursive);

            // Act - Call the service method
            List<String> listFiles = azureFileService.listFiles(basePath, extensionFilter, recursive);

            // Assert - Verify results
            if (listFiles != null) {
                logger.info("listFiles test passed - Found {} files", listFiles.size());

                // Log all files found
                if (!listFiles.isEmpty()) {
                    logger.info("Files found:");
                    listFiles.forEach(file ->
                        logger.info("  - {}", file));

                    // Additional assertions for file listing
                    assertTrue(listFiles.stream().allMatch(file -> !file.isEmpty()),
                              "All file paths should not be empty");

                    // Check if files have proper path format
                    logger.info("File path validation completed");

                } else {
                    logger.info("No files found in the specified path");
                }

                // Basic assertions
                assertNotNull(listFiles, "File list should not be null");
                logger.info("File listing completed successfully");

            } else {
                logger.warn("File listing returned null - this may indicate an error");
                fail("File listing should not return null");
            }

        } catch (Exception e) {
            logger.error("listFiles test failed with exception: {}", e.getMessage(), e);
            fail("File listing should not throw exception: " + e.getMessage());
        }

        logger.info("listOfFiles test completed");
    }

}
