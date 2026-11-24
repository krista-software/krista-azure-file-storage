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
 * <p>Supports non-recursive and recursive traversal, extension filtering, parallel
 * processing, result limits, and XXE-protected XML parsing.
 * Never throws exceptions — errors are surfaced via {@link AzureFilesException}.
 */
@Service
public class AzureFileListingServiceImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFileListingServiceImpl.class);

    // Shared thread pool — prevents resource leaks across instances
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(
        Math.max(4, Runtime.getRuntime().availableProcessors()),
        r -> {
            Thread t = new Thread(r, "azure-listing-worker");
            t.setDaemon(true);
            return t;
        }
    );

    private final AzureFileRestClient restClient;

    @Inject
    public AzureFileListingServiceImpl(AzureFileRestClient restClient) {
        this.restClient = restClient;
    }

    // ==================== Public API ====================

    public List<String> listFiles(String basePath, String extensionFilter, boolean recursive) {
        LOGGER.info("Listing files: path='{}', filter='{}', recursive={}", basePath, extensionFilter, recursive);
        long startTime = System.currentTimeMillis();

        ListingResult result = listEntries(basePath, recursive, false, extensionFilter);
        long duration = System.currentTimeMillis() - startTime;

        if (!result.success()) {
            LOGGER.error("Failed to list files in {}ms: {} (code: {})", duration, result.errorMessage(), result.errorCode());
            throw new AzureFilesException(result.errorMessage(), result.errorCode());
        }

        LOGGER.info("Listed {} files in {}ms", result.entries().size(), duration);
        return result.entries();
    }

    public List<String> listDirectories(String basePath, boolean recursive) {
        LOGGER.info("Listing directories: path='{}', recursive={}", basePath, recursive);
        long startTime = System.currentTimeMillis();

        ListingResult result = listEntries(basePath, recursive, true, null);
        long duration = System.currentTimeMillis() - startTime;

        if (!result.success()) {
            LOGGER.error("Failed to list directories in {}ms: {} (code: {})", duration, result.errorMessage(), result.errorCode());
            throw new AzureFilesException(result.errorMessage(), result.errorCode());
        }

        LOGGER.info("Listed {} directories in {}ms", result.entries().size(), duration);
        return result.entries();
    }

    // ==================== Private Helpers ====================

    private ListingResult listEntries(String basePath, boolean recursive, boolean directoriesOnly, String extensionFilter) {
        try {
            String normalizedPath = PathUtils.normalizePath(basePath);
            LOGGER.debug("Normalized path: '{}'", normalizedPath);

            Set<String> extensions = FileValidationUtils.parseExtensionFilter(extensionFilter);
            if (extensions != null) {
                LOGGER.debug("Extension filter: {}", extensions);
            }

            ListingContext context = new ListingContext(directoriesOnly, extensions);

            List<String> entries;
            if (recursive) {
                entries = traverseRecursivelyWithTimeout(normalizedPath, context);
            } else {
                // For non-recursive, propagate errors (404, 401, 403, etc.) as failures.
                FetchResult fetchResult = fetchEntriesInternal(normalizedPath, context);
                if (!fetchResult.success()) {
                    return ListingResult.error(fetchResult.errorMessage(), fetchResult.errorCode());
                }
                entries = fetchResult.entries();
            }

            boolean truncated = entries.size() >= Constants.MAX_LISTING_RESULTS;
            if (truncated) {
                LOGGER.warn("Results truncated to {} entries (limit: {})", Constants.MAX_LISTING_RESULTS, Constants.MAX_LISTING_RESULTS);
                entries = entries.subList(0, Constants.MAX_LISTING_RESULTS);
            }

            return ListingResult.success(entries, context.getWarnings(), truncated);

        } catch (TimeoutException e) {
            LOGGER.error("Listing operation timed out after {} seconds", Constants.LISTING_TIMEOUT_SECONDS);
            return ListingResult.error(
                String.format("The operation timed out after %d seconds because the directory structure is very large. "
                    + "Please try: 1) Turn off 'Recursive Search', 2) Use a more specific folder path, or 3) Add file extension filters.",
                    Constants.LISTING_TIMEOUT_SECONDS),
                Constants.ERROR_TIMEOUT
            );
        } catch (Exception e) {
            LOGGER.error("Unexpected error during listing: {}", e.getMessage(), e);
            return ListingResult.error(
                "An unexpected error occurred while browsing your files. Please try again in a few minutes.",
                Constants.ERROR_UNEXPECTED
            );
        }
    }

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

    private void traverseInternal(String currentPath, List<String> results, int depth, ListingContext context) {
        if (depth >= Constants.MAX_TRAVERSAL_DEPTH) {
            context.addWarning(String.format("Maximum depth (%d) reached at path: %s", Constants.MAX_TRAVERSAL_DEPTH, currentPath));
            return;
        }

        if (results.size() >= Constants.MAX_LISTING_RESULTS) {
            return;
        }

        FetchResult fetchResult = fetchEntriesInternal(currentPath, context);
        if (fetchResult.success()) {
            results.addAll(fetchResult.entries());
        } else {
            context.addWarning(String.format("Failed to list '%s': %s", currentPath, fetchResult.errorMessage()));
        }

        // When listing files, fetch subdirectory names using a dir-only context so we can recurse.
        // When listing directories, reuse the same context (already dir-only).
        ListingContext subDirContext = context.isDirectoriesOnly() ? context : new ListingContext(true, null);
        FetchResult subDirsResult = fetchEntriesInternal(currentPath, subDirContext);

        if (subDirsResult.success()) {
            List<CompletableFuture<Void>> asyncTasks = subDirsResult.entries().stream()
                    .map(subDir -> CompletableFuture.runAsync(
                            () -> traverseInternal(subDir, results, depth + 1, context),
                            EXECUTOR_SERVICE))
                    .collect(Collectors.toList());
            waitForTasks(asyncTasks, context);
        }
    }

    // Used only during recursive traversal — errors become warnings, not failures
    private List<String> fetchEntriesWithErrorHandling(String path, ListingContext context) {
        FetchResult result = fetchEntriesInternal(path, context);
        return result.success() ? result.entries() : Collections.emptyList();
    }

    private FetchResult fetchEntriesInternal(String path, ListingContext context) {
        try {
            String baseUrl = restClient.buildBaseUrl(path);
            String url = restClient.appendSasToken(baseUrl + Constants.AZURE_FILES_LIST_QUERY);
            LOGGER.debug("Fetching entries from: '{}'", path);

            HttpResponse<String> response = restClient.sendGetString(url);

            if (response == null) {
                return FetchResult.error("Received null response from Azure Files API", Constants.ERROR_UNEXPECTED);
            }

            int statusCode = response.statusCode();
            if (statusCode != Constants.HTTP_OK) {
                AzureHttpErrorHandler.ErrorResponse err = AzureHttpErrorHandler.handleHttpError(statusCode, path, LOGGER);
                return FetchResult.error(err.message(), err.errorCode());
            }

            List<String> entries = parseEntriesFromXml(response.body(), context.isDirectoriesOnly(), path);

            if (!context.isDirectoriesOnly() && context.getExtensions() != null) {
                entries = entries.stream()
                        .filter(file -> FileValidationUtils.matchesExtension(file, context.getExtensions()))
                        .collect(Collectors.toList());
            }

            return FetchResult.success(entries);

        } catch (java.net.http.HttpTimeoutException e) {
            LOGGER.error("Timeout fetching entries for '{}': {}", path, e.getMessage());
            return FetchResult.error("Request timed out", Constants.ERROR_TIMEOUT);
        } catch (java.io.IOException e) {
            LOGGER.error("IO error fetching entries for '{}': {}", path, e.getMessage());
            return FetchResult.error("Network error occurred", Constants.ERROR_CONNECTION_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while fetching entries for '{}': {}", path, e.getMessage());
            return FetchResult.error("Operation interrupted", Constants.ERROR_UNEXPECTED);
        } catch (Exception e) {
            LOGGER.error("Unexpected error fetching entries for '{}': {}", path, e.getMessage(), e);
            return FetchResult.error("Unexpected error: " + e.getMessage(), Constants.ERROR_UNEXPECTED);
        }
    }

    private void waitForTasks(List<CompletableFuture<Void>> tasks, ListingContext context) {
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            context.addWarning("Some folders couldn't be accessed. The results may be incomplete.");
        } catch (Exception e) {
            context.addWarning("Some folders couldn't be processed due to technical issues. The results may be incomplete.");
        }
    }

    private List<String> parseEntriesFromXml(String xml, boolean directoriesOnly, String basePath) {
        List<String> results = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = directoriesOnly ? "//Entries/Directory/Name" : "//Entries/File/Name";
            NodeList nodes = (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);

            for (int i = 0; i < nodes.getLength(); i++) {
                String entryName = nodes.item(i).getTextContent();
                if (entryName != null && !entryName.trim().isEmpty()) {
                    results.add(PathUtils.buildFullPath(basePath, entryName));
                }
            }

        } catch (Exception e) {
            LOGGER.error("Failed to parse Azure XML response: {}", e.getMessage(), e);
        }
        return results;
    }

    // ==================== Inner Classes ====================

    /** Mutable context for a listing operation — holds config and accumulates warnings. */
    private static class ListingContext {
        private final boolean directoriesOnly;
        private final Set<String> extensions;
        private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());

        ListingContext(boolean directoriesOnly, Set<String> extensions) {
            this.directoriesOnly = directoriesOnly;
            this.extensions = extensions;
        }

        boolean isDirectoriesOnly() { return directoriesOnly; }
        Set<String> getExtensions() { return extensions; }
        List<String> getWarnings() { return new ArrayList<>(warnings); }

        void addWarning(String warning) {
            warnings.add(warning);
            LOGGER.warn("Listing warning: {}", warning);
        }
    }

    private record ListingResult(boolean success, List<String> entries, String errorMessage,
                                 String errorCode, List<String> warnings, boolean truncated) {
        ListingResult {
            entries  = entries  != null ? entries  : Collections.emptyList();
            warnings = warnings != null ? warnings : Collections.emptyList();
        }
        static ListingResult success(List<String> entries, List<String> warnings, boolean truncated) {
            return new ListingResult(true, entries, null, null, warnings, truncated);
        }
        static ListingResult error(String errorMessage, String errorCode) {
            return new ListingResult(false, null, errorMessage, errorCode, null, false);
        }
    }

    private record FetchResult(boolean success, List<String> entries, String errorMessage, String errorCode) {
        FetchResult {
            entries = entries != null ? entries : Collections.emptyList();
        }
        static FetchResult success(List<String> entries) {
            return new FetchResult(true, entries, null, null);
        }
        static FetchResult error(String errorMessage, String errorCode) {
            return new FetchResult(false, null, errorMessage, errorCode);
        }
    }
}
