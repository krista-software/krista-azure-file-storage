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

/**
 * Utility class for path manipulation and normalization.
 * 
 * <p>Provides common path operations used across Azure Files services including
 * normalization, validation, and path component extraction.
 */
public final class PathUtils {

    private PathUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Normalizes a path by:
     * <ul>
     *   <li>Trimming whitespace</li>
     *   <li>Converting backslashes to forward slashes</li>
     *   <li>Removing leading slashes</li>
     *   <li>Removing trailing slashes (except for root)</li>
     * </ul>
     * 
     * @param path the path to normalize (can be null)
     * @return normalized path, or empty string if path is null/empty
     */
    public static String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }

        // Trim whitespace and convert backslashes to forward slashes
        String normalized = path.trim().replace(Constants.BACKSLASH, Constants.PATH_SEPARATOR);

        // Remove leading slash
        if (normalized.startsWith(Constants.PATH_SEPARATOR)) {
            normalized = normalized.substring(1);
        }

        // Remove trailing slash (but keep if it's the only character)
        if (normalized.endsWith(Constants.PATH_SEPARATOR) && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    /**
     * Extracts the file name from a full file path.
     * 
     * @param filePath the full file path
     * @return the file name (last component of the path)
     */
    public static String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }

        int lastSlashIndex = filePath.lastIndexOf(Constants.PATH_SEPARATOR);
        return lastSlashIndex >= 0 ? filePath.substring(lastSlashIndex + 1) : filePath;
    }

    /**
     * Extracts the file extension from a file name or path.
     * 
     * @param fileName the file name or path
     * @return the extension (including the dot), or empty string if no extension
     */
    public static String extractFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= fileName.length() - 1) {
            return "";
        }

        return fileName.substring(dotIndex).toLowerCase();
    }

    /**
     * Builds a full path by combining base path and entry name.
     * Handles path separator logic correctly.
     * 
     * @param basePath the base directory path
     * @param entryName the file or directory name to append
     * @return the combined full path
     */
    public static String buildFullPath(String basePath, String entryName) {
        if (basePath == null || basePath.isEmpty()) {
            return entryName;
        }
        
        if (entryName == null || entryName.isEmpty()) {
            return basePath;
        }

        return basePath.endsWith(Constants.PATH_SEPARATOR) 
            ? basePath + entryName 
            : basePath + Constants.PATH_SEPARATOR + entryName;
    }

    /**
     * Validates that a path is not null or empty (after trimming).
     * 
     * @param path the path to validate
     * @return true if path is valid (not null and not empty after trim), false otherwise
     */
    public static boolean isValidPath(String path) {
        return path != null && !path.trim().isEmpty();
    }

    /**
     * Checks if a file name has a valid extension.
     * 
     * @param fileName the file name to check
     * @return true if the file has an extension, false otherwise
     */
    public static boolean hasExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }

        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < fileName.length() - 1;
    }
}

