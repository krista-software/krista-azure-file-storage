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

package com.krista.extension.azure.files.krista;

import app.krista.ksdk.files.FileHandle;
import app.krista.ksdk.files.FileRepository;
import com.krista.extension.azure.files.util.FilenameUtil;
import org.jvnet.hk2.annotations.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class KristaMediaClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(KristaMediaClient.class);

    private static final String ZIP_DIR = System.getProperty("java.io.tmpdir");

    /** Buffer size for file I/O operations (8KB for optimal performance) */
    private static final int BUFFER_SIZE = 8192;

    @Inject
    private FileRepository fileRepository;

    private final List<String> unSupportedFileFormats = Arrays.asList("html", "php5", "pht", "phtml", "shtml", "asa", "cer", "asax", "swf", "xap", "jsp", "exe", "js");

    /**
     * Uploads a file to Krista's media server. it will take java.io.File object as input and returns krista's file object
     *
     * @param file The file to be uploaded.
     * @return The Krista file object.
     * @throws IOException If an I/O error occurs.
     */
    public app.krista.model.base.File toKristaFile(File file) throws IOException {
        return toKristaFile(file, file.getName());
    }
    /**
     * Sanitizes the file name by replacing problematic characters with underscores.
     *
     * @param fileName The original file name.
     * @return The sanitized file name.
     */
    private String sanitizeFileName(String fileName) {
        return FilenameUtil.toSafeFilename(fileName);
    }
    /**
     * Uploads a file to Krista's media server with a specific filename.
     *
     * @param file             The file to be uploaded.
     * @param originalFileName The original filename to use for the Krista file.
     * @return The Krista file object.
     * @throws IOException If an I/O error occurs.
     */
    public app.krista.model.base.File toKristaFile(File file, String originalFileName) throws IOException {
        String fileNameToUse = sanitizeFileName(originalFileName);
        File fileToUpload = file;
        File tempZipFile = null;

        try {
            if (isUnsupportedFileFormat(originalFileName)) {
                // Use a sanitized base name for both the on-disk zip path AND the
                // name passed to Krista's FileRepository. Both code paths construct
                // filesystem paths under the hood and crash with InvalidPathException
                // if the name contains characters unmappable in sun.jnu.encoding
                // (e.g., '®', '™', smart quotes) on a non-UTF-8 system locale.
                String baseName = originalFileName.substring(0, originalFileName.lastIndexOf("."));
                String safeBase = FilenameUtil.sanitizeForFs(baseName);
                String zipFilePath = ZIP_DIR + File.separator + safeBase + ".zip";
                compressFile(zipFilePath, file.getAbsolutePath());
                tempZipFile = new File(zipFilePath);
                fileToUpload = tempZipFile;
                fileNameToUse = sanitizeFileName(baseName) + ".zip";
            }

            try (final FileHandle fileHandle = fileRepository.createNewFileByName(fileNameToUse)) {
                fileHandle.setContent(new FileInputStream(fileToUpload));
                return fileHandle.getFile();
            }
        } finally {
            // Clean up temporary ZIP file to prevent disk space exhaustion
            if (tempZipFile != null && tempZipFile.exists()) {
                if (!tempZipFile.delete()) {
                    LOGGER.warn("Failed to delete temporary ZIP file: {}", tempZipFile.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Downloads a file from Krista's media server. it will take krista's file object as input and returns java.io.File object
     *
     * @param file The Krista file object to be downloaded.
     * @return The downloaded Java file object.
     * @throws IOException If an I/O error occurs.
     */
    public File toJavaFile(app.krista.model.base.File file) throws IOException {
        try (FileHandle fileHandle = fileRepository.getFile(file)) {
            InputStream content = fileHandle.getContent();
            final File input = new File(file.getFileName());
            return convertInputStreamToFile(content, input);
        }
    }

    /**
     * Checks if the file format is unsupported.
     *
     * @param fileName The name of the file.
     * @return True if the file format is unsupported, otherwise false.
     */
    private boolean isUnsupportedFileFormat(String fileName) {
        String fileExtension = getFileExtension(fileName);
        return unSupportedFileFormats.contains(fileExtension);
    }

    /**
     * Converts an input stream to a file.
     *
     * @param inputStream The input stream to be converted.
     * @param input       The file to write the input stream content to.
     * @return The file with the content of the input stream.
     * @throws IOException If an I/O error occurs.
     */
    private File convertInputStreamToFile(InputStream inputStream, File input) throws IOException {
        try (OutputStream outputStream = new FileOutputStream(input)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return input;
    }

    /**
     * Retrieves the file extension from the file name.
     *
     * @param fileName The name of the file.
     * @return The file extension.
     * @throws IllegalArgumentException If the file format is unsupported.
     */
    private String getFileExtension(String fileName) {
        if (fileName.contains(".")) {
            return fileName.substring((fileName.lastIndexOf(".") + 1));
        }
        throw new IllegalArgumentException("Unsupported file format");
    }

    /**
     * Compresses a file into a zip archive.
     *
     * @param zipFilePath  The path where the zip file will be created.
     * @param dirPathToZip The path of the directory to be zipped.
     * @throws IOException If an I/O error occurs.
     */
    public static void compressFile(String zipFilePath, String dirPathToZip) throws IOException {
        File fileToZip = new File(dirPathToZip);
        try (
                FileInputStream fis = new FileInputStream(fileToZip);
                ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFilePath))
        ) {
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zipOut.write(buffer, 0, length);
            }
            zipOut.closeEntry();
        }
    }

}
