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
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import com.krista.extension.azure.files.exception.AzureFilesException;
import com.krista.extension.azure.files.impl.AzureFileServiceImpl;
import com.krista.extension.azure.files.util.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Azure Files Operation Area
 * <p>
 * This class handles catalog requests for Azure Files operations, providing
 * functionality to list files, list directories, and download files from
 * Azure File Share storage.
 * consistent implementation across the extension.
 * </p>
 *
 * @author Krista Extension Framework
 * @version 1.0.0
 * @since 2024-06-27
 */
@Domain(id = "catEntryDomain_b77b40c4-1319-4b32-9b55-07d360453974",
        name = "Cloud Storage",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "5b250296-4743-4dd2-9c4c-1c6412f7e969")
public class AzureFilesOperationArea {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFilesOperationArea.class);
    private final AzureFileServiceImpl azureFileService;

    @Inject
    public AzureFilesOperationArea(AzureFileServiceImpl azureFileService) {

        this.azureFileService = azureFileService;
    }

    /**
     * Lists files in an Azure File Share directory.
     * <p>
     * This method retrieves a list of files from the specified path in Azure File Share.
     * It supports optional filtering by file extension and recursive searching.
     * </p>
     *
     * @param filePath             The path in Azure File Share to list files from
     * @param fileExtensionsFilter Optional comma-separated list of file extensions to filter by
     * @param recursiveSearch      Whether to search recursively through subdirectories
     * @return An ExtensionResponse containing the list of files or an error message
     */
    @CatalogRequest(
            id = "localDomainRequest_f45a0a12-5d1b-4d6e-a63d-e2267c834e4c",
            name = "List Of Files",
            description = "List all files in an Azure File Share directory based on a specific file extension filter, with recursive search starting from a given folder path.",
            area = "Azure Files Operation",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Desc(name = "List Of Files", type = "[ Composite ]", required = false)
    @Field.Boolean(name = "Success", required = true)
    public ExtensionResponse listFiles(
            @Field.Text(name = "File Path", required = true) String filePath,
            @Field.Text(name = "File Extensions Filter", required = false) String fileExtensionsFilter,
            @Field.Boolean(name = "Recursive Search", required = false) Boolean recursiveSearch) {

        LOGGER.info("Catalog request: List Of Files - path='{}', filter='{}', recursive={}",
                filePath, fileExtensionsFilter, recursiveSearch);

        try {
            // Call service - will throw AzureFilesException if error occurs
            List<String> files = azureFileService.listFiles(
                    filePath,
                    fileExtensionsFilter,
                    recursiveSearch
            );

            //  If we reach here, operation succeeded
            if (files.isEmpty()) {
                // Valid path, but directory is empty
                LOGGER.info("No files found at path '{}' (directory is empty)", filePath);
                return ResponseHandler.createEmptyFileSuccessResponse();
            }

            // Valid path with results
            LOGGER.info("Successfully listed {} files from path '{}'", Optional.of(files.size()), filePath);
            return ResponseHandler.createListResponse("List Of Files", files);

        } catch (AzureFilesException e) {
            //  Operation failed (404, 403, 401, 500, timeout, etc.)
            LOGGER.error("Failed to list files from path '{}': {} (code: {})",
                    filePath, e.getMessage(), e.getErrorCode());

            return ResponseHandler.createListErrorResponse(
                    "List Of Files",
                    e.getMessage(),
                    e.getErrorCode()
            );

        } catch (Exception e) {
            //  Unexpected error
            LOGGER.error("Unexpected error listing files from path '{}': {}",
                    filePath, e.getMessage(), e);

            return ResponseHandler.createListErrorResponse(
                    "List Of Files",
                    "An unexpected error occurred: " + e.getMessage(),
                    "UNEXPECTED_ERROR"
            );
        }
    }

    /**
     * Lists directories in an Azure File Share path.
     * <p>
     * This method retrieves a list of directories from the specified path in Azure File Share.
     * It supports optional recursive searching through subdirectories.
     * </p>
     *
     * @param filePath        The path in Azure File Share to list directories from
     * @param recursiveSearch Whether to search recursively through subdirectories
     * @return An ExtensionResponse containing the list of directories or an error message
     */
    @CatalogRequest(
            id = "localDomainRequest_2741803f-5ec0-41fe-8fd0-39e6ec102f7e",
            name = "List Of Directories",
            description = "Retrieve a list of all directories in an Azure File Share with recursive search, including the complete directory paths.",
            area = "Azure Files Operation",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field.Desc(name = "List Of Directories", type = "[ Composite ]", required = false)
    @Field.Boolean(name = "Success", required = true)
    public ExtensionResponse listDirectories(
            @Field.Text(name = "File Path", required = true) String filePath,
            @Field.Boolean(name = "Recursive Search", required = false,
                    attributes = {@Attribute(name = "visualWidth", value = "S")},
                    options = {}) Boolean recursiveSearch) {

        LOGGER.info("Catalog request: List Of Directories - path='{}', recursive={}",
                filePath, recursiveSearch);

        try {
            // Call service - will throw AzureFilesException if error occurs
            List<String> directories = azureFileService.listDirectories(
                    filePath,
                    recursiveSearch
            );

            //  If we reach here, operation succeeded
            if (directories.isEmpty()) {
                // Valid path, but no subdirectories
                LOGGER.info("No directories found at path '{}' (no subdirectories)", filePath);
                return ResponseHandler.createEmptyDirectorySuccessResponse();
            }

            // Valid path with results
            LOGGER.info("Successfully listed {} directories from path '{}'",
                    Optional.of(directories.size()), filePath);
            return ResponseHandler.createListResponse("List Of Directories", directories);

        } catch (AzureFilesException e) {
            //  Operation failed (404, 403, 401, 500, timeout, etc.)
            LOGGER.error("Failed to list directories from path '{}': {} (code: {})",
                    filePath, e.getMessage(), e.getErrorCode());

            return ResponseHandler.createListErrorResponse(
                    "List Of Directories",
                    e.getMessage(),
                    e.getErrorCode()
            );

        } catch (Exception e) {
            //  Unexpected error
            LOGGER.error("Unexpected error listing directories from path '{}': {}",
                    filePath, e.getMessage(), e);

            return ResponseHandler.createListErrorResponse(
                    "List Of Directories",
                    "An unexpected error occurred: " + e.getMessage(),
                    "UNEXPECTED_ERROR"
            );
        }
    }

    /**
     * Downloads a file from Azure File Share.
     * <p>
     * This method retrieves a file from the specified path in Azure File Share
     * and returns it along with its metadata.
     * </p>
     *
     * @param filePath The path to the file in Azure File Share
     * @return An ExtensionResponse containing the file and its metadata or an error message
     */
    @CatalogRequest(
            id = "localDomainRequest_d54872b8-951d-4bb2-a591-d89062e587b6",
            name = "Get File",
            description = "Retrieve a file along with its downloadable content and metadata using the file path and name.",
            area = "Azure Files Operation",
            type = CatalogRequest.Type.QUERY_SYSTEM)
    @Field(name = "File Info", type = "FreeForm", required = false, attributes = {}, options = {})
    @Field.Boolean(name = "Success", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    @Field.File(name = "File", multipleFileUpload = false, required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public ExtensionResponse getFile(
            @Field.Text(name = "File Path", required = false, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {}) String filePath) {

        LOGGER.debug("Getting file from path: {}", filePath);

        try {
            Map<String, Object> fileInfo = azureFileService.getFile(filePath);
            if (fileInfo.get("error") != null) {
                LOGGER.error("Failed to get file from path: {}", filePath);
                return ResponseHandler.createExceptionFileResponse(fileInfo);
            }
            LOGGER.info("Successfully retrieved file: {}", filePath);
            return ResponseHandler.createFileSuccessResponse(fileInfo);
        } catch (Exception cause) {
            LOGGER.error("Unexpected error getting file from path: {}", filePath, cause);
            return new ExtensionResponseBuilder().failure("Failed to get file: " + cause.getMessage(), cause).build();
        }
    }

}
