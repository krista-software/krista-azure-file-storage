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
 * Central constants repository for Azure Files extension.
 *
 * <p>This class contains all configuration constants, HTTP status codes,
 * file validation rules, and other shared constants used across the extension.
 */
public final class Constants {

    // ==================== SAS Token Authentication ====================

    /** Configuration key for Azure Storage Account Name */
    public static final String STORAGE_ACCOUNT_NAME = "Storage Account Name";

    /** Configuration key for Azure File Share Name */
    public static final String FILE_SHARE_NAME = "File Share Name";

    /** Configuration key for SAS Token */
    public static final String SAS_TOKEN = "SAS Token";

    /** Configuration key for SAS Token Expiry Date (auto-populated from SAS token 'se' parameter) */
    public static final String SAS_TOKEN_EXPIRY = "SAS Token Expiry";

    // ==================== Azure Files API Endpoints ====================

    /** Azure Files endpoint URL template: https://{account}.file.core.windows.net/{share} */
    public static final String AZURE_FILES_ENDPOINT = "https://%s.file.core.windows.net/%s";

    /** Query string for listing directory contents */
    public static final String AZURE_FILES_LIST_QUERY = "?restype=directory&comp=list";

    /** Query string for retrieving share metadata */
    public static final String AZURE_FILES_SHARE_METADATA = "?restype=share";

    // ==================== HTTP Client Configuration ====================

    /** Connection timeout in seconds for HTTP requests */
    public static final int CONNECTION_TIMEOUT_SECONDS = 10;

    /** Operation timeout in seconds for file downloads (2 minutes) */
    public static final int OPERATION_TIMEOUT_SECONDS = 120;

    /** Operation timeout in seconds for listing operations (5 minutes) */
    public static final long LISTING_TIMEOUT_SECONDS = 300;

    // ==================== File Size Limits ====================

    /** Technical maximum file size limit (100 MB) - enforced by REST client */
    public static final long MAX_FILE_SIZE_BYTES_TECHNICAL = 100 * 1024 * 1024;

    /** Business maximum file size limit (50 MB) - enforced by download service */
    public static final long MAX_FILE_SIZE_BYTES_BUSINESS = 50 * 1024 * 1024;

    /** Maximum number of results for listing operations */
    public static final int MAX_LISTING_RESULTS = 10000;

    /** Maximum depth for recursive directory traversal */
    public static final int MAX_TRAVERSAL_DEPTH = 10;

    // ==================== HTTP Status Codes ====================

    /** HTTP 200 - OK */
    public static final int HTTP_OK = 200;

    /** HTTP 401 - Unauthorized (invalid or expired SAS token) */
    public static final int HTTP_UNAUTHORIZED = 401;

    /** HTTP 403 - Forbidden (insufficient permissions) */
    public static final int HTTP_FORBIDDEN = 403;

    /** HTTP 404 - Not Found (file or directory doesn't exist) */
    public static final int HTTP_NOT_FOUND = 404;

    /** HTTP 500 - Internal Server Error */
    public static final int HTTP_SERVER_ERROR = 500;

    /** HTTP 502 - Bad Gateway */
    public static final int HTTP_BAD_GATEWAY = 502;

    /** HTTP 503 - Service Unavailable */
    public static final int HTTP_SERVICE_UNAVAILABLE = 503;

    /** HTTP 504 - Gateway Timeout */
    public static final int HTTP_GATEWAY_TIMEOUT = 504;

    // ==================== Path Constants ====================

    /** Forward slash path separator */
    public static final String PATH_SEPARATOR = "/";

    /** Backslash (Windows-style separator) */
    public static final String BACKSLASH = "\\";

    /** Extension separator for multiple extensions */
    public static final String EXTENSION_SEPARATOR = ",";

    // ==================== Response Map Keys ====================

    /** Response key for file name */
    public static final String KEY_FILENAME = "filename";

    /** Response key for Krista file object */
    public static final String KEY_KRISTA_FILE = "kristafile";

    /** Response key for file size in KB (formatted as "XX.XX KB") */
    public static final String KEY_FILE_SIZE_KB = "filesizekb";

    /** Response key for error message */
    public static final String KEY_ERROR = "error";

    /** Response key for error code */
    public static final String KEY_ERROR_CODE = "errorCode";

    // ==================== ResponseHandler Keys ====================

    /** Response wrapper key for file info map */
    public static final String KEY_RESPONSE_FILE_INFO = "File Info";

    /** Response key for success flag */
    public static final String KEY_RESPONSE_SUCCESS = "Success";

    /** Response key for Krista file object in extension response */
    public static final String KEY_RESPONSE_FILE = "File";

    /** Response key for error message in list/file responses */
    public static final String KEY_RESPONSE_ERROR_MSG = "Error";

    /** Response key for error code in list/file responses */
    public static final String KEY_RESPONSE_ERROR_CODE_FIELD = "ErrorCode";

    /** Response key for informational message */
    public static final String KEY_RESPONSE_MESSAGE = "Message";

    /** List field name for file listing responses */
    public static final String LIST_NAME_FILES = "List Of Files";

    /** List field name for directory listing responses */
    public static final String LIST_NAME_DIRECTORIES = "List Of Directories";

    // ==================== Error Codes ====================

    /** Error code for invalid path */
    public static final String ERROR_INVALID_PATH = "INVALID_PATH";

    /** Error code for invalid file extension */
    public static final String ERROR_INVALID_EXTENSION = "INVALID_EXTENSION";

    /** Error code for file too large */
    public static final String ERROR_FILE_TOO_LARGE = "FILE_TOO_LARGE";

    /** Error code for file not found (HTTP 404) */
    public static final String ERROR_FILE_NOT_FOUND = "FILE_NOT_FOUND";

    /** Error code for access forbidden (HTTP 403) */
    public static final String ERROR_ACCESS_FORBIDDEN = "ACCESS_FORBIDDEN";

    /** Error code for unauthorized (HTTP 401) */
    public static final String ERROR_UNAUTHORIZED = "UNAUTHORIZED";

    /** Error code for service unavailable (HTTP 500/502/503/504) */
    public static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    /** Error code for timeout */
    public static final String ERROR_TIMEOUT = "TIMEOUT";

    /** Error code for connection error */
    public static final String ERROR_CONNECTION_ERROR = "CONNECTION_ERROR";

    /** Error code for unexpected error */
    public static final String ERROR_UNEXPECTED = "UNEXPECTED_ERROR";

    private Constants() {
        // Utility class - prevent instantiation
    }
}