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

/**
 * Utility class for handling HTTP errors from Azure Files API.
 * 
 * <p>Provides centralized error handling logic that converts HTTP status codes
 * into user-friendly error messages and appropriate error codes.
 */
public final class AzureHttpErrorHandler {

    private AzureHttpErrorHandler() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates an error response for HTTP error status codes.
     * 
     * @param statusCode HTTP status code from Azure Files API
     * @param resourcePath the path that was being accessed (for logging)
     * @param logger logger instance for recording errors
     * @return ErrorResponse containing user-friendly message and error code
     */
    public static ErrorResponse handleHttpError(int statusCode, String resourcePath, Logger logger) {
        switch (statusCode) {
            case Constants.HTTP_NOT_FOUND:
                logger.warn("Resource not found (404): '{}'", resourcePath);
                return new ErrorResponse(
                    String.format("The file or directory '%s' was not found in your Azure File Share. Please check the spelling and make sure the path exists. You can use 'List Of Directories' to browse available folders.", resourcePath),
                    Constants.ERROR_FILE_NOT_FOUND
                );

            case Constants.HTTP_FORBIDDEN:
                logger.error("Access forbidden (403) for resource: '{}'", resourcePath);
                return new ErrorResponse(
                    "Access denied. Your SAS token doesn't have the required permissions for this operation. Please generate a new SAS token with 'Read' and 'List' permissions, or contact your Azure administrator.",
                    Constants.ERROR_ACCESS_FORBIDDEN
                );

            case Constants.HTTP_UNAUTHORIZED:
                logger.error("Unauthorized access (401) for resource: '{}'", resourcePath);
                return new ErrorResponse(
                    "Authentication failed. Your SAS token is either invalid, expired, or incorrectly formatted. Please generate a new SAS token from Azure Portal and update your extension configuration.",
                    Constants.ERROR_UNAUTHORIZED
                );

            case Constants.HTTP_SERVER_ERROR:
            case Constants.HTTP_BAD_GATEWAY:
            case Constants.HTTP_SERVICE_UNAVAILABLE:
            case Constants.HTTP_GATEWAY_TIMEOUT:
                logger.error("Azure server error ({}) for resource: '{}'", statusCode, resourcePath);
                return new ErrorResponse(
                    String.format("Azure Files service is temporarily unavailable (HTTP %d). This is usually a temporary issue. Please wait a few minutes and try again. If the problem persists, check Azure service status.", statusCode),
                    Constants.ERROR_SERVICE_UNAVAILABLE
                );

            default:
                logger.error("Unexpected HTTP status ({}) for resource: '{}'", statusCode, resourcePath);
                return new ErrorResponse(
                    String.format("An unexpected error occurred while communicating with Azure Files (HTTP %d). Please try again in a few minutes. If the issue continues, please contact support with this error code.", statusCode),
                    Constants.ERROR_UNEXPECTED
                );
        }
    }

    /**
     * Creates an error response for timeout exceptions.
     * 
     * @param resourcePath the path that was being accessed
     * @param logger logger instance for recording errors
     * @return ErrorResponse for timeout scenario
     */
    public static ErrorResponse handleTimeout(String resourcePath, Logger logger) {
        logger.error("Request timeout for resource: '{}'", resourcePath);
        return new ErrorResponse(
            "The operation timed out while accessing the file. This can happen with large files or slow network connections. Please try again, or if downloading a large file, consider using a smaller file.",
            Constants.ERROR_TIMEOUT
        );
    }

    /**
     * Creates an error response for connection/IO errors.
     * 
     * @param resourcePath the path that was being accessed
     * @param logger logger instance for recording errors
     * @return ErrorResponse for connection error scenario
     */
    public static ErrorResponse handleConnectionError(String resourcePath, Logger logger) {
        logger.error("Connection error for resource: '{}'", resourcePath);
        return new ErrorResponse(
            "Unable to connect to Azure Files. Please check your internet connection and ensure that your firewall allows access to Azure storage endpoints (*.file.core.windows.net). Try again in a few moments.",
            Constants.ERROR_CONNECTION_ERROR
        );
    }

    /**
     * Creates an error response for unexpected exceptions.
     * 
     * @param resourcePath the path that was being accessed
     * @param exception the exception that occurred
     * @param logger logger instance for recording errors
     * @return ErrorResponse for unexpected error scenario
     */
    public static ErrorResponse handleUnexpectedError(String resourcePath, Exception exception, Logger logger) {
        logger.error("Unexpected error for resource '{}': {}", resourcePath, exception.getMessage(), exception);
        return new ErrorResponse(
            "An unexpected error occurred while processing your request. Please try again in a few minutes. If the problem continues, please contact support and mention this error occurred while accessing Azure Files.",
            Constants.ERROR_UNEXPECTED
        );
    }

    /** Immutable error response containing message and error code. */
    public record ErrorResponse(String message, String errorCode) {}
}

