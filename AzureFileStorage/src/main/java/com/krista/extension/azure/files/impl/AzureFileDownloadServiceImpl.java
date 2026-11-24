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
import com.krista.extension.azure.files.util.FilenameUtil;
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
 * <p>Handles the complete download workflow: path validation, Azure API call,
 * size validation, temp file creation, Krista conversion, and cleanup.
 * Never throws exceptions — all errors are returned as structured error maps.
 */
@Service
public class AzureFileDownloadServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFileDownloadServiceImpl.class);

    private final AzureFileRestClient restClient;
    private final KristaMediaClient mediaClient;

    @Inject
    public AzureFileDownloadServiceImpl(AzureFileRestClient restClient, KristaMediaClient mediaClient) {
        this.restClient = restClient;
        this.mediaClient = mediaClient;
    }

    /**
     * Downloads a file from Azure File Share and returns it as a Krista-compatible file object.
     *
     * @param filePath path to the file in Azure File Share (e.g., "documents/report.pdf")
     * @return map with success keys (filename, kristafile, filesizekb) or error keys (error, errorCode)
     */
    public Map<String, Object> getFile(String filePath) {
        LOGGER.info("Starting file download for path: '{}'", filePath);
        long startTime = System.currentTimeMillis();
        java.io.File tempFile = null;

        try {
            String validationError = FileValidationUtils.validateFilePath(filePath, LOGGER);
            if (validationError != null) {
                return errorResponse(validationError, Constants.ERROR_INVALID_PATH);
            }

            String normalizedPath = PathUtils.normalizePath(filePath);
            String fileName = PathUtils.extractFileName(normalizedPath);
            LOGGER.debug("Normalized path: '{}', fileName: '{}'", normalizedPath, fileName);

            DownloadResult downloadResult = downloadFile(normalizedPath);
            if (!downloadResult.success()) {
                return errorResponse(downloadResult.errorMessage(), downloadResult.errorCode());
            }

            String extensionError = FileValidationUtils.validateFileExtension(fileName, LOGGER);
            if (extensionError != null) {
                return errorResponse(extensionError, Constants.ERROR_INVALID_EXTENSION);
            }

            byte[] content = downloadResult.content();
            double fileSizeKB = content.length / 1024.0;
            LOGGER.debug("Downloaded {} bytes ({} KB) for file: '{}'", content.length, String.format("%.2f", fileSizeKB), fileName);

            String sizeError = FileValidationUtils.validateFileSize(content.length, fileName, LOGGER);
            if (sizeError != null) {
                return errorResponse(sizeError, Constants.ERROR_FILE_TOO_LARGE);
            }

            tempFile = createTempFile(fileName, content);
            app.krista.model.base.File kristaFile = mediaClient.toKristaFile(tempFile, fileName);

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("Successfully downloaded file '{}' ({} KB) in {}ms", fileName, String.format("%.2f", fileSizeKB), duration);

            return successResponse(fileName, kristaFile, content.length);

        } catch (java.nio.file.InvalidPathException e) {
            // Defensive catch: sanitization in createTempFile should prevent this,
            // but guards against non-UTF-8 locale environments (sun.jnu.encoding=US-ASCII).
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.error("Invalid file path for '{}' after {}ms: {}", filePath, duration, e.getMessage());
            return errorResponse(
                "The file name contains characters that this server cannot store on disk. "
                    + "Please rename the file in Azure Files to use standard ASCII characters and try again.",
                "INVALID_FILENAME"
            );
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LOGGER.error("Unexpected error downloading file '{}' after {}ms: {}", filePath, duration, e.getMessage(), e);
            return errorResponse(
                "An unexpected error occurred while downloading your file. Please try again in a few minutes. "
                    + "If the problem continues, please contact support and mention you were downloading from Azure Files.",
                Constants.ERROR_UNEXPECTED
            );
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    // ==================== Private Helpers ====================

    private DownloadResult downloadFile(String filePath) {
        try {
            String url = restClient.buildDownloadUrl(filePath);
            LOGGER.debug("Downloading file from Azure: '{}'", filePath);

            HttpResponse<byte[]> response = restClient.sendGetByte(url);
            int statusCode = response.statusCode();

            if (statusCode == Constants.HTTP_OK) {
                return DownloadResult.success(response.body());
            }

            AzureHttpErrorHandler.ErrorResponse err = AzureHttpErrorHandler.handleHttpError(statusCode, filePath, LOGGER);
            return DownloadResult.error(err.message(), err.errorCode());

        } catch (java.net.http.HttpTimeoutException e) {
            AzureHttpErrorHandler.ErrorResponse err = AzureHttpErrorHandler.handleTimeout(filePath, LOGGER);
            return DownloadResult.error(err.message(), err.errorCode());
        } catch (java.io.IOException e) {
            LOGGER.error("IO error downloading file '{}': {}", filePath, e.getMessage(), e);
            String msg = e.getMessage().toLowerCase();
            if (msg.contains("file too large")) {
                return DownloadResult.error(
                    String.format("The file is too large to download (%d MB limit). Please compress it or use a smaller file.",
                        Constants.MAX_FILE_SIZE_BYTES_TECHNICAL / (1024 * 1024)),
                    Constants.ERROR_FILE_TOO_LARGE
                );
            } else if (msg.contains("connection")) {
                AzureHttpErrorHandler.ErrorResponse err = AzureHttpErrorHandler.handleConnectionError(filePath, LOGGER);
                return DownloadResult.error(err.message(), err.errorCode());
            }
            return DownloadResult.error(
                "There was a problem downloading the file from Azure Files. Please check your connection and try again.",
                Constants.ERROR_UNEXPECTED
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Download interrupted for file '{}': {}", filePath, e.getMessage());
            return DownloadResult.error(
                "The file download was interrupted. Please try downloading the file again.",
                Constants.ERROR_UNEXPECTED
            );
        } catch (Exception e) {
            AzureHttpErrorHandler.ErrorResponse err = AzureHttpErrorHandler.handleUnexpectedError(filePath, e, LOGGER);
            return DownloadResult.error(err.message(), err.errorCode());
        }
    }

    /**
     * Creates a temp file with an ASCII-safe on-disk name derived from the original filename.
     * The original filename is preserved by the caller for Krista display purposes.
     */
    private java.io.File createTempFile(String fileName, byte[] content) throws IOException {
        int dotIndex = fileName.lastIndexOf('.');
        String nameWithoutExt = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;
        String extension = (dotIndex > 0 && dotIndex < fileName.length() - 1) ? fileName.substring(dotIndex) : "";

        String safePrefix = FilenameUtil.sanitizeForFs(nameWithoutExt);
        String safeExtension = FilenameUtil.sanitizeForFs(extension);
        if (safePrefix.length() < 3) {
            safePrefix = "azfile-" + safePrefix;
        }

        java.io.File tempFile = Files.createTempFile(safePrefix, safeExtension).toFile();
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            outputStream.write(content);
        } catch (IOException e) {
            if (tempFile.exists() && !tempFile.delete()) {
                LOGGER.warn("Failed to delete temp file after write error: {}", tempFile.getAbsolutePath());
            }
            throw e;
        }

        LOGGER.debug("Temporary file created: '{}' ({} bytes)", tempFile.getAbsolutePath(), content.length);
        return tempFile;
    }

    private void cleanupTempFile(java.io.File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            try {
                if (tempFile.delete()) {
                    LOGGER.debug("Temporary file deleted: '{}'", tempFile.getAbsolutePath());
                } else {
                    LOGGER.warn("Failed to delete temporary file: '{}'", tempFile.getAbsolutePath());
                }
            } catch (SecurityException e) {
                LOGGER.error("Security exception deleting temporary file '{}': {}", tempFile.getAbsolutePath(), e.getMessage());
            }
        }
    }

    private Map<String, Object> successResponse(String fileName, app.krista.model.base.File kristaFile, long fileSizeBytes) {
        String fileSizeFormatted = String.format("%.2f KB", fileSizeBytes / 1024.0);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.KEY_FILENAME, fileName);
        response.put(Constants.KEY_KRISTA_FILE, kristaFile);
        response.put(Constants.KEY_FILE_SIZE_KB, fileSizeFormatted);
        LOGGER.debug("Success response: file='{}', size={}", fileName, fileSizeFormatted);
        return response;
    }

    private Map<String, Object> errorResponse(String message, String errorCode) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put(Constants.KEY_ERROR, message);
        errorMap.put(Constants.KEY_ERROR_CODE, errorCode);
        LOGGER.debug("Error response: code={}", errorCode);
        return errorMap;
    }

    // ==================== Inner Record ====================

    private record DownloadResult(boolean success, byte[] content, String errorMessage, String errorCode) {
        static DownloadResult success(byte[] content) {
            return new DownloadResult(true, content, null, null);
        }
        static DownloadResult error(String errorMessage, String errorCode) {
            return new DownloadResult(false, null, errorMessage, errorCode);
        }
    }
}
