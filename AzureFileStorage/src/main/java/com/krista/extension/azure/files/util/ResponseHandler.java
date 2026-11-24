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
        File file = (File) fileInfo.get(Constants.KEY_KRISTA_FILE);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.KEY_RESPONSE_FILE_INFO, fileInfo);
        responseMap.put(Constants.KEY_RESPONSE_SUCCESS, true);
        responseMap.put(Constants.KEY_RESPONSE_FILE, file);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates a file response for exception cases with error info
     */
    public static ExtensionResponse createExceptionFileResponse(Map<String, Object> fileInfo) {
        LOGGER.debug("Creating exception file response with file info: {}", fileInfo);
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(Constants.KEY_RESPONSE_FILE_INFO, fileInfo);
        responseMap.put(Constants.KEY_RESPONSE_SUCCESS, false);
        responseMap.put(Constants.KEY_RESPONSE_FILE, null);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates an error response for list operations with error details.
     */
    public static ExtensionResponse createListErrorResponse(
            String listName,
            String errorMessage,
            String errorCode) {

        LOGGER.error("Creating list error response: {} - {} (code: {})", listName, errorMessage, errorCode);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(listName, Collections.emptyList());
        responseMap.put(Constants.KEY_RESPONSE_SUCCESS, false);
        responseMap.put(Constants.KEY_RESPONSE_ERROR_MSG, errorMessage);
        responseMap.put(Constants.KEY_RESPONSE_ERROR_CODE_FIELD, errorCode);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates a success response for empty list (valid path, no results).
     */
    public static ExtensionResponse createEmptyListSuccessResponse(
            String listName,
            String message) {

        LOGGER.info("Creating empty list success response: {} - {}", listName, message);

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(listName, Collections.emptyList());
        responseMap.put(Constants.KEY_RESPONSE_SUCCESS, true);
        responseMap.put(Constants.KEY_RESPONSE_MESSAGE, message);

        return new ExtensionResponseBuilder().success(responseMap).build();
    }

    /**
     * Creates an empty file list success response.
     */
    public static ExtensionResponse createEmptyFileSuccessResponse() {
        return createEmptyListSuccessResponse(
            Constants.LIST_NAME_FILES,
            "No files found (directory is empty)"
        );
    }

    /**
     * Creates an empty directory list success response.
     */
    public static ExtensionResponse createEmptyDirectorySuccessResponse() {
        return createEmptyListSuccessResponse(
            Constants.LIST_NAME_DIRECTORIES,
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
        responseMap.put(Constants.KEY_RESPONSE_SUCCESS, true);
        return new ExtensionResponseBuilder().success(responseMap).build();
    }
}
