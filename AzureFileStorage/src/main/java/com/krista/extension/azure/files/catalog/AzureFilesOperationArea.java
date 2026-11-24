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
import app.krista.extension.impl.anno.Attribute;
import app.krista.extension.impl.anno.CatalogRequest;
import app.krista.extension.impl.anno.Domain;
import app.krista.extension.impl.anno.Field;
import com.krista.extension.azure.files.exception.AzureFilesException;
import com.krista.extension.azure.files.impl.AzureFileServiceImpl;
import com.krista.extension.azure.files.service.AzureFilesTelemetryService;
import com.krista.extension.azure.files.util.Constants;
import com.krista.extension.azure.files.util.ResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Catalog operations for Azure File Share: list files, list directories, and download files.
 *
 * <p><b>Failure contract:</b> every method in this class ALWAYS returns an
 * {@link ExtensionResponse} built with {@code ExtensionResponseBuilder.success()}.
 * Errors are surfaced through a {@code Success=false} field in the response map, never
 * through a {@code FAILURE} result code, so the Krista runtime can always continue the
 * conversation and display the error message to the user.
 */
@Domain(id = "catEntryDomain_b77b40c4-1319-4b32-9b55-07d360453974",
        name = "Cloud Storage",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "5b250296-4743-4dd2-9c4c-1c6412f7e969")
public class AzureFilesOperationArea {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFilesOperationArea.class);

    private final AzureFileServiceImpl azureFileService;
    private final AzureFilesTelemetryService telemetryService;

    @Inject
    public AzureFilesOperationArea(AzureFileServiceImpl azureFileService,
                                   AzureFilesTelemetryService telemetryService) {
        this.azureFileService = azureFileService;
        this.telemetryService = telemetryService;
    }

    // ==================== Catalog Operations ====================

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

        AzureFilesTelemetryService.TelemetryContext ctx =
                telemetryService.recordRequestStart("azure.files", "list_files");

        try {
            List<String> files = azureFileService.listFiles(
                    filePath,
                    fileExtensionsFilter,
                    Boolean.TRUE.equals(recursiveSearch)
            );

            ExtensionResponse response = files.isEmpty()
                    ? ResponseHandler.createEmptyFileSuccessResponse()
                    : ResponseHandler.createListResponse(Constants.LIST_NAME_FILES, files);

            LOGGER.info("List Of Files succeeded: {} files at path '{}'", files.size(), filePath);
            telemetryService.recordSuccess(ctx);
            return response;

        } catch (AzureFilesException e) {
            LOGGER.error("List Of Files failed at path '{}': {} (code: {})", filePath, e.getMessage(), e.getErrorCode());
            ExtensionResponse response = ResponseHandler.createListErrorResponse(
                    Constants.LIST_NAME_FILES, e.getMessage(), e.getErrorCode());
            telemetryService.recordFailure(ctx, response);
            return response;

        } catch (Exception e) {
            LOGGER.error("List Of Files unexpected error at path '{}': {}", filePath, e.getMessage(), e);
            telemetryService.recordException(ctx, e);
            return ResponseHandler.createListErrorResponse(
                    Constants.LIST_NAME_FILES,
                    "An unexpected error occurred while listing files. Please try again.",
                    Constants.ERROR_UNEXPECTED);
        }
    }

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

        LOGGER.info("Catalog request: List Of Directories - path='{}', recursive={}", filePath, recursiveSearch);

        AzureFilesTelemetryService.TelemetryContext ctx =
                telemetryService.recordRequestStart("azure.files", "list_directories");

        try {
            List<String> directories = azureFileService.listDirectories(
                    filePath,
                    Boolean.TRUE.equals(recursiveSearch)
            );

            ExtensionResponse response = directories.isEmpty()
                    ? ResponseHandler.createEmptyDirectorySuccessResponse()
                    : ResponseHandler.createListResponse(Constants.LIST_NAME_DIRECTORIES, directories);

            LOGGER.info("List Of Directories succeeded: {} dirs at path '{}'", directories.size(), filePath);
            telemetryService.recordSuccess(ctx);
            return response;

        } catch (AzureFilesException e) {
            LOGGER.error("List Of Directories failed at path '{}': {} (code: {})", filePath, e.getMessage(), e.getErrorCode());
            ExtensionResponse response = ResponseHandler.createListErrorResponse(
                    Constants.LIST_NAME_DIRECTORIES, e.getMessage(), e.getErrorCode());
            telemetryService.recordFailure(ctx, response);
            return response;

        } catch (Exception e) {
            LOGGER.error("List Of Directories unexpected error at path '{}': {}", filePath, e.getMessage(), e);
            telemetryService.recordException(ctx, e);
            return ResponseHandler.createListErrorResponse(
                    Constants.LIST_NAME_DIRECTORIES,
                    "An unexpected error occurred while listing directories. Please try again.",
                    Constants.ERROR_UNEXPECTED);
        }
    }

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

        LOGGER.info("Catalog request: Get File - path='{}'", filePath);

        AzureFilesTelemetryService.TelemetryContext ctx =
                telemetryService.recordRequestStart("azure.files", "get_file");

        try {
            Map<String, Object> fileInfo = azureFileService.getFile(filePath);

            if (fileInfo.containsKey(Constants.KEY_ERROR)) {
                LOGGER.error("Get File failed at path '{}': {}", filePath, fileInfo.get(Constants.KEY_ERROR));
                ExtensionResponse response = ResponseHandler.createExceptionFileResponse(fileInfo);
                telemetryService.recordFailure(ctx, response);
                return response;
            }

            LOGGER.info("Get File succeeded: '{}'", filePath);
            ExtensionResponse response = ResponseHandler.createFileSuccessResponse(fileInfo);
            telemetryService.recordSuccess(ctx);
            return response;

        } catch (Exception e) {
            // getFile() is designed to never throw, but this is the final safety net.
            // We MUST return Success=false via the structured response, NOT .failure(),
            // to prevent the Krista runtime from halting the conversation.
            LOGGER.error("Get File unexpected exception at path '{}': {}", filePath, e.getMessage(), e);
            telemetryService.recordException(ctx, e);

            Map<String, Object> errorInfo = Map.of(
                    Constants.KEY_ERROR, "An unexpected error occurred while downloading the file. Please try again.",
                    Constants.KEY_ERROR_CODE, Constants.ERROR_UNEXPECTED
            );
            return ResponseHandler.createExceptionFileResponse(errorInfo);
        }
    }
}
