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

package com.krista.extension.azure.files.exception;

/**
 * Exception thrown when Azure Files operations fail.
 * 
 * <p>This exception is used to communicate operation failures from the service layer
 * to the catalog layer, allowing proper distinction between:
 * <ul>
 *   <li>Successful operations with empty results (no exception)</li>
 *   <li>Failed operations due to errors (exception thrown)</li>
 * </ul>
 * 
 * <p>The exception contains both a user-friendly error message and an error code
 * for programmatic error handling.
 * 
 * <p><b>Common Error Codes:</b>
 * <ul>
 *   <li>FILE_NOT_FOUND - Path doesn't exist (HTTP 404)</li>
 *   <li>ACCESS_FORBIDDEN - Insufficient permissions (HTTP 403)</li>
 *   <li>UNAUTHORIZED - Invalid or expired SAS token (HTTP 401)</li>
 *   <li>SERVICE_UNAVAILABLE - Azure service error (HTTP 500/502/503/504)</li>
 *   <li>TIMEOUT - Operation timed out</li>
 *   <li>CONNECTION_ERROR - Network/connection error</li>
 *   <li>UNEXPECTED_ERROR - Unexpected error occurred</li>
 * </ul>
 */
public class AzureFilesException extends RuntimeException {
    
    private final String errorCode;
    
    /**
     * Constructs a new Azure Files exception with the specified error message and code.
     * 
     * @param message user-friendly error message describing what went wrong
     * @param errorCode error code for programmatic error handling
     */
    public AzureFilesException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Returns the error code associated with this exception.
     * 
     * @return error code (e.g., "FILE_NOT_FOUND", "ACCESS_FORBIDDEN")
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    @Override
    public String toString() {
        return String.format("AzureFilesException[code=%s, message=%s]", errorCode, getMessage());
    }
}

