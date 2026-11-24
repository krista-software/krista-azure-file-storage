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

import com.krista.extension.azure.files.service.AzureFileService;
import org.jvnet.hk2.annotations.Service;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

/**
 * Unified service implementation for Azure File Share operations.
 *
 * <p>This service acts as a facade that delegates to specialized service implementations:
 * <ul>
 *   <li>{@link AzureFileListingServiceImpl} - Handles file and directory listing operations</li>
 *   <li>{@link AzureFileDownloadServiceImpl} - Handles file download operations</li>
 * </ul>
 *
 * <p>This design provides:
 * <ul>
 *   <li>Clean separation of concerns - listing and download logic remain separate</li>
 *   <li>Single entry point for all Azure File operations</li>
 *   <li>Easy dependency injection with HK2</li>
 *   <li>Simplified testing and maintenance</li>
 * </ul>
 *
 * <p><b>Usage:</b> Inject this service in catalog operations or other components that need
 * to interact with Azure File Share. All methods delegate to the appropriate specialized service.
 *
 * @see AzureFileService
 * @see AzureFileListingServiceImpl
 * @see AzureFileDownloadServiceImpl
 */
@Service
public class AzureFileServiceImpl implements AzureFileService {

    private final AzureFileListingServiceImpl listingService;
    private final AzureFileDownloadServiceImpl downloadService;

    /**
     * Constructs the unified Azure File Service with specialized service implementations.
     *
     * @param listingService service for listing files and directories
     * @param downloadService service for downloading files
     */
    @Inject
    public AzureFileServiceImpl(AzureFileListingServiceImpl listingService, AzureFileDownloadServiceImpl downloadService) {
        this.listingService = listingService;
        this.downloadService = downloadService;
    }

    /**
     * Lists all files in the specified path with optional extension filtering and recursive traversal.
     *
     * <p>Delegates to {@link AzureFileListingServiceImpl#listFiles(String, String, boolean)}.
     *
     * @param basePath the base path in Azure File Share (e.g., "documents" or "" for root)
     * @param extensionFilter optional comma-separated file extensions (e.g., "pdf,docx,xlsx")
     * @param recursive whether to search recursively through subdirectories
     * @return List of file paths found, or empty list if no files match
     * @throws com.krista.extension.azure.files.exception.AzureFilesException if operation fails
     */
    @Override
    public List<String> listFiles(String basePath, String extensionFilter, boolean recursive) {
        return listingService.listFiles(basePath, extensionFilter, recursive);
    }

    /**
     * Lists all directories in the specified path with optional recursive traversal.
     *
     * <p>Delegates to {@link AzureFileListingServiceImpl#listDirectories(String, boolean)}.
     *
     * @param basePath the base path in Azure File Share (e.g., "documents" or "" for root)
     * @param recursive whether to search recursively through subdirectories
     * @return List of directory paths found, or empty list if no directories exist
     * @throws com.krista.extension.azure.files.exception.AzureFilesException if operation fails
     */
    @Override
    public List<String> listDirectories(String basePath, boolean recursive) {
        return listingService.listDirectories(basePath, recursive);
    }

    /**
     * Downloads a file from Azure File Share and returns it as a Krista-compatible file object.
     *
     * <p>Delegates to {@link AzureFileDownloadServiceImpl#getFile(String)}.
     *
     * @param path the path to the file in Azure File Share (e.g., "documents/report.pdf")
     * @return Map containing either success data (filename, kristafile, filesize) or error information
     */
    @Override
    public Map<String, Object> getFile(String path) {
        return downloadService.getFile(path);
    }
}

