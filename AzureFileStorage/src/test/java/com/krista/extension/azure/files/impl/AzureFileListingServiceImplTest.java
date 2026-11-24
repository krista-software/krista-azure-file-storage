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
import com.krista.extension.azure.files.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AzureFileListingServiceImpl.
 * All Azure HTTP calls are mocked — no network required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AzureFileListingServiceImpl")
class AzureFileListingServiceImplTest {

    @Mock AzureFileRestClient restClient;
    @SuppressWarnings("unchecked") @Mock HttpResponse<String> httpResponse;

    private AzureFileListingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AzureFileListingServiceImpl(restClient);
        // Standard stub: URL building delegates work transparently
        when(restClient.buildBaseUrl(anyString()))
                .thenAnswer(inv -> "https://test.file.core.windows.net/share/" + inv.getArgument(0, String.class));
        when(restClient.appendSasToken(anyString()))
                .thenAnswer(inv -> inv.getArgument(0, String.class) + "&sv=test");
    }

    // ==================== Helpers ====================

    private static String xmlWith(List<String> fileNames, List<String> dirNames) {
        StringBuilder sb = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<EnumerationResults><Entries>");
        for (String f : fileNames) {
            sb.append("<File><Name>").append(f).append("</Name></File>");
        }
        for (String d : dirNames) {
            sb.append("<Directory><Name>").append(d).append("</Name></Directory>");
        }
        sb.append("</Entries></EnumerationResults>");
        return sb.toString();
    }

    private void givenAzureReturns(int statusCode, String body) throws Exception {
        when(restClient.sendGetString(anyString())).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(statusCode);
        if (body != null) {
            when(httpResponse.body()).thenReturn(body);
        }
    }

    // ==================== listFiles ====================

    @Nested
    @DisplayName("listFiles")
    class ListFiles {

        @Test
        @DisplayName("returns files from Azure XML response")
        void returnsFilesFromXml() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("report.pdf", "data.xlsx"), List.of()));

            List<String> result = service.listFiles("", null, false);

            assertEquals(2, result.size());
            assertTrue(result.stream().anyMatch(p -> p.endsWith("report.pdf")));
            assertTrue(result.stream().anyMatch(p -> p.endsWith("data.xlsx")));
        }

        @Test
        @DisplayName("returns empty list when directory has no files")
        void emptyDirectory_returnsEmptyList() throws Exception {
            givenAzureReturns(200, xmlWith(List.of(), List.of()));

            List<String> result = service.listFiles("", null, false);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null extension filter returns all files")
        void nullExtensionFilter_returnsAllFiles() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("a.pdf", "b.docx", "c.exe"), List.of()));

            List<String> result = service.listFiles("", null, false);

            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("extension filter returns only matching files")
        void extensionFilter_returnsOnlyMatchingFiles() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("a.pdf", "b.docx", "c.pdf"), List.of()));

            List<String> result = service.listFiles("", ".pdf", false);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(p -> p.endsWith(".pdf")));
        }

        @Test
        @DisplayName("extension filter without leading dot also matches")
        void extensionFilterWithoutDot_matchesFiles() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("a.pdf", "b.docx"), List.of()));

            List<String> result = service.listFiles("", "pdf", false);

            assertEquals(1, result.size());
            assertTrue(result.get(0).endsWith(".pdf"));
        }

        @Test
        @DisplayName("multi-extension filter returns files matching any extension")
        void multiExtensionFilter_returnsMatchingFiles() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("a.pdf", "b.docx", "c.txt"), List.of()));

            List<String> result = service.listFiles("", ".pdf,.docx", false);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("HTTP 404 throws AzureFilesException")
        void http404_throwsAzureFilesException() throws Exception {
            givenAzureReturns(404, null);

            AzureFilesException ex = assertThrows(AzureFilesException.class,
                    () -> service.listFiles("missing/path", null, false));

            assertEquals(Constants.ERROR_FILE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("HTTP 401 throws AzureFilesException with UNAUTHORIZED code")
        void http401_throwsAzureFilesExceptionUnauthorized() throws Exception {
            givenAzureReturns(401, null);

            AzureFilesException ex = assertThrows(AzureFilesException.class,
                    () -> service.listFiles("", null, false));

            assertEquals(Constants.ERROR_UNAUTHORIZED, ex.getErrorCode());
        }

        @Test
        @DisplayName("HTTP 403 throws AzureFilesException with ACCESS_FORBIDDEN code")
        void http403_throwsAzureFilesExceptionForbidden() throws Exception {
            givenAzureReturns(403, null);

            AzureFilesException ex = assertThrows(AzureFilesException.class,
                    () -> service.listFiles("", null, false));

            assertEquals(Constants.ERROR_ACCESS_FORBIDDEN, ex.getErrorCode());
        }

        @Test
        @DisplayName("path with leading slash is normalized — no exception thrown")
        void leadingSlashPath_normalized() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("file.pdf"), List.of()));

            assertDoesNotThrow(() -> service.listFiles("/docs", null, false));
        }

        @Test
        @DisplayName("null path is treated as root — no exception thrown")
        void nullPath_treatedAsRoot() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("file.pdf"), List.of()));

            assertDoesNotThrow(() -> service.listFiles(null, null, false));
        }
    }

    // ==================== listDirectories ====================

    @Nested
    @DisplayName("listDirectories")
    class ListDirectories {

        @Test
        @DisplayName("returns directory list from Azure XML response")
        void returnsDirectoriesFromXml() throws Exception {
            givenAzureReturns(200, xmlWith(List.of(), List.of("docs", "images")));

            List<String> result = service.listDirectories("", false);

            assertEquals(2, result.size());
            assertTrue(result.stream().anyMatch(p -> p.endsWith("docs")));
            assertTrue(result.stream().anyMatch(p -> p.endsWith("images")));
        }

        @Test
        @DisplayName("returns empty list when no subdirectories exist")
        void noDirs_returnsEmptyList() throws Exception {
            givenAzureReturns(200, xmlWith(List.of("file.pdf"), List.of()));

            List<String> result = service.listDirectories("", false);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("HTTP 404 throws AzureFilesException")
        void http404_throwsAzureFilesException() throws Exception {
            givenAzureReturns(404, null);

            AzureFilesException ex = assertThrows(AzureFilesException.class,
                    () -> service.listDirectories("missing/path", false));

            assertEquals(Constants.ERROR_FILE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("HTTP 401 throws AzureFilesException with UNAUTHORIZED code")
        void http401_throwsAzureFilesException() throws Exception {
            givenAzureReturns(401, null);

            AzureFilesException ex = assertThrows(AzureFilesException.class,
                    () -> service.listDirectories("", false));

            assertEquals(Constants.ERROR_UNAUTHORIZED, ex.getErrorCode());
        }

        @Test
        @DisplayName("null path is treated as root — no exception thrown")
        void nullPath_treatedAsRoot() throws Exception {
            givenAzureReturns(200, xmlWith(List.of(), List.of("subdir")));

            assertDoesNotThrow(() -> service.listDirectories(null, false));
        }

        @Test
        @DisplayName("full directory path is built correctly from base path and entry name")
        void fullPathIsBuiltFromBasePathAndEntry() throws Exception {
            givenAzureReturns(200, xmlWith(List.of(), List.of("reports")));

            List<String> result = service.listDirectories("archive", false);

            assertEquals(1, result.size());
            assertEquals("archive/reports", result.get(0));
        }
    }
}
