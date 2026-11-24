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

package com.krista.extension.azure.files.integration;

import com.krista.extension.azure.files.AzureFilesAttributes;
import com.krista.extension.azure.files.client.AzureFileRestClient;
import com.krista.extension.azure.files.impl.AzureFileDownloadServiceImpl;
import com.krista.extension.azure.files.impl.AzureFileListingServiceImpl;
import com.krista.extension.azure.files.krista.KristaMediaClient;
import com.krista.extension.azure.files.util.Constants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for Azure File Share operations against real credentials.
 *
 * <p><b>How to run:</b>
 * <pre>
 *   ./gradlew test --tests "*.integration.*"
 * </pre>
 *
 * <p><b>Prerequisites:</b>
 * <ul>
 *   <li>Valid credentials in {@code src/main/resources/config.properties}</li>
 *   <li>Network access to {@code vamssolution.file.core.windows.net}</li>
 * </ul>
 *
 * <p>All tests are skipped automatically if the Azure connection is unavailable,
 * so this class is safe to include in CI — it will be marked as "skipped" rather
 * than "failed" when credentials are not present.
 *
 * <p><b>Note:</b> {@link KristaMediaClient} depends on the Krista platform's
 * {@code FileRepository} which is not available outside the Krista runtime.
 * Download tests mock only that final upload step — the actual HTTP download
 * from Azure is real.
 */
@Tag("integration")
@DisplayName("Azure Files Integration — /chca/")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AzureFilesIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFilesIntegrationTest.class);

    private static final String BASE_PATH = "chca";

    private static AzureFileRestClient restClient;
    private static AzureFileListingServiceImpl listingService;

    // KristaMediaClient mocked — its FileRepository is only available inside Krista runtime.
    // The real Azure HTTP download still happens; only the final upload to Krista is stubbed.
    private static final KristaMediaClient mediaClient = mock(KristaMediaClient.class);
    private static final app.krista.model.base.File MOCK_KRISTA_FILE = mock(app.krista.model.base.File.class);

    private AzureFileDownloadServiceImpl downloadService;

    // ==================== Setup ====================

    @BeforeAll
    static void connect() throws Exception {
        // Load credentials from config.properties (the local reproduction file)
        Properties props = new Properties();
        try (InputStream is = AzureFilesIntegrationTest.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (is != null) {
                props.load(is);
            }
        }

        String account  = props.getProperty("storageAccountName", "").trim();
        String share    = props.getProperty("fileShareName", "").trim();
        String sasToken = props.getProperty("sasToken", "").trim();

        assumeFalse(account.isEmpty(),  "Skipping — storageAccountName not set in config.properties");
        assumeFalse(share.isEmpty(),    "Skipping — fileShareName not set in config.properties");
        assumeFalse(sasToken.isEmpty(), "Skipping — sasToken not set in config.properties");

        Map<String, Object> attrs = new HashMap<>();
        attrs.put(Constants.STORAGE_ACCOUNT_NAME, account);
        attrs.put(Constants.FILE_SHARE_NAME, share);
        attrs.put(Constants.SAS_TOKEN, sasToken);

        AzureFilesAttributes attributes = new AzureFilesAttributes(attrs);
        restClient = new AzureFileRestClient(attributes);
        listingService = new AzureFileListingServiceImpl(restClient);

        // Warm-up: verify the connection works before running any test
        boolean connected = restClient.testConnection();
        assumeTrue(connected, "Skipping — cannot reach Azure File Share (check credentials or network)");

        LOGGER.info("Integration test connected: account={}, share={}", account, share);

        // Stub KristaMediaClient to capture the on-disk temp file without needing the platform
        when(mediaClient.toKristaFile(any(File.class), anyString())).thenReturn(MOCK_KRISTA_FILE);
    }

    @BeforeEach
    void buildDownloadService() {
        downloadService = new AzureFileDownloadServiceImpl(restClient, mediaClient);
    }

    // ==================== Directory Listing ====================

    @Test
    @Order(1)
    @DisplayName("List directories under /chca/ (non-recursive)")
    void listDirectories_chca_nonRecursive() {
        List<String> dirs = listingService.listDirectories(BASE_PATH, false);

        assertNotNull(dirs);
        LOGGER.info("--- Directories under /{}/  ({} found) ---", BASE_PATH, dirs.size());
        dirs.forEach(d -> LOGGER.info("  DIR  {}", d));
    }

    @Test
    @Order(2)
    @DisplayName("List files under /chca/ (non-recursive)")
    void listFiles_chca_nonRecursive() {
        List<String> files = listingService.listFiles(BASE_PATH, null, false);

        assertNotNull(files);
        assertTrue(files.stream().noneMatch(String::isEmpty), "No entry should be blank");

        LOGGER.info("--- Files under /{}/  ({} found) ---", BASE_PATH, files.size());
        files.forEach(f -> LOGGER.info("  FILE {}", f));
    }

    @Test
    @Order(3)
    @DisplayName("List PDF files only under /chca/ using extension filter")
    void listFiles_chca_pdfFilter() {
        List<String> pdfs = listingService.listFiles(BASE_PATH, ".pdf", false);

        assertNotNull(pdfs);
        pdfs.forEach(f ->
            assertTrue(f.toLowerCase().endsWith(".pdf"),
                "Extension filter should return only PDFs but got: " + f));

        LOGGER.info("--- PDF files under /{}/  ({} found) ---", BASE_PATH, pdfs.size());
        pdfs.forEach(f -> LOGGER.info("  PDF  {}", f));
    }

    @Test
    @Order(4)
    @DisplayName("List files with multiple extension filter (.pdf, .docx)")
    void listFiles_chca_multiExtensionFilter() {
        List<String> files = listingService.listFiles(BASE_PATH, ".pdf,.docx", false);

        assertNotNull(files);
        files.forEach(f -> {
            String lower = f.toLowerCase();
            assertTrue(lower.endsWith(".pdf") || lower.endsWith(".docx"),
                "Multi-filter should return only .pdf or .docx but got: " + f);
        });

        LOGGER.info("--- PDF+DOCX files under /{}/  ({} found) ---", BASE_PATH, files.size());
        files.forEach(f -> LOGGER.info("  FILE {}", f));
    }

    // ==================== File Download ====================

    @Test
    @Order(5)
    @DisplayName("Download first available file from /chca/")
    void downloadFirstFile_chca() {
        List<String> files = listingService.listFiles(BASE_PATH, null, false);
        assumeFalse(files.isEmpty(), "Skipping — no files found at /" + BASE_PATH + "/");

        String targetPath = files.get(0);
        LOGGER.info("Downloading first file: {}", targetPath);

        Map<String, Object> result = downloadService.getFile(targetPath);

        assertNotNull(result);
        assertFalse(result.containsKey(Constants.KEY_ERROR),
            "Download failed: " + result.get(Constants.KEY_ERROR));
        assertNotNull(result.get(Constants.KEY_FILENAME),  "filename must be present");
        assertNotNull(result.get(Constants.KEY_KRISTA_FILE), "kristafile must be present");
        assertNotNull(result.get(Constants.KEY_FILE_SIZE_KB), "filesizekb must be present");

        LOGGER.info("Downloaded OK — file='{}', size='{}'",
            result.get(Constants.KEY_FILENAME), result.get(Constants.KEY_FILE_SIZE_KB));
    }

    @Test
    @Order(6)
    @DisplayName("Download first PDF from /chca/ (validates full flow for PDF type)")
    void downloadFirstPdf_chca() {
        List<String> pdfs = listingService.listFiles(BASE_PATH, ".pdf", false);
        assumeFalse(pdfs.isEmpty(), "Skipping — no PDF files found at /" + BASE_PATH + "/");

        String pdfPath = pdfs.get(0);
        LOGGER.info("Downloading PDF: {}", pdfPath);

        Map<String, Object> result = downloadService.getFile(pdfPath);

        assertNotNull(result);
        assertFalse(result.containsKey(Constants.KEY_ERROR),
            "PDF download failed: " + result.get(Constants.KEY_ERROR));

        String fileName = (String) result.get(Constants.KEY_FILENAME);
        assertNotNull(fileName);
        assertTrue(fileName.toLowerCase().endsWith(".pdf"),
            "Returned filename should end with .pdf: " + fileName);

        LOGGER.info("PDF downloaded OK — file='{}', size='{}'",
            fileName, result.get(Constants.KEY_FILE_SIZE_KB));
    }

    // ==================== Non-ASCII Filename Safety (KE-3149 Regression) ====================

    @Test
    @Order(7)
    @DisplayName("KE-3149: file with ® in name does not crash (GLIDEWIRE ADVANTAGE® Brochure)")
    void nonAscii_registeredTrademark_doesNotCrash() {
        // This is the exact filename from the production log that caused the crash
        String path = BASE_PATH + "/GLIDEWIRE ADVANTAGE\u00AE Brochure12284326952412476436.pdf";

        Map<String, Object> result = assertDoesNotThrow(
            () -> downloadService.getFile(path),
            "® in filename must never throw an exception"
        );

        assertNotNull(result, "Result must never be null");

        if (result.containsKey(Constants.KEY_ERROR)) {
            // File may not exist in this environment — acceptable as long as no crash
            LOGGER.warn("File not found (acceptable) — error: {}", result.get(Constants.KEY_ERROR));
        } else {
            LOGGER.info("Downloaded OK — file='{}', size='{}'",
                result.get(Constants.KEY_FILENAME), result.get(Constants.KEY_FILE_SIZE_KB));
        }
    }

    @Test
    @Order(8)
    @DisplayName("KE-3149: file with ™ in name does not crash (Keller Funnel™)")
    void nonAscii_trademark_doesNotCrash() {
        String path = BASE_PATH + "/L FORM 102 004 Rev C Keller Funnel\u21222 Price List (2)3706187482771018226.pdf";

        Map<String, Object> result = assertDoesNotThrow(
            () -> downloadService.getFile(path),
            "™ in filename must never throw an exception"
        );

        assertNotNull(result);
        LOGGER.info("™ filename result: {}",
            result.containsKey(Constants.KEY_ERROR) ? "error=" + result.get(Constants.KEY_ERROR) : "success");
    }

    @Test
    @Order(9)
    @DisplayName("KE-3149: file with smart quote and backslash does not crash (CDI's Ink Aid™)")
    void nonAscii_smartQuoteAndBackslash_doesNotCrash() {
        String path = BASE_PATH + "/CDI\u2019s Ink Aid\u2122 - CS_5 \\_ Cancer Diagnostics Inc.14796546156968475101.html";

        Map<String, Object> result = assertDoesNotThrow(
            () -> downloadService.getFile(path),
            "Smart quote / backslash in filename must never throw an exception"
        );

        assertNotNull(result);
        LOGGER.info("Smart-quote filename result: {}",
            result.containsKey(Constants.KEY_ERROR) ? "error=" + result.get(Constants.KEY_ERROR) : "success");
    }

    // ==================== Error Handling ====================

    @Test
    @Order(10)
    @DisplayName("Non-existent path under /chca/ returns error map, not exception")
    void nonExistentPath_returnsErrorNotException() {
        String missingPath = BASE_PATH + "/this-path-does-not-exist-xyz-12345/missing.pdf";

        Map<String, Object> result = assertDoesNotThrow(
            () -> downloadService.getFile(missingPath),
            "Missing file should return error map, never throw"
        );

        assertNotNull(result);
        assertTrue(result.containsKey(Constants.KEY_ERROR),
            "Should have error key for non-existent path");
        assertEquals(Constants.ERROR_FILE_NOT_FOUND, result.get(Constants.KEY_ERROR_CODE),
            "Should return FILE_NOT_FOUND error code for 404");

        LOGGER.info("Non-existent path correctly returned error: {}", result.get(Constants.KEY_ERROR));
    }

    @Test
    @Order(11)
    @DisplayName("Listing non-existent subdirectory under /chca/ throws AzureFilesException")
    void listFiles_nonExistentSubdir_throwsException() {
        String missingDir = BASE_PATH + "/nonexistent-subdir-xyz-99999";

        com.krista.extension.azure.files.exception.AzureFilesException ex = assertThrows(
            com.krista.extension.azure.files.exception.AzureFilesException.class,
            () -> listingService.listFiles(missingDir, null, false)
        );

        assertEquals(Constants.ERROR_FILE_NOT_FOUND, ex.getErrorCode());
        LOGGER.info("Non-existent directory correctly raised AzureFilesException: {}", ex.getMessage());
    }
}
