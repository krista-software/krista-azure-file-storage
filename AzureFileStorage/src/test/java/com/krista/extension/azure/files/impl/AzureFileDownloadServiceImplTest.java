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
import com.krista.extension.azure.files.krista.KristaMediaClient;
import com.krista.extension.azure.files.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AzureFileDownloadServiceImpl.
 * All Azure HTTP calls and Krista media operations are mocked — no network required.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AzureFileDownloadServiceImpl")
class AzureFileDownloadServiceImplTest {

    @Mock AzureFileRestClient restClient;
    @Mock KristaMediaClient mediaClient;
    @SuppressWarnings("unchecked") @Mock HttpResponse<byte[]> httpResponse;

    private AzureFileDownloadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AzureFileDownloadServiceImpl(restClient, mediaClient);
    }

    // ==================== Path Validation ====================

    @Nested
    @DisplayName("Path Validation")
    class PathValidation {

        @Test
        @DisplayName("null path returns error with INVALID_PATH code")
        void nullPath_returnsError() {
            Map<String, Object> result = service.getFile(null);

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_INVALID_PATH, result.get(Constants.KEY_ERROR_CODE));
            verifyNoInteractions(restClient, mediaClient);
        }

        @Test
        @DisplayName("empty path returns error with INVALID_PATH code")
        void emptyPath_returnsError() {
            Map<String, Object> result = service.getFile("");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_INVALID_PATH, result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("whitespace-only path returns error")
        void whitespacePath_returnsError() {
            Map<String, Object> result = service.getFile("   ");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_INVALID_PATH, result.get(Constants.KEY_ERROR_CODE));
        }
    }

    // ==================== Successful Download ====================

    @Nested
    @DisplayName("Successful Download")
    class SuccessfulDownload {

        @Test
        @DisplayName("valid file path returns filename, kristafile, and size")
        void validFile_returnsSuccessMap() throws Exception {
            byte[] content = new byte[2048];
            app.krista.model.base.File mockKristaFile = mock(app.krista.model.base.File.class);

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);
            when(mediaClient.toKristaFile(any(File.class), eq("report.pdf"))).thenReturn(mockKristaFile);

            Map<String, Object> result = service.getFile("docs/report.pdf");

            assertFalse(result.containsKey(Constants.KEY_ERROR), "Should not contain error key");
            assertEquals("report.pdf", result.get(Constants.KEY_FILENAME));
            assertSame(mockKristaFile, result.get(Constants.KEY_KRISTA_FILE));
            assertNotNull(result.get(Constants.KEY_FILE_SIZE_KB));
        }

        @Test
        @DisplayName("path with leading slash is normalized before download")
        void pathWithLeadingSlash_normalized() throws Exception {
            byte[] content = new byte[512];
            app.krista.model.base.File mockFile = mock(app.krista.model.base.File.class);

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.txt");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);
            when(mediaClient.toKristaFile(any(File.class), eq("file.txt"))).thenReturn(mockFile);

            Map<String, Object> result = service.getFile("/file.txt");

            assertFalse(result.containsKey(Constants.KEY_ERROR));
            assertEquals("file.txt", result.get(Constants.KEY_FILENAME));
        }

        @Test
        @DisplayName("path with backslash is normalized before download")
        void pathWithBackslash_normalized() throws Exception {
            byte[] content = new byte[256];
            app.krista.model.base.File mockFile = mock(app.krista.model.base.File.class);

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.docx");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);
            when(mediaClient.toKristaFile(any(File.class), eq("file.docx"))).thenReturn(mockFile);

            Map<String, Object> result = service.getFile("docs\\file.docx");

            assertFalse(result.containsKey(Constants.KEY_ERROR));
        }

        @Test
        @DisplayName("file size is formatted as KB in response")
        void fileSizeFormattedAsKB() throws Exception {
            byte[] content = new byte[1024]; // exactly 1 KB
            app.krista.model.base.File mockFile = mock(app.krista.model.base.File.class);

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);
            when(mediaClient.toKristaFile(any(File.class), anyString())).thenReturn(mockFile);

            Map<String, Object> result = service.getFile("file.pdf");

            String sizeKB = (String) result.get(Constants.KEY_FILE_SIZE_KB);
            assertNotNull(sizeKB);
            assertTrue(sizeKB.endsWith("KB"), "Size should be formatted as KB: " + sizeKB);
        }
    }

    // ==================== HTTP Error Handling ====================

    @Nested
    @DisplayName("HTTP Error Handling")
    class HttpErrors {

        @Test
        @DisplayName("HTTP 404 returns FILE_NOT_FOUND error")
        void http404_returnsNotFoundError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/missing.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(404);

            Map<String, Object> result = service.getFile("missing.pdf");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_FILE_NOT_FOUND, result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("HTTP 403 returns ACCESS_FORBIDDEN error")
        void http403_returnsForbiddenError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(403);

            Map<String, Object> result = service.getFile("file.pdf");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_ACCESS_FORBIDDEN, result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("HTTP 401 returns UNAUTHORIZED error")
        void http401_returnsUnauthorizedError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(401);

            Map<String, Object> result = service.getFile("file.pdf");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_UNAUTHORIZED, result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("HTTP 500 returns SERVICE_UNAVAILABLE error")
        void http500_returnsServiceUnavailableError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(500);

            Map<String, Object> result = service.getFile("file.pdf");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_SERVICE_UNAVAILABLE, result.get(Constants.KEY_ERROR_CODE));
        }
    }

    // ==================== File Validation ====================

    @Nested
    @DisplayName("File Validation")
    class FileValidation {

        @Test
        @DisplayName("empty file (0 bytes) returns error after download")
        void emptyFile_returnsError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/empty.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(new byte[0]);

            Map<String, Object> result = service.getFile("empty.pdf");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_FILE_TOO_LARGE, result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("file exceeding 50 MB business limit returns FILE_TOO_LARGE error")
        void oversizedFile_returnsFileTooLargeError() throws Exception {
            // 51 MB exceeds the 50 MB business limit
            byte[] bigContent = new byte[(int) (Constants.MAX_FILE_SIZE_BYTES_BUSINESS + 1)];

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/large.pdf");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(bigContent);

            Map<String, Object> result = service.getFile("large.pdf");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_FILE_TOO_LARGE, result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("file without extension returns INVALID_EXTENSION error")
        void noExtension_returnsInvalidExtensionError() throws Exception {
            byte[] content = new byte[512];

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/noext");
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);

            Map<String, Object> result = service.getFile("noext");

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_INVALID_EXTENSION, result.get(Constants.KEY_ERROR_CODE));
        }
    }

    // ==================== Non-ASCII Filename Safety (KE-3149) ====================

    @Nested
    @DisplayName("Non-ASCII Filename Safety (KE-3149)")
    class NonAsciiFilenames {

        @Test
        @DisplayName("® in filename does not throw InvalidPathException")
        void registeredTrademark_doesNotThrow() throws Exception {
            byte[] content = new byte[1024];
            app.krista.model.base.File mockFile = mock(app.krista.model.base.File.class);
            String fileName = "I9206-Posey\u00AE-Lap-Belt.pdf"; // ® = \u00AE

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/" + fileName);
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);
            when(mediaClient.toKristaFile(any(File.class), eq(fileName))).thenReturn(mockFile);

            Map<String, Object> result = assertDoesNotThrow(() -> service.getFile(fileName));

            // Should succeed (or fail gracefully with an error map, never throw)
            assertNotNull(result);
        }

        @Test
        @DisplayName("™ in filename does not throw InvalidPathException")
        void trademark_doesNotThrow() throws Exception {
            byte[] content = new byte[1024];
            app.krista.model.base.File mockFile = mock(app.krista.model.base.File.class);
            String fileName = "Keller-Funnel\u2122.pdf"; // ™ = \u2122

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/" + fileName);
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);
            when(mediaClient.toKristaFile(any(File.class), eq(fileName))).thenReturn(mockFile);

            Map<String, Object> result = assertDoesNotThrow(() -> service.getFile(fileName));

            assertNotNull(result);
        }

        @Test
        @DisplayName("smart quotes in filename do not throw")
        void smartQuotes_doesNotThrow() throws Exception {
            byte[] content = new byte[512];
            app.krista.model.base.File mockFile = mock(app.krista.model.base.File.class);
            String fileName = "\u201CInk-Aid\u201D.pdf"; // " and " = smart quotes

            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/" + fileName);
            when(restClient.sendGetByte(anyString())).thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(content);
            when(mediaClient.toKristaFile(any(File.class), eq(fileName))).thenReturn(mockFile);

            Map<String, Object> result = assertDoesNotThrow(() -> service.getFile(fileName));

            assertNotNull(result);
        }
    }

    // ==================== Exception Handling ====================

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandling {

        @Test
        @DisplayName("HttpTimeoutException returns TIMEOUT error — never throws")
        void timeout_returnsTimeoutError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenThrow(new HttpTimeoutException("timed out"));

            Map<String, Object> result = assertDoesNotThrow(() -> service.getFile("file.pdf"));

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_TIMEOUT, result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("IOException returns error — never throws")
        void ioException_returnsError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenThrow(new java.io.IOException("network failure"));

            Map<String, Object> result = assertDoesNotThrow(() -> service.getFile("file.pdf"));

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertNotNull(result.get(Constants.KEY_ERROR_CODE));
        }

        @Test
        @DisplayName("InterruptedException returns error and restores interrupt flag")
        void interruptedException_returnsError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenThrow(new InterruptedException("interrupted"));

            Map<String, Object> result = assertDoesNotThrow(() -> service.getFile("file.pdf"));

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            // Verify interrupt flag was restored
            assertTrue(Thread.interrupted(), "Interrupt flag should be restored after InterruptedException");
        }

        @Test
        @DisplayName("unexpected RuntimeException returns UNEXPECTED_ERROR — never throws")
        void unexpectedException_returnsUnexpectedError() throws Exception {
            when(restClient.buildDownloadUrl(anyString())).thenReturn("https://example.com/file.pdf");
            when(restClient.sendGetByte(anyString())).thenThrow(new RuntimeException("unexpected"));

            Map<String, Object> result = assertDoesNotThrow(() -> service.getFile("file.pdf"));

            assertTrue(result.containsKey(Constants.KEY_ERROR));
            assertEquals(Constants.ERROR_UNEXPECTED, result.get(Constants.KEY_ERROR_CODE));
        }
    }
}
