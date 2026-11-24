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

package com.krista.extension.azure.files.catalog;

import app.krista.extension.executor.ExtensionResponse;
import com.krista.extension.azure.files.exception.AzureFilesException;
import com.krista.extension.azure.files.impl.AzureFileServiceImpl;
import com.krista.extension.azure.files.service.AzureFilesTelemetryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests telemetry integration in AzureFilesOperationArea.
 * Verifies that telemetry is recorded correctly for all catalog operations
 * across success, failure, and exception scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Azure Files Operation Area - Telemetry Integration Tests")
class AzureFilesOperationAreaTelemetryTest {

    @Mock
    private AzureFileServiceImpl azureFileService;

    @Mock
    private AzureFilesTelemetryService telemetryService;

    private AzureFilesOperationArea operationArea;

    private final AzureFilesTelemetryService.TelemetryContext mockContext =
            new AzureFilesTelemetryService.TelemetryContext(
                    System.currentTimeMillis(), "azure.files", Map.of("operation", "test"));

    @BeforeEach
    void setUp() {
        operationArea = new AzureFilesOperationArea(azureFileService, telemetryService);
    }

    // ==================== listFiles telemetry ====================

    @Nested
    @DisplayName("listFiles telemetry")
    class ListFilesTelemetry {

        @Test
        @DisplayName("Should record telemetry start and success when files are returned")
        void listFiles_success_withResults() {
            when(telemetryService.recordRequestStart("azure.files", "list_files")).thenReturn(mockContext);
            when(azureFileService.listFiles("/docs", null, false))
                    .thenReturn(List.of("file1.pdf", "file2.txt"));

            ExtensionResponse response = operationArea.listFiles("/docs", null, false);

            assertNotNull(response);
            InOrder inOrder = inOrder(telemetryService);
            inOrder.verify(telemetryService).recordRequestStart("azure.files", "list_files");
            inOrder.verify(telemetryService).recordSuccess(mockContext);
            verify(telemetryService, never()).recordFailure(any(), any());
            verify(telemetryService, never()).recordException(any(), any());
        }

        @Test
        @DisplayName("Should record telemetry success when empty file list is returned")
        void listFiles_success_emptyResults() {
            when(telemetryService.recordRequestStart("azure.files", "list_files")).thenReturn(mockContext);
            when(azureFileService.listFiles("", null, false))
                    .thenReturn(List.of());

            ExtensionResponse response = operationArea.listFiles("", null, false);

            assertNotNull(response);
            verify(telemetryService).recordRequestStart("azure.files", "list_files");
            verify(telemetryService).recordSuccess(mockContext);
            verify(telemetryService, never()).recordFailure(any(), any());
        }

        @Test
        @DisplayName("Should record telemetry failure on AzureFilesException")
        void listFiles_azureFilesException() {
            when(telemetryService.recordRequestStart("azure.files", "list_files")).thenReturn(mockContext);
            when(azureFileService.listFiles("/missing", null, false))
                    .thenThrow(new AzureFilesException("Path not found", "FILE_NOT_FOUND"));

            ExtensionResponse response = operationArea.listFiles("/missing", null, false);

            assertNotNull(response);
            verify(telemetryService).recordRequestStart("azure.files", "list_files");
            verify(telemetryService).recordFailure(eq(mockContext), any(ExtensionResponse.class));
            verify(telemetryService, never()).recordSuccess(any());
            verify(telemetryService, never()).recordException(any(), any());
        }

        @Test
        @DisplayName("Should record telemetry exception on unexpected error")
        void listFiles_unexpectedException() {
            when(telemetryService.recordRequestStart("azure.files", "list_files")).thenReturn(mockContext);
            RuntimeException exception = new RuntimeException("Connection refused");
            when(azureFileService.listFiles("/path", null, false)).thenThrow(exception);

            ExtensionResponse response = operationArea.listFiles("/path", null, false);

            assertNotNull(response);
            verify(telemetryService).recordRequestStart("azure.files", "list_files");
            verify(telemetryService).recordException(mockContext, exception);
            verify(telemetryService, never()).recordSuccess(any());
            verify(telemetryService, never()).recordFailure(any(), any());
        }
    }

    // ==================== listDirectories telemetry ====================

    @Nested
    @DisplayName("listDirectories telemetry")
    class ListDirectoriesTelemetry {

        @Test
        @DisplayName("Should record telemetry start and success when directories are returned")
        void listDirectories_success_withResults() {
            when(telemetryService.recordRequestStart("azure.files", "list_directories")).thenReturn(mockContext);
            when(azureFileService.listDirectories("/root", true))
                    .thenReturn(List.of("dir1", "dir2", "dir3"));

            ExtensionResponse response = operationArea.listDirectories("/root", true);

            assertNotNull(response);
            InOrder inOrder = inOrder(telemetryService);
            inOrder.verify(telemetryService).recordRequestStart("azure.files", "list_directories");
            inOrder.verify(telemetryService).recordSuccess(mockContext);
            verify(telemetryService, never()).recordFailure(any(), any());
            verify(telemetryService, never()).recordException(any(), any());
        }

        @Test
        @DisplayName("Should record telemetry success when empty directory list is returned")
        void listDirectories_success_emptyResults() {
            when(telemetryService.recordRequestStart("azure.files", "list_directories")).thenReturn(mockContext);
            when(azureFileService.listDirectories("", false))
                    .thenReturn(List.of());

            ExtensionResponse response = operationArea.listDirectories("", false);

            assertNotNull(response);
            verify(telemetryService).recordSuccess(mockContext);
            verify(telemetryService, never()).recordFailure(any(), any());
        }

        @Test
        @DisplayName("Should record telemetry failure on AzureFilesException")
        void listDirectories_azureFilesException() {
            when(telemetryService.recordRequestStart("azure.files", "list_directories")).thenReturn(mockContext);
            when(azureFileService.listDirectories("/forbidden", false))
                    .thenThrow(new AzureFilesException("Access denied", "ACCESS_FORBIDDEN"));

            ExtensionResponse response = operationArea.listDirectories("/forbidden", false);

            assertNotNull(response);
            verify(telemetryService).recordFailure(eq(mockContext), any(ExtensionResponse.class));
            verify(telemetryService, never()).recordSuccess(any());
        }

        @Test
        @DisplayName("Should record telemetry exception on unexpected error")
        void listDirectories_unexpectedException() {
            when(telemetryService.recordRequestStart("azure.files", "list_directories")).thenReturn(mockContext);
            RuntimeException exception = new RuntimeException("Timeout");
            when(azureFileService.listDirectories("/path", true)).thenThrow(exception);

            ExtensionResponse response = operationArea.listDirectories("/path", true);

            assertNotNull(response);
            verify(telemetryService).recordException(mockContext, exception);
            verify(telemetryService, never()).recordSuccess(any());
        }
    }

    // ==================== getFile telemetry ====================

    @Nested
    @DisplayName("getFile telemetry")
    class GetFileTelemetry {

        @Test
        @DisplayName("Should record telemetry start and success when file is retrieved")
        void getFile_success() {
            when(telemetryService.recordRequestStart("azure.files", "get_file")).thenReturn(mockContext);
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("filename", "report.pdf");
            fileInfo.put("filesize", 1024L);
            when(azureFileService.getFile("docs/report.pdf")).thenReturn(fileInfo);

            ExtensionResponse response = operationArea.getFile("docs/report.pdf");

            assertNotNull(response);
            InOrder inOrder = inOrder(telemetryService);
            inOrder.verify(telemetryService).recordRequestStart("azure.files", "get_file");
            inOrder.verify(telemetryService).recordSuccess(mockContext);
            verify(telemetryService, never()).recordFailure(any(), any());
            verify(telemetryService, never()).recordException(any(), any());
        }

        @Test
        @DisplayName("Should record telemetry failure when service returns error map")
        void getFile_serviceReturnsError() {
            when(telemetryService.recordRequestStart("azure.files", "get_file")).thenReturn(mockContext);
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("error", "File not found");
            errorInfo.put("errorCode", "FILE_NOT_FOUND");
            when(azureFileService.getFile("missing.pdf")).thenReturn(errorInfo);

            ExtensionResponse response = operationArea.getFile("missing.pdf");

            assertNotNull(response);
            verify(telemetryService).recordRequestStart("azure.files", "get_file");
            verify(telemetryService).recordFailure(eq(mockContext), any(ExtensionResponse.class));
            verify(telemetryService, never()).recordSuccess(any());
            verify(telemetryService, never()).recordException(any(), any());
        }

        @Test
        @DisplayName("Should record telemetry exception on unexpected error")
        void getFile_unexpectedException() {
            when(telemetryService.recordRequestStart("azure.files", "get_file")).thenReturn(mockContext);
            RuntimeException exception = new RuntimeException("Network error");
            when(azureFileService.getFile("file.pdf")).thenThrow(exception);

            ExtensionResponse response = operationArea.getFile("file.pdf");

            assertNotNull(response);
            verify(telemetryService).recordRequestStart("azure.files", "get_file");
            verify(telemetryService).recordException(eq(mockContext), eq(exception));
            verify(telemetryService, never()).recordSuccess(any(AzureFilesTelemetryService.TelemetryContext.class));
            verify(telemetryService, never()).recordFailure(any(AzureFilesTelemetryService.TelemetryContext.class), any());
        }
    }

    // ==================== Cross-cutting telemetry tests ====================

    @Nested
    @DisplayName("Cross-cutting telemetry behavior")
    class CrossCuttingTelemetry {

        @Test
        @DisplayName("Should always call recordRequestStart before any outcome recording")
        void telemetryStartAlwaysCalledFirst() {
            when(telemetryService.recordRequestStart("azure.files", "list_files")).thenReturn(mockContext);
            when(azureFileService.listFiles("/path", null, false)).thenReturn(List.of("file.txt"));

            operationArea.listFiles("/path", null, false);

            InOrder inOrder = inOrder(telemetryService);
            inOrder.verify(telemetryService).recordRequestStart("azure.files", "list_files");
            inOrder.verify(telemetryService).recordSuccess(mockContext);
        }

        @Test
        @DisplayName("Should use correct operation names for each catalog request")
        void correctOperationNamesUsed() {
            when(telemetryService.recordRequestStart(anyString(), anyString())).thenReturn(mockContext);
            when(azureFileService.listFiles("/p", null, false)).thenReturn(List.of());
            when(azureFileService.listDirectories("/p", false)).thenReturn(List.of());
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("filename", "test.txt");
            when(azureFileService.getFile("/p/f.txt")).thenReturn(fileInfo);

            operationArea.listFiles("/p", null, false);
            operationArea.listDirectories("/p", false);
            operationArea.getFile("/p/f.txt");

            verify(telemetryService).recordRequestStart("azure.files", "list_files");
            verify(telemetryService).recordRequestStart("azure.files", "list_directories");
            verify(telemetryService).recordRequestStart("azure.files", "get_file");
        }

        @Test
        @DisplayName("Should use 'azure.files' as metric prefix for all operations")
        void correctMetricPrefixUsed() {
            when(telemetryService.recordRequestStart("azure.files", "list_files")).thenReturn(mockContext);
            when(azureFileService.listFiles("/path", null, false)).thenReturn(List.of());

            operationArea.listFiles("/path", null, false);

            verify(telemetryService).recordRequestStart(eq("azure.files"), eq("list_files"));
        }
    }
}
