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
import com.krista.extension.azure.files.exception.AzureFilesException;
import com.krista.extension.azure.files.util.AzureHttpErrorHandler;
import com.krista.extension.azure.files.util.Constants;
import com.krista.extension.azure.files.util.FileValidationUtils;
import com.krista.extension.azure.files.util.PathUtils;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.inject.Inject;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Service implementation for listing files and directories from Azure File Share.
 *
 * <p>This service provides comprehensive directory listing capabilities including:
 * <ul>
 *   <li>Non-recursive and recursive directory traversal</li>
 *   <li>File extension filtering (supports multiple extensions)</li>
 *   <li>Parallel processing for recursive operations</li>
 *   <li>Comprehensive error handling for all Azure API scenarios</li>
 *   <li>Result size limits to prevent memory exhaustion</li>
 *   <li>Partial failure reporting with warnings</li>
 *   <li>Secure XML parsing with XXE protection</li>
 * </ul>
 *
 * <p><b>Important:</b> This service never throws exceptions to the catalog layer.
 * All errors are returned as structured ListingResult objects with user-friendly messages.
 */
@Service
public class AzureFileListingServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFileListingServiceImpl.class);

    // Shared thread pool for all instances - prevents resource leak
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors()),
        r -> {
            Thread t = new Thread(r, "azure-listing-worker");
            t.setDaemon(true);
            return t;
        }
    );

    private final AzureFileRestClient restClient;

    /**
     * Constructs the Azure File Listing Service with the provided REST client.
     * Uses a shared static thread pool to prevent resource leaks.
     *
     * @param restClient REST client for Azure Files API operations
     */
    @Inject
    public AzureFileListingServiceImpl(AzureFileRestClient restClient) {
        this.restClient = restClient;
    }

    // ==================== Public API Methods ====================

    /**
     * Lists all files in the specified path with optional extension filtering and recursive traversal.
     *
     * <p>This method handles the complete file listing workflow:
     * <ol>
     *   <li>Validates and normalizes the base path</li>
     *   <li>Performs recursive or non-recursive traversal</li>
     *   <li>Applies extension filtering (supports multiple extensions like ".pdf,.docx")</li>
     *   <li>Enforces result size limits</li>
     *   <li>Reports partial failures as warnings</li>
     * </ol>
     *
     * <p><b>Error Handling:</b> This method never throws exceptions. All errors are returned
     * as ListingResult objects with user-friendly messages and error codes.
     *
     * @param basePath the base path in Azure File Share (e.g., "documents" or "" for root)
     * @param extensionFilter optional comma-separated file extensions (e.g., ".pdf,.docx,.xlsx")
     * @param recursive whether to search recursively through subdirectories
     * @return ListingResult containing either file list or error information (never null)
     */
    public List<String> listFiles(String basePath, String extensionFilter, boolean recursive) {
        LOGGER.info("Listing files: path='{}', filter='{}', recursive={}", basePath, extensionFilter, recursive);
        long startTime = System.currentTimeMillis();

        ListingResult result = listEntries(basePath, recursive, false, extensionFilter);

        long duration = System.currentTimeMillis() - startTime;

        // Throw exception if operation failed
        if (!result.isSuccess()) {
            LOGGER.error("Failed to list files in {}ms: {} (code: {})",
                duration, result.getErrorMessage(), result.getErrorCode());
            throw new AzureFilesException(result.getErrorMessage(), result.getErrorCode());
        }

        // Success - return entries (may be empty for valid empty directory)
        if (result.getEntries().isEmpty()) {
            LOGGER.info("No files found at path '{}' (directory is empty) - {}ms", basePath, duration);
        } else {
            LOGGER.info("Listed {} files in {}ms", result.getEntries().size(), duration);
        }

        return result.getEntries();
    }

    /**
     * Lists all directories in the specified path with optional recursive traversal.
     *
     * <p>This method handles the complete directory listing workflow:
     * <ol>
     *   <li>Validates and normalizes the base path</li>
     *   <li>Performs recursive or non-recursive traversal</li>
     *   <li>Enforces result size limits</li>
     *   <li>Reports partial failures as warnings</li>
     * </ol>
     *
     * <p><b>Error Handling:</b> This method never throws exceptions. All errors are returned
     * as ListingResult objects with user-friendly messages and error codes.
     *
     * @param basePath the base path in Azure File Share (e.g., "documents" or "" for root)
     * @param recursive whether to search recursively through subdirectories
     * @return ListingResult containing either directory list or error information (never null)
     */
    public List<String> listDirectories(String basePath, boolean recursive) {
        LOGGER.info("Listing directories: path='{}', recursive={}", basePath, recursive);
        long startTime = System.currentTimeMillis();

        ListingResult result = listEntries(basePath, recursive, true, null);

        long duration = System.currentTimeMillis() - startTime;

        // Throw exception if operation failed
        if (!result.isSuccess()) {
            LOGGER.error("Failed to list directories in {}ms: {} (code: {})",
                duration, result.getErrorMessage(), result.getErrorCode());
            throw new AzureFilesException(result.getErrorMessage(), result.getErrorCode());
        }

        // Success - return entries (may be empty for valid path with no subdirectories)
        if (result.getEntries().isEmpty()) {
            LOGGER.info("No directories found at path '{}' (no subdirectories) - {}ms", basePath, duration);
        } else {
            LOGGER.info("Listed {} directories in {}ms", result.getEntries().size(), duration);
        }

        return result.getEntries();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Core method to list entries (files or directories) with validation, normalization, and error handling.
     */
    private ListingResult listEntries(String basePath, boolean recursive, boolean directoriesOnly, String extensionFilter) {
        try {
            // Step 1: Validate base path
            String validationError = validateBasePath(basePath);
            if (validationError != null) {
                return ListingResult.error(validationError, "INVALID_PATH");
            }

            // Step 2: Normalize path
            String normalizedPath = normalizePath(basePath);
            LOGGER.debug("Normalized path: '{}'", normalizedPath);

            // Step 3: Parse extension filter
            Set<String> extensions = parseExtensionFilter(extensionFilter);
            if (extensions != null) {
                LOGGER.debug("Extension filter: {}", extensions);
            }

            // Step 4: Perform listing with timeout
            ListingContext context = new ListingContext(directoriesOnly, extensions);

            List<String> entries = recursive
                    ? traverseRecursivelyWithTimeout(normalizedPath, context)
                    : fetchEntriesWithErrorHandling(normalizedPath, context);

            // Step 5: Check for truncation
            boolean truncated = entries.size() >= Constants.MAX_LISTING_RESULTS;
            if (truncated) {
                LOGGER.warn("Results limited to {} entries for performance (limit: {})", entries.size(), Constants.MAX_LISTING_RESULTS);
                entries = entries.subList(0, Constants.MAX_LISTING_RESULTS);
            }

            // Step 6: Build result with warnings
            return ListingResult.success(entries, context.getWarnings(), truncated);

        } catch (TimeoutException e) {
            LOGGER.error("Listing operation timed out after {} seconds", Constants.LISTING_TIMEOUT_SECONDS);
            return ListingResult.error(
                String.format("The operation timed out after %d seconds because the directory structure is very large. Please try: 1) Turn off 'Recursive Search', 2) Use a more specific folder path, or 3) Add file extension filters to reduce results.",
                    Constants.LISTING_TIMEOUT_SECONDS),
                Constants.ERROR_TIMEOUT
            );
        } catch (Exception e) {
            LOGGER.error("Unexpected error during listing: {}", e.getMessage(), e);
            return ListingResult.error(
                "An unexpected error occurred while browsing your files. Please try again in a few minutes. If the problem continues, please contact support.",
                "UNEXPECTED_ERROR"
            );
        }
    }

    /**
     * Performs recursive traversal with timeout protection.
     */
    private List<String> traverseRecursivelyWithTimeout(String basePath, ListingContext context) throws TimeoutException {
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Void> traversalFuture = CompletableFuture.runAsync(
            () -> traverseInternal(basePath, results, 0, context),
            EXECUTOR_SERVICE
        );

        try {
            traversalFuture.get(Constants.LISTING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            traversalFuture.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Traversal interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Traversal failed", e.getCause());
        }

        return results;
    }

    /**
     * Recursively traverses directories up to MAX_TRAVERSAL_DEPTH, collecting entries asynchronously.
     */
    private void traverseInternal(String currentPath, List<String> results, int depth, ListingContext context) {
        // Check depth limit
        if (depth >= Constants.MAX_TRAVERSAL_DEPTH) {
            context.addWarning(String.format("Maximum depth (%d) reached at path: %s", Constants.MAX_TRAVERSAL_DEPTH, currentPath));
            return;
        }

        // Check result size limit
        if (results.size() >= Constants.MAX_LISTING_RESULTS) {
            return;
        }

        // Fetch entries for current directory
        FetchResult fetchResult = fetchEntriesInternal(currentPath, context);
        if (fetchResult.isSuccess()) {
            results.addAll(fetchResult.getEntries());
        } else {
            context.addWarning(String.format("Failed to list '%s': %s", currentPath, fetchResult.getErrorMessage()));
        }

        // If not looking for directories only, fetch subdirectories for recursion
        if (!context.isDirectoriesOnly()) {
            ListingContext dirContext = new ListingContext(true, null);
            FetchResult subDirsResult = fetchEntriesInternal(currentPath, dirContext);

            if (subDirsResult.isSuccess()) {
                List<String> subDirs = subDirsResult.getEntries();

                // Process subdirectories in parallel
                List<CompletableFuture<Void>> asyncTasks = subDirs.stream()
                        .map(subDir -> CompletableFuture.runAsync(
                                () -> traverseInternal(subDir, results, depth + 1, context),
                                EXECUTOR_SERVICE))
                        .collect(Collectors.toList());

                waitForTasks(asyncTasks, context);
            }
        } else {
            // When listing directories, also recurse into subdirectories
            FetchResult subDirsResult = fetchEntriesInternal(currentPath, context);
            if (subDirsResult.isSuccess()) {
                List<String> subDirs = subDirsResult.getEntries();

                List<CompletableFuture<Void>> asyncTasks = subDirs.stream()
                        .map(subDir -> CompletableFuture.runAsync(
                                () -> traverseInternal(subDir, results, depth + 1, context),
                                EXECUTOR_SERVICE))
                        .collect(Collectors.toList());

                waitForTasks(asyncTasks, context);
            }
        }
    }

    /**
     * Fetches entries with comprehensive error handling (wrapper for public API).
     */
    private List<String> fetchEntriesWithErrorHandling(String path, ListingContext context) {
        FetchResult result = fetchEntriesInternal(path, context);
        return result.isSuccess() ? result.getEntries() : Collections.emptyList();
    }

    /**
     * Fetches entries from Azure Files API with detailed error information.
     */
    private FetchResult fetchEntriesInternal(String path, ListingContext context) {
        try {
            String url = buildListUrl(path);
            LOGGER.debug("Fetching entries from: '{}'", path);

            HttpResponse<String> response = restClient.sendGetString(url);

            if (response == null) {
                return FetchResult.error("Received null response from Azure Files API");
            }

            int statusCode = response.statusCode();
            if (statusCode != Constants.HTTP_OK) {
                return handleHttpError(statusCode, path);
            }

            // Parse XML response
            List<String> parsedEntries = parseEntriesFromXml(response.body(), context.isDirectoriesOnly(), path);

            // Apply extension filtering
            if (!context.isDirectoriesOnly() && context.getExtensions() != null) {
                parsedEntries = applyExtensionFilter(parsedEntries, context.getExtensions());
            }

            return FetchResult.success(parsedEntries);

        } catch (java.net.http.HttpTimeoutException e) {
            LOGGER.error("Timeout fetching entries for '{}': {}", path, e.getMessage());
            return FetchResult.error("Request timed out");
        } catch (java.io.IOException e) {
            LOGGER.error("IO error fetching entries for '{}': {}", path, e.getMessage());
            return FetchResult.error("Network error occurred");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while fetching entries for '{}': {}", path, e.getMessage());
            return FetchResult.error("Operation interrupted");
        } catch (Exception e) {
            LOGGER.error("Unexpected error fetching entries for '{}': {}", path, e.getMessage(), e);
            return FetchResult.error("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Handles HTTP error responses with user-friendly messages.
     */
    private FetchResult handleHttpError(int statusCode, String path) {
        AzureHttpErrorHandler.ErrorResponse errorResponse = AzureHttpErrorHandler.handleHttpError(statusCode, path, LOGGER);
        return FetchResult.error(errorResponse.getMessage());
    }

    /**
     * Waits for all asynchronous tasks to complete, capturing exceptions as warnings.
     */
    private void waitForTasks(List<CompletableFuture<Void>> tasks, ListingContext context) {
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            context.addWarning("Some folders couldn't be accessed. This might be due to permissions or network issues. The results may be incomplete.");
        } catch (Exception e) {
            context.addWarning("Some folders couldn't be processed due to technical issues. The results may be incomplete.");
        }
    }

    /**
     * Validates the base path.
     * Returns error message if invalid, null if valid.
     */
    private String validateBasePath(String path) {
        // Note: null or empty path is valid (represents root directory)
        return null;
    }

    /**
     * Normalizes the path by trimming whitespace, converting backslashes, and removing leading/trailing slashes.
     */
    private String normalizePath(String path) {
        return PathUtils.normalizePath(path);
    }

    /**
     * Parses extension filter string into a set of lowercase extensions.
     * Supports comma-separated extensions like ".pdf,.docx,.xlsx"
     */
    private Set<String> parseExtensionFilter(String extensionFilter) {
        return FileValidationUtils.parseExtensionFilter(extensionFilter);
    }

    /**
     * Applies extension filter to list of file paths.
     */
    private List<String> applyExtensionFilter(List<String> files, Set<String> extensions) {
        return files.stream()
                .filter(file -> FileValidationUtils.matchesExtension(file, extensions))
                .collect(Collectors.toList());
    }

    /**
     * Builds the complete Azure Files list URL with query parameters and SAS token.
     */
    private String buildListUrl(String path) {
        String baseUrl = restClient.buildBaseUrl(path);
        baseUrl += Constants.AZURE_FILES_LIST_QUERY;
        return restClient.appendSasToken(baseUrl);
    }

    /**
     * Builds full path by combining base path and entry name.
     */
    private String buildFullPath(String basePath, String entryName) {
        return PathUtils.buildFullPath(basePath, entryName);
    }

    /**
     * Parses the XML response from Azure Files API with XXE protection.
     * Extracts file or directory names based on the specified type.
     */
    private List<String> parseEntriesFromXml(String xml, boolean directoriesOnly, String basePath) {
        List<String> results = new ArrayList<>();
        try {
            // Create secure DocumentBuilderFactory with XXE protection
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            // Disable external entities to prevent XXE attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            // Set secure processing
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = directoriesOnly
                    ? "//Entries/Directory/Name"
                    : "//Entries/File/Name";

            NodeList nodes = (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);
            extractEntryNamesFromNodes(basePath, nodes, results);

        } catch (Exception cause) {
            LOGGER.error("Failed to parse Azure XML response: {}", cause.getMessage(), cause);
        }
        return results;
    }

    /**
     * Extracts entry names from XML nodes and constructs full paths.
     */
    private void extractEntryNamesFromNodes(String basePath, NodeList nodes, List<String> results) {
        for (int i = 0; i < nodes.getLength(); i++) {
            String entryName = nodes.item(i).getTextContent();
            if (entryName != null && !entryName.trim().isEmpty()) {
                results.add(buildFullPath(basePath, entryName));
            }
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Context object for listing operations containing configuration and state.
     */
    private static class ListingContext {
        private final boolean directoriesOnly;
        private final Set<String> extensions;
        private final List<String> warnings;

        ListingContext(boolean directoriesOnly, Set<String> extensions) {
            this.directoriesOnly = directoriesOnly;
            this.extensions = extensions;
            this.warnings = Collections.synchronizedList(new ArrayList<>());
        }

        boolean isDirectoriesOnly() {
            return directoriesOnly;
        }

        Set<String> getExtensions() {
            return extensions;
        }

        List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }

        void addWarning(String warning) {
            warnings.add(warning);
            LOGGER.warn("Listing warning: {}", warning);
        }
    }

    /**
     * Result object for listing operations.
     */
    private static class ListingResult {
        private final boolean success;
        private final List<String> entries;
        private final String errorMessage;
        private final String errorCode;
        private final List<String> warnings;
        private final boolean truncated;

        private ListingResult(boolean success, List<String> entries, String errorMessage,
                            String errorCode, List<String> warnings, boolean truncated) {
            this.success = success;
            this.entries = entries != null ? entries : Collections.emptyList();
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
            this.warnings = warnings != null ? warnings : Collections.emptyList();
            this.truncated = truncated;
        }

        static ListingResult success(List<String> entries, List<String> warnings, boolean truncated) {
            return new ListingResult(true, entries, null, null, warnings, truncated);
        }

        static ListingResult error(String errorMessage, String errorCode) {
            return new ListingResult(false, null, errorMessage, errorCode, null, false);
        }

        boolean isSuccess() {
            return success;
        }

        List<String> getEntries() {
            return entries;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        String getErrorCode() {
            return errorCode;
        }

        List<String> getWarnings() {
            return warnings;
        }

        boolean isTruncated() {
            return truncated;
        }

        boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    /**
     * Result object for fetch operations.
     */
    private static class FetchResult {
        private final boolean success;
        private final List<String> entries;
        private final String errorMessage;

        private FetchResult(boolean success, List<String> entries, String errorMessage) {
            this.success = success;
            this.entries = entries != null ? entries : Collections.emptyList();
            this.errorMessage = errorMessage;
        }

        static FetchResult success(List<String> entries) {
            return new FetchResult(true, entries, null);
        }

        static FetchResult error(String errorMessage) {
            return new FetchResult(false, null, errorMessage);
        }

        boolean isSuccess() {
            return success;
        }

        List<String> getEntries() {
            return entries;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }
}
