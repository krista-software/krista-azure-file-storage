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

package com.krista.extension.azure.files.util;

import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for file validation operations.
 * 
 * <p>Provides validation logic for file paths, extensions, and sizes
 * with user-friendly error messages.
 */
public final class FileValidationUtils {

    private FileValidationUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that a file path is not null or empty.
     * 
     * @param filePath the file path to validate
     * @param logger logger instance for recording validation failures
     * @return error message if invalid, null if valid
     */
    public static String validateFilePath(String filePath, Logger logger) {
        if (filePath == null || filePath.trim().isEmpty()) {
            logger.warn("File path validation failed: path is null or empty");
            return "File path is required. Please enter the complete path to your file (e.g., 'documents/report.pdf' or 'images/logo.png').";
        }
        return null;
    }

    /**
     * Validates that the file has an extension.
     *
     * <p>The extension whitelist has been intentionally removed — any file present
     * in Azure Files is considered downloadable. Format-specific safety (e.g.,
     * auto-zipping HTML / executable formats) is handled downstream by
     * {@code KristaMediaClient}.
     *
     * @param fileName the file name to validate
     * @param logger logger instance for recording validation failures
     * @return error message if no extension, null if valid
     */
    public static String validateFileExtension(String fileName, Logger logger) {
        String extension = PathUtils.extractFileExtension(fileName);

        if (extension.isEmpty()) {
            logger.warn("File extension validation failed: no extension found in '{}'", fileName);
            return String.format("The file '%s' doesn't have a file extension. Please ensure your file has a valid extension like .pdf, .docx, .xlsx, or .txt.", fileName);
        }

        return null;
    }

    /**
     * Validates file size against business limits.
     * 
     * @param fileSize the file size in bytes
     * @param fileName the file name (for error messages)
     * @param logger logger instance for recording validation failures
     * @return error message if too large or empty, null if valid
     */
    public static String validateFileSize(long fileSize, String fileName, Logger logger) {
        if (fileSize == 0) {
            logger.warn("File size validation failed: file '{}' is empty", fileName);
            return String.format("The file '%s' appears to be empty (0 bytes). Please check that the file contains data and try uploading it again to Azure Files.", fileName);
        }

        if (fileSize > Constants.MAX_FILE_SIZE_BYTES_BUSINESS) {
            long maxSizeMB = Constants.MAX_FILE_SIZE_BYTES_BUSINESS / (1024 * 1024);
            long fileSizeMB = fileSize / (1024 * 1024);
            logger.warn("File size validation failed: file '{}' is {} MB (max: {} MB)", fileName, fileSizeMB, maxSizeMB);
            return String.format("The file '%s' is too large (%d MB). The maximum file size for download is %d MB. Please use a smaller file or compress it before uploading to Azure Files.",
                fileName, fileSizeMB, maxSizeMB);
        }

        return null;
    }

    /**
     * Parses extension filter string into a set of lowercase extensions.
     * Supports comma-separated extensions like ".pdf,.docx,.xlsx" or "pdf,docx,xlsx"
     * 
     * @param extensionFilter comma-separated extension filter string
     * @return set of lowercase extensions with dots, or null if filter is empty
     */
    public static Set<String> parseExtensionFilter(String extensionFilter) {
        if (extensionFilter == null || extensionFilter.trim().isEmpty()) {
            return null;
        }

        Set<String> extensions = new HashSet<>();
        String[] parts = extensionFilter.split(Constants.EXTENSION_SEPARATOR);
        
        for (String part : parts) {
            String ext = part.trim().toLowerCase();
            if (!ext.isEmpty()) {
                // Ensure extension starts with dot
                if (!ext.startsWith(".")) {
                    ext = "." + ext;
                }
                extensions.add(ext);
            }
        }

        return extensions.isEmpty() ? null : extensions;
    }

    /**
     * Checks if a file matches any of the provided extensions.
     * 
     * @param filePath the file path to check
     * @param extensions set of allowed extensions (with dots, lowercase)
     * @return true if file matches any extension, false otherwise
     */
    public static boolean matchesExtension(String filePath, Set<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return true; // No filter means all files match
        }

        String fileExtension = PathUtils.extractFileExtension(filePath);
        return extensions.contains(fileExtension);
    }
}

