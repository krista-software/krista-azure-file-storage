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

package com.krista.extension.azure.files.client;

import com.krista.extension.azure.files.AzureFilesAttributes;
import com.krista.extension.azure.files.util.Constants;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * REST client for Azure Files API operations using SAS token authentication.
 * Provides methods for file downloads, directory listings, and connection testing.
 *
 * <p>Features:
 * <ul>
 *   <li>HTTP/2 support for improved performance</li>
 *   <li>Automatic retry logic with exponential backoff</li>
 *   <li>File size validation before download</li>
 *   <li>Comprehensive logging with SAS token sanitization</li>
 * </ul>
 */
@Service
public class AzureFileRestClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFileRestClient.class);

    // Shared HTTP client for all instances - prevents resource leaks
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(Constants.CONNECTION_TIMEOUT_SECONDS))
            .version(HttpClient.Version.HTTP_2)
            .build();

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 1000L;

    // URL encoding constants
    private static final String SPACE_ENCODING = "%20";
    private static final String PLUS_SIGN = "+";

    // SAS token constants
    private static final String SAS_SIGNATURE_PARAM = "sig=";
    private static final String REDACTED_TOKEN = "sig=***REDACTED***";
    private static final String QUERY_SEPARATOR = "?";
    private static final String PARAM_SEPARATOR = "&";

    private final AzureFilesAttributes attributes;

    /**
     * Constructs a new Azure Files REST client with the provided configuration attributes.
     *
     * @param attributes Azure Files configuration including storage account, file share, and SAS token
     */
    @Inject
    public AzureFileRestClient(AzureFilesAttributes attributes) {
        this.attributes = attributes;
    }

    // ==================== Public API Methods ====================

    /**
     * Downloads file content as byte array with automatic size validation and retry logic.
     * Performs a HEAD request first to check file size before downloading.
     *
     * @param url fully qualified URL including SAS token
     * @return HTTP response containing file bytes
     * @throws IOException          if file exceeds size limit or download fails after retries
     * @throws InterruptedException if the operation is interrupted
     */
    public HttpResponse<byte[]> sendGetByte(String url) throws IOException, InterruptedException {
        String sanitizedUrl = sanitizeUrlForLogging(url);
        LOGGER.debug("Initiating byte download from: {}", sanitizedUrl);

        long startTime = System.currentTimeMillis();

        try {
            validateFileSizeBeforeDownload(url);
            HttpRequest request = buildGetRequest(url);
            HttpResponse<byte[]> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofByteArray());

            logSuccessfulByteDownload(response, startTime);
            return response;

        } catch (Exception cause) {
            logFailedRequest(startTime, cause);
            throw cause;
        }
    }

    /**
     * Fetches content as string with automatic retry logic.
     * Typically used for XML responses from directory listing operations.
     *
     * @param url fully qualified URL including SAS token
     * @return HTTP response containing string content
     * @throws IOException          if request fails after retries
     * @throws InterruptedException if the operation is interrupted
     */
    public HttpResponse<String> sendGetString(String url) throws IOException, InterruptedException {
        String sanitizedUrl = sanitizeUrlForLogging(url);
        LOGGER.debug("Initiating string request to: {}", sanitizedUrl);

        long startTime = System.currentTimeMillis();

        try {
            HttpRequest request = buildGetRequest(url);
            HttpResponse<String> response = sendWithRetry(request, HttpResponse.BodyHandlers.ofString());

            logSuccessfulStringRequest(response, startTime);
            return response;

        } catch (Exception cause) {
            logFailedRequest(startTime, cause);
            throw cause;
        }
    }

    /**
     * Builds the base URL for Azure Files operations without query parameters.
     * Properly encodes each path segment to preserve directory structure.
     *
     * @param path file or directory path (cause.g., "documents/reports/file.pdf")
     * @return base URL with encoded path segments
     */
    public String buildBaseUrl(String path) {
        String baseUrl = buildStorageAccountUrl();

        if (isPathEmpty(path)) {
            return baseUrl;
        }

        return baseUrl + encodePath(path);
    }

    /**
     * Builds the complete download URL for a specific file including SAS token.
     *
     * @param filePath file path relative to share root
     * @return complete URL with encoded path and SAS token
     */
    public String buildDownloadUrl(String filePath) {
        String baseUrl = buildStorageAccountUrl();
        String encodedPath = isPathEmpty(filePath) ? "" : encodePath(filePath);
        String completeUrl = baseUrl + encodedPath;
        String finalUrl = appendSasToken(completeUrl);

        LOGGER.debug("Built download URL for file: '{}'", filePath);
        return finalUrl;
    }

    /**
     * Appends SAS token to the given URL with proper query parameter formatting.
     * Cleans and normalizes the token before appending.
     *
     * @param url base URL without SAS token
     * @return URL with appended SAS token
     */
    public String appendSasToken(String url) {
        String cleanedToken = cleanSasToken(attributes.getSasToken());
        String separator = url.contains(QUERY_SEPARATOR) ? PARAM_SEPARATOR : QUERY_SEPARATOR;
        return url + separator + cleanedToken;
    }

    /**
     * Tests connectivity to Azure Files by attempting to retrieve share metadata.
     * Validates both network connectivity and SAS token permissions.
     *
     * @return true if connection successful, false otherwise
     */
    public boolean testConnection() {
        try {
            String testUrl = buildShareMetadataUrl();
            HttpResponse<String> response = sendGetString(testUrl);

            if (response.statusCode() == Constants.HTTP_OK) {
                LOGGER.info("Connection test successful - HTTP {}", response.statusCode());
                return true;
            }

            LOGGER.error("Connection test failed - HTTP {}", response.statusCode());
            return false;

        } catch (IllegalArgumentException cause) {
            LOGGER.error("Connection test failed - invalid arguments: {}", cause.getMessage());
            return false;
        } catch (Exception cause) {
            LOGGER.error("Connection test failed - {}: {}", cause.getClass().getSimpleName(), cause.getMessage());
            return false;
        }
    }


    /**
     * Sends HTTP request with automatic retry logic and exponential backoff.
     * Retries up to MAX_RETRY_ATTEMPTS times with increasing delays between attempts.
     */
    private <T> HttpResponse<T> sendWithRetry(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        IOException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return HTTP_CLIENT.send(request, handler);
            } catch (IOException cause) {
                lastException = cause;
                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    long backoffMs = BASE_BACKOFF_MS * (attempt + 1);
                    LOGGER.warn("Request failed (attempt {}/{}): {}. Retrying in {}ms...",
                        attempt + 1, MAX_RETRY_ATTEMPTS, cause.getMessage(), backoffMs);
                    Thread.sleep(backoffMs);
                }
            }
        }

        LOGGER.error("Request failed after {} attempts", MAX_RETRY_ATTEMPTS);
        throw lastException;
    }

    /**
     * Validates file size before downloading to prevent OutOfMemoryError.
     * Sends a HEAD request to check Content-Length header.
     */
    private void validateFileSizeBeforeDownload(String url) throws IOException, InterruptedException {
        HttpRequest headRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(Constants.OPERATION_TIMEOUT_SECONDS))
                .build();

        HttpResponse<Void> headResponse = HTTP_CLIENT.send(headRequest, HttpResponse.BodyHandlers.discarding());
        long contentLength = headResponse.headers().firstValueAsLong("Content-Length").orElse(-1);

        if (contentLength > Constants.MAX_FILE_SIZE_BYTES_TECHNICAL) {
            String message = String.format("File too large: %d bytes (max: %d bytes)",
                contentLength, Constants.MAX_FILE_SIZE_BYTES_TECHNICAL);
            throw new IOException(message);
        }

        LOGGER.debug("File size validation passed: {} bytes", contentLength);
    }

    /**
     * Builds a standard GET request with configured timeout.
     */
    private HttpRequest buildGetRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Constants.OPERATION_TIMEOUT_SECONDS))
                .GET()
                .build();
    }

    /**
     * Builds the base storage account URL without path or query parameters.
     */
    private String buildStorageAccountUrl() {
        return String.format(Constants.AZURE_FILES_ENDPOINT,
                attributes.getStorageAccountName().toLowerCase(),
                attributes.getFileShareName().toLowerCase());
    }

    /**
     * Builds URL for share metadata endpoint used in connection testing.
     */
    private String buildShareMetadataUrl() {
        String baseUrl = buildStorageAccountUrl();
        String testUrl = baseUrl + Constants.AZURE_FILES_SHARE_METADATA;
        return appendSasToken(testUrl);
    }

    /**
     * Encodes file path by encoding each segment separately to preserve directory structure.
     * Splits path by forward slash and encodes each segment individually.
     */
    private String encodePath(String path) {
        String[] segments = path.split(Constants.PATH_SEPARATOR);
        StringBuilder encodedPath = new StringBuilder();

        for (String segment : segments) {
            if (!segment.isEmpty()) {
                String encoded = URLEncoder.encode(segment, StandardCharsets.UTF_8)
                        .replace(PLUS_SIGN, SPACE_ENCODING);
                encodedPath.append(Constants.PATH_SEPARATOR).append(encoded);
            }
        }

        return encodedPath.toString();
    }

    /**
     * Checks if path is null or empty after trimming whitespace.
     */
    private boolean isPathEmpty(String path) {
        return path == null || path.trim().isEmpty();
    }

    /**
     * Cleans and normalizes SAS token by removing invisible characters and leading separators.
     */
    private String cleanSasToken(String token) {
        String cleaned = token.trim()
                .replace("\u00A0", "")  // Non-breaking space
                .replaceAll("[\\p{Zs}]", "")  // All Unicode spaces
                .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");  // Control characters except newlines/tabs

        // Remove leading ? or & if present
        if (cleaned.startsWith(QUERY_SEPARATOR) || cleaned.startsWith(PARAM_SEPARATOR)) {
            cleaned = cleaned.substring(1);
        }

        return cleaned;
    }

    /**
     * Sanitizes URL for logging by redacting SAS token signature.
     * Prevents sensitive token information from appearing in logs.
     */
    private String sanitizeUrlForLogging(String url) {
        int tokenStart = url.indexOf(SAS_SIGNATURE_PARAM);
        if (tokenStart > 0) {
            return url.substring(0, tokenStart) + REDACTED_TOKEN;
        }
        return url;
    }

    /**
     * Logs successful byte download with response details.
     */
    private void logSuccessfulByteDownload(HttpResponse<byte[]> response, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("Byte download completed: status={}, duration={}ms, size={} bytes",
            response.statusCode(), duration, response.body().length);
    }

    /**
     * Logs successful string request with response details.
     */
    private void logSuccessfulStringRequest(HttpResponse<String> response, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("String request completed: status={}, duration={}ms, length={} chars",
            response.statusCode(), duration, response.body().length());
    }

    /**
     * Logs failed request with duration and error message.
     */
    private void logFailedRequest(long startTime, Exception cause) {
        long duration = System.currentTimeMillis() - startTime;
        LOGGER.error("Request failed after {}ms: {} - {}",
            duration, cause.getClass().getSimpleName(), cause.getMessage());
    }
}
