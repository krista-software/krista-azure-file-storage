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

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.ExtensionResponseBuilder;
import app.krista.model.base.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Utility class for creating standardized extension responses
 */
public class ResponseHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseHandler.class);

    private ResponseHandler() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a successful file response with file data and metadata
     */
    public static ExtensionResponse createFileSuccessResponse(Map<String, Object> fileInfo) {
        LOGGER.debug("Creating file response with file info: {}", fileInfo);
        File file = null;

        for (Map.Entry<String, Object> entry : fileInfo.entrySet()) {
            if ("kristafile".equals(entry.getKey())) {
                file = (File) entry.getValue();
            }
        }

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("File Info", fileInfo);
        responseMap.put("Success", true);
        responseMap.put("File", file);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates a file response for exception cases with error info
     */
    public static ExtensionResponse createExceptionFileResponse(Map<String, Object> fileInfo) {
        LOGGER.debug("Creating exception file response with file info: {}", fileInfo);
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("File Info", fileInfo);
        responseMap.put("Success", false);
        responseMap.put("File", null);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates an error response for list operations with error details.
     * Used when operations fail (404, 403, 401, 500, timeout, etc.)
     *
     * @param listName the name of the list field (e.g., "List Of Files")
     * @param errorMessage user-friendly error message
     * @param errorCode error code for programmatic handling
     * @return ExtensionResponse with Success=false and error details
     */
    public static ExtensionResponse createListErrorResponse(
            String listName,
            String errorMessage,
            String errorCode) {

        LOGGER.error("Creating list error response: {} - {} (code: {})",
            listName, errorMessage, errorCode);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(listName, Collections.emptyList());
        responseMap.put("Success", false);
        responseMap.put("Error", errorMessage);
        responseMap.put("ErrorCode", errorCode);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates a success response for empty list (valid path, no results).
     * Used when operation succeeds but directory is empty.
     *
     * @param listName the name of the list field (e.g., "List Of Files")
     * @param message informational message for the user
     * @return ExtensionResponse with Success=true and empty list
     */
    public static ExtensionResponse createEmptyListSuccessResponse(
            String listName,
            String message) {

        LOGGER.info("Creating empty list success response: {} - {}", listName, message);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(listName, Collections.emptyList());
        responseMap.put("Success", true);
        responseMap.put("Message", message);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates an empty file list success response.
     * Used when path is valid but contains no files.
     */
    public static ExtensionResponse createEmptyFileSuccessResponse() {
        return createEmptyListSuccessResponse(
            "List Of Files",
            "No files found (directory is empty)"
        );
    }

    /**
     * Creates an empty directory list success response.
     * Used when path is valid but contains no subdirectories.
     */
    public static ExtensionResponse createEmptyDirectorySuccessResponse() {
        return createEmptyListSuccessResponse(
            "List Of Directories",
            "No directories found (no subdirectories)"
        );
    }

    /**
     * Creates a response containing a list of items
     */
    public static ExtensionResponse createListResponse(String listName, List<String> items) {
        LOGGER.debug("Creating list response with {} items for field: {}", items.size(), listName);

        List<Map<String, Object>> itemList = new ArrayList<>();
        for (String item : items) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put(listName, item);
            itemList.add(itemMap);
        }

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(listName, itemList);
        responseMap.put("Success", true);
        return new ExtensionResponseBuilder().success(responseMap).build();
    }
}
