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

import com.krista.extension.azure.files.client.AzureFileRestClient;
import com.krista.extension.azure.files.krista.KristaMediaClient;
import com.krista.extension.azure.files.util.AzureHttpErrorHandler;
import com.krista.extension.azure.files.util.Constants;
import com.krista.extension.azure.files.util.FileValidationUtils;
import com.krista.extension.azure.files.util.PathUtils;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Service implementation for downloading files from Azure File Share.
 *
 * <p>This service handles the complete file download workflow including:
 * <ul>
 *   <li>Path validation and normalization</li>
 *   <li>File download from Azure Files API</li>
 *   <li>Temporary file creation and management</li>
 *   <li>Conversion to Krista-compatible file objects</li>
 *   <li>Comprehensive error handling for all Azure API scenarios</li>
 *   <li>Automatic resource cleanup</li>
 * </ul>
 *
 * <p><b>Important:</b> This service never throws exceptions to the catalog layer.
 * All errors are returned as structured error responses with user-friendly messages.
 */
@Service
public class AzureFileDownloadServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFileDownloadServiceImpl.class);

    private final AzureFileRestClient restClient;
    private final KristaMediaClient mediaClient;

    /**
     * Constructs the Azure File Download Service with the provided REST client and media client.
     *
     * @param restClient REST client for Azure Files API operations
     * @param mediaClient Krista media client for file conversion
     */
    @Inject
    public AzureFileDownloadServiceImpl(AzureFileRestClient restClient, KristaMediaClient mediaClient) {
        this.restClient = restClient;
        this.mediaClient = mediaClient;
    }


    /**
     * Downloads a file from Azure File Share and returns it as a Krista-compatible file object.
     *
     * <p>This method handles the complete download workflow:
     * <ol>
     *   <li>Validates and normalizes the file path</li>
     *   <li>Validates file extension against allowed types</li>
     *   <li>Downloads file content from Azure Files API</li>
     *   <li>Validates file size against business limits</li>
     *   <li>Creates temporary file and converts to Krista format</li>
     *   <li>Cleans up temporary resources</li>
     * </ol>
     *
     * <p><b>Error Handling:</b> This method never throws exceptions. All errors are returned
     * as structured error responses with user-friendly messages and error codes.
     *
     * @param filePath the path to the file in Azure File Share (e.g., "documents/report.pdf")
     * @return Map containing either success data (filename, kristafile, filesize) or error information
     */
    public Map<String, Object> getFile(String filePath) {
        LOGGER.info("Starting file download for path: '{}'", filePath);
        long startTime = System.currentTimeMillis();
        java.io.File tempFile = null;

        try {
            // Step 1: Validate file path
            String validationError = validateFilePath(filePath);
            if (validationError != null) {
                return createErrorResponse(validationError, "INVALID_PATH");
            }

            // Step 2: Normalize path
            String normalizedPath = normalizeFilePath(filePath);
            String fileName = extractFileName(normalizedPath);
            LOGGER.debug("Normalized path: '{}', fileName: '{}'", normalizedPath, fileName);

            // Step 3: Download file from Azure (checks if file exists first)
            DownloadResult downloadResult = downloadFile(normalizedPath);
            if (!downloadResult.isSuccess()) {
                return createErrorResponse(downloadResult.getErrorMessage(), downloadResult.getErrorCode());
            }

            // Step 4: Validate file extension (only after confirming file exists)
            String extensionError = validateFileExtension(fileName);
            if (extensionError != null) {
                return createErrorResponse(extensionError, "INVALID_EXTENSION");
            }

            byte[] fileContent = downloadResult.getContent();
            double fileSizeKB = fileContent.length / 1024.0;
            LOGGER.debug("Downloaded {} bytes ({} KB) for file: '{}'", fileContent.length, String.format("%.2f", fileSizeKB), fileName);

            // Step 5: Validate file size
            String sizeError = validateFileSize(fileContent.length, fileName);
            if (sizeError != null) {
                return createErrorResponse(sizeError, "FILE_TOO_LARGE");
            }

            // Step 6: Create temp file and convert to Krista format
            tempFile = createTempFile(fileName, fileContent);
            app.krista.model.base.File kristaFile = convertToKristaFile(tempFile, fileName);

            // Step 7: Build success response
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Successfully downloaded file '{}' ({} KB) in {}ms", fileName, String.format("%.2f", fileSizeKB), duration);

            return createSuccessResponse(fileName, kristaFile, fileContent.length);

        } catch (Exception e) {
            // Catch-all for unexpected errors
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.error("Unexpected error downloading file '{}' after {}ms: {}",
                filePath, duration, e.getMessage(), e);
            return createErrorResponse(
                "An unexpected error occurred while downloading your file. Please try again in a few minutes. If the problem continues, please contact support and mention you were downloading from Azure Files.",
                "UNEXPECTED_ERROR"
            );
        } finally {
            // Always clean up temp file
            cleanupTempFile(tempFile);
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates the file path is not null or empty.
     * Returns error message if invalid, null if valid.
     */
    private String validateFilePath(String filePath) {
        return FileValidationUtils.validateFilePath(filePath, LOGGER);
    }

    /**
     * Validates file extension against allowed types.
     * Returns error message if invalid, null if valid.
     */
    private String validateFileExtension(String fileName) {
        return FileValidationUtils.validateFileExtension(fileName, LOGGER);
    }

    /**
     * Validates file size against business limits.
     * Returns error message if too large, null if valid.
     */
    private String validateFileSize(long fileSize, String fileName) {
        return FileValidationUtils.validateFileSize(fileSize, fileName, LOGGER);
    }

    /**
     * Normalizes the file path by trimming whitespace, converting backslashes to forward slashes,
     * and removing leading/trailing slashes.
     */
    private String normalizeFilePath(String filePath) {
        return PathUtils.normalizePath(filePath);
    }

    /**
     * Extracts the file name from the full file path by finding the last slash separator.
     */
    private String extractFileName(String filePath) {
        return PathUtils.extractFileName(filePath);
    }

    /**
     * Downloads file content from Azure File Share with comprehensive error handling.
     * Returns DownloadResult containing either file content or error information.
     */
    private DownloadResult downloadFile(String filePath) {
        try {
            String url = restClient.buildDownloadUrl(filePath);
            LOGGER.debug("Downloading file from Azure: '{}'", filePath);

            HttpResponse<byte[]> response = restClient.sendGetByte(url);
            int statusCode = response.statusCode();

            if (statusCode == Constants.HTTP_OK) {
                return DownloadResult.success(response.body());
            }

            // Handle specific HTTP error codes with user-friendly messages
            return handleHttpError(statusCode, filePath);

        } catch (java.net.http.HttpTimeoutException e) {
            AzureHttpErrorHandler.ErrorResponse errorResponse = AzureHttpErrorHandler.handleTimeout(filePath, LOGGER);
            return DownloadResult.error(errorResponse.getMessage(), errorResponse.getErrorCode());
        } catch (java.io.IOException e) {
            LOGGER.error("IO error downloading file '{}': {}", filePath, e.getMessage(), e);

            // Check for specific IO errors
            String errorMsg = e.getMessage().toLowerCase();
            if (errorMsg.contains("file too large")) {
                return DownloadResult.error(
                    String.format("The file is too large to download (%d MB limit). Please compress the file or use a smaller file. Large files can also be accessed directly through Azure Portal.",
                        Constants.MAX_FILE_SIZE_BYTES_TECHNICAL / (1024 * 1024)),
                    Constants.ERROR_FILE_TOO_LARGE
                );
            } else if (errorMsg.contains("connection")) {
                AzureHttpErrorHandler.ErrorResponse errorResponse = AzureHttpErrorHandler.handleConnectionError(filePath, LOGGER);
                return DownloadResult.error(errorResponse.getMessage(), errorResponse.getErrorCode());
            }

            return DownloadResult.error(
                "There was a problem downloading the file from Azure Files. Please check your internet connection and try again. If the issue persists, the file may be corrupted or temporarily unavailable.",
                Constants.ERROR_UNEXPECTED
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Download interrupted for file '{}': {}", filePath, e.getMessage());
            return DownloadResult.error(
                "The file download was interrupted. This can happen due to network issues or system load. Please try downloading the file again.",
                Constants.ERROR_UNEXPECTED
            );
        } catch (Exception e) {
            AzureHttpErrorHandler.ErrorResponse errorResponse = AzureHttpErrorHandler.handleUnexpectedError(filePath, e, LOGGER);
            return DownloadResult.error(errorResponse.getMessage(), errorResponse.getErrorCode());
        }
    }

    /**
     * Handles HTTP error responses with user-friendly messages based on status code.
     */
    private DownloadResult handleHttpError(int statusCode, String filePath) {
        AzureHttpErrorHandler.ErrorResponse errorResponse = AzureHttpErrorHandler.handleHttpError(statusCode, filePath, LOGGER);
        return DownloadResult.error(errorResponse.getMessage(), errorResponse.getErrorCode());
    }

    /**
     * Creates a temporary file on the local filesystem with the specified name and content.
     * The temp file will be automatically deleted by the cleanup method.
     */
    private java.io.File createTempFile(String fileName, byte[] content) throws IOException {
        String nameWithoutExtension;
        String extension;

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            nameWithoutExtension = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        } else {
            nameWithoutExtension = fileName;
            extension = "";
        }

        java.io.File tempFile = Files.createTempFile(nameWithoutExtension, extension).toFile();

        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            outputStream.write(content);
        } catch (IOException e) {
            // Clean up temp file if write fails
            if (tempFile.exists() && !tempFile.delete()) {
                LOGGER.warn("Failed to delete temp file after write error: {}", tempFile.getAbsolutePath());
            }
            throw e;
        }

        LOGGER.debug("Temporary file created: '{}' ({} bytes)", tempFile.getAbsolutePath(), content.length);
        return tempFile;
    }

    /**
     * Converts a Java File object to a Krista-compatible File object using the media client.
     */
    private app.krista.model.base.File convertToKristaFile(java.io.File javaFile, String originalFileName) throws IOException {
        LOGGER.debug("Converting to Krista file: '{}'", originalFileName);
        return mediaClient.toKristaFile(javaFile, originalFileName);
    }

    /**
     * Cleans up temporary file by deleting it from the filesystem.
     * This method is called in the finally block to ensure cleanup always happens.
     */
    private void cleanupTempFile(java.io.File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                if (tempFile.delete()) {
                    LOGGER.debug("Temporary file deleted: '{}'", tempFile.getAbsolutePath());
                } else {
                    LOGGER.warn("Failed to delete temporary file: '{}'", tempFile.getAbsolutePath());
                }
            } catch (SecurityException e) {
                LOGGER.error("Security exception deleting temporary file '{}': {}",
                    tempFile.getAbsolutePath(), e.getMessage());
            }
        }
    }

    /**
     * Creates a success response map with file information.
     */
    private Map<String, Object> createSuccessResponse(String fileName, app.krista.model.base.File kristaFile, long fileSizeBytes) {
        // Convert bytes to KB (rounded to 2 decimal places)
        double fileSizeKB = fileSizeBytes / 1024.0;
        String fileSizeFormatted = String.format("%.2f KB", fileSizeKB);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.KEY_FILENAME, fileName);
        response.put(Constants.KEY_KRISTA_FILE, kristaFile);
        response.put(Constants.KEY_FILE_SIZE_KB, fileSizeFormatted);  // Only KB format

        LOGGER.debug("Created success response: file='{}', size={}",
            fileName, fileSizeFormatted);

        return response;
    }

    /**
     * Creates an error response map with error message and error code.
     * This is used to return structured error information to the catalog layer.
     */
    private Map<String, Object> createErrorResponse(String message, String errorCode) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put(Constants.KEY_ERROR, message);
        errorMap.put(Constants.KEY_ERROR_CODE, errorCode);
        LOGGER.debug("Created error response: code={}, message={}", errorCode, message);
        return errorMap;
    }


    /**
     * Result object for download operations containing either success data or error information.
     */
    private static class DownloadResult {
        private final boolean success;
        private final byte[] content;
        private final String errorMessage;
        private final String errorCode;

        private DownloadResult(boolean success, byte[] content, String errorMessage, String errorCode) {
            this.success = success;
            this.content = content;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
        }

        static DownloadResult success(byte[] content) {
            return new DownloadResult(true, content, null, null);
        }

        static DownloadResult error(String errorMessage, String errorCode) {
            return new DownloadResult(false, null, errorMessage, errorCode);
        }

        boolean isSuccess() {
            return success;
        }

        byte[] getContent() {
            return content;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getErrorCode() {
            return errorCode;
        }
    }
}
