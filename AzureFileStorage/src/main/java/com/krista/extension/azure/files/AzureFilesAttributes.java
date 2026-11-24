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

package com.krista.extension.azure.files;

import app.krista.extension.executor.Invoker;
import com.krista.extension.azure.files.util.Constants;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class AzureFilesAttributes {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFilesAttributes.class);

    private static final int EXPIRY_WARNING_DAYS = 7;

    private String storageAccountName;
    private String fileShareName;
    private String sasToken;
    private String sasTokenExpiry;
    private boolean userProvidedExpiry;

    @Inject
    public AzureFilesAttributes(Invoker invoker) {
        this(invoker.getAttributes());
    }

    public AzureFilesAttributes(Map<String, Object> attributes) {
        update(attributes);
    }

    public void update(Map<String, Object> attributes) {
        this.storageAccountName = getStringValue(attributes, Constants.STORAGE_ACCOUNT_NAME, "");
        this.fileShareName = getStringValue(attributes, Constants.FILE_SHARE_NAME, "");
        this.sasToken = getStringValue(attributes, Constants.SAS_TOKEN, "");
        this.sasTokenExpiry = getStringValue(attributes, Constants.SAS_TOKEN_EXPIRY, "");

        this.userProvidedExpiry = this.sasTokenExpiry != null && !this.sasTokenExpiry.trim().isEmpty();

        // Auto-populate expiry from SAS token if not provided
        if (!this.userProvidedExpiry && hasSasToken()) {
            this.sasTokenExpiry = parseSasTokenExpiry(this.sasToken);
            LOGGER.info("Auto-populated SAS Token Expiry from token: {}", this.sasTokenExpiry);
        }
    }

    private String getStringValue(Map<String, Object> attributes, String key, String defaultValue) {
        Object value = attributes.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public static AzureFilesAttributes create(Invoker invoker, Map<String, Object> invokerAttributes) {
        AzureFilesAttributes azureFilesAttributes = new AzureFilesAttributes(invoker);
        azureFilesAttributes.update(invokerAttributes);
        return azureFilesAttributes;
    }


    public String getStorageAccountName() {
        return storageAccountName;
    }

    public String getFileShareName() {
        return fileShareName;
    }

    public String getSasToken() {
        return sasToken;
    }

    public boolean hasSasToken() {
        return sasToken != null && !sasToken.trim().isEmpty();
    }

    public String getSasTokenExpiry() {
        return sasTokenExpiry;
    }

    public boolean hasSasTokenExpiry() {
        return sasTokenExpiry != null && !sasTokenExpiry.trim().isEmpty();
    }

    public boolean isUserProvidedExpiry() {
        return userProvidedExpiry;
    }

    /**
     * Parses a parameter value from the SAS token by key.
     * SAS token format: sv=...&se=2030-07-01T03:49:20Z&st=2025-06-01T19:49:20Z&...
     *
     * @param token    the SAS token string
     * @param paramKey the parameter key to look for (e.g., "se", "st")
     * @return the decoded parameter value, or empty string if not found
     */
    private static String parseSasTokenParam(String token, String paramKey) {
        if (token == null || token.trim().isEmpty()) {
            return "";
        }
        String cleaned = token.startsWith("?") ? token.substring(1) : token;
        String[] params = cleaned.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && paramKey.equals(keyValue[0].trim())) {
                return URLDecoder.decode(keyValue[1].trim(), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /**
     * Parses the 'se' (signed expiry) parameter from the SAS token.
     *
     * @param token the SAS token string
     * @return the expiry date string, or empty string if not found
     */
    public static String parseSasTokenExpiry(String token) {
        String expiry = parseSasTokenParam(token, "se");
        if (expiry.isEmpty()) {
            LOGGER.warn("Could not find 'se' (signed expiry) parameter in SAS token");
        }
        return expiry;
    }

    /**
     * Parses the 'st' (signed start) parameter from the SAS token.
     *
     * @param token the SAS token string
     * @return the start date string, or empty string if not found
     */
    public static String parseSasTokenStart(String token) {
        return parseSasTokenParam(token, "st");
    }

    /**
     * Validates that SAS token expiry is after start time.
     *
     * @throws IllegalArgumentException if expiry is before or equal to start time
     */
    public void validateSasTokenExpiry() throws IllegalArgumentException {
        if (!hasSasToken()) {
            return;
        }

        String tokenExpiry = parseSasTokenExpiry(this.sasToken);
        String start = parseSasTokenStart(this.sasToken);

        if (tokenExpiry.isEmpty()) {
            LOGGER.warn("SAS token does not contain an expiry date (se parameter)");
            return;
        }

        // If user entered an expiry date, it must match the actual 'se' from SAS token
        if (userProvidedExpiry) {
            try {
                ZonedDateTime userExpiry = ZonedDateTime.parse(this.sasTokenExpiry, DateTimeFormatter.ISO_DATE_TIME);
                ZonedDateTime actualExpiry = ZonedDateTime.parse(tokenExpiry, DateTimeFormatter.ISO_DATE_TIME);

                if (!userExpiry.isEqual(actualExpiry)) {
                    LOGGER.error("SAS Token Expiry mismatch: user entered={}, actual from token={}", this.sasTokenExpiry, tokenExpiry);
                    throw new IllegalArgumentException(
                            "SAS Token Expiry date (" + this.sasTokenExpiry + ") does not match the expiry from SAS token (" + tokenExpiry + "). "
                                    + "Please correct the expiry date or leave the field empty to auto-populate.");
                }
            } catch (DateTimeParseException e) {
                LOGGER.error("Invalid SAS Token Expiry format: {}", this.sasTokenExpiry);
                throw new IllegalArgumentException(
                        "Invalid SAS Token Expiry date format: " + this.sasTokenExpiry + ". Expected format: yyyy-MM-ddTHH:mm:ssZ (e.g., 2030-07-01T03:49:20Z)");
            }
        }

        // Parse the token's expiry date — if unparseable, skip further validation
        ZonedDateTime expiryDate;
        try {
            expiryDate = ZonedDateTime.parse(tokenExpiry, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            LOGGER.warn("Could not parse SAS token expiry date '{}': {}", tokenExpiry, e.getMessage());
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();

        if (expiryDate.isBefore(now)) {
            LOGGER.error("SAS Token has expired: expiry={}, now={}", tokenExpiry, now);
            throw new IllegalArgumentException(
                    "SAS Token has expired on " + tokenExpiry + ". Please generate a new SAS token from Azure Portal.");
        }

        // Warn if expiry field was empty and token expires within 7 days
        if (!userProvidedExpiry) {
            long daysUntilExpiry = ChronoUnit.DAYS.between(now, expiryDate);
            if (daysUntilExpiry <= EXPIRY_WARNING_DAYS) {
                LOGGER.warn("SAS Token expires in {} day(s) on {}. "
                        + "Consider adding the SAS Token Expiry date in the '{}' field to track token expiry, "
                        + "or generate a new SAS token with a longer validity period from Azure Portal.",
                        daysUntilExpiry, tokenExpiry, Constants.SAS_TOKEN_EXPIRY);
            }
        }

        // Validate start date if present — parse error here is non-fatal
        if (!start.isEmpty()) {
            try {
                ZonedDateTime startDate = ZonedDateTime.parse(start, DateTimeFormatter.ISO_DATE_TIME);
                if (!expiryDate.isAfter(startDate)) {
                    LOGGER.error("SAS Token expiry ({}) must be after start time ({})", tokenExpiry, start);
                    throw new IllegalArgumentException(
                            "SAS Token expiry date (" + tokenExpiry + ") must be after start time (" + start + "). Please generate a valid SAS token.");
                }
                LOGGER.debug("SAS Token dates validated: start={}, expiry={}", start, tokenExpiry);
            } catch (DateTimeParseException e) {
                LOGGER.warn("Could not parse SAS token start date '{}': {}", start, e.getMessage());
            }
        }
    }

    public void validateConfiguration() throws IllegalArgumentException {
        LOGGER.debug("Validating Azure Files configuration");

        if (storageAccountName == null || storageAccountName.trim().isEmpty()) {
            LOGGER.error("Validation failed: Storage Account Name is missing");
            throw new IllegalArgumentException("Storage Account Name is required. Please enter the name of your Azure storage account (e.g., 'mystorageaccount'). You can find this in the Azure Portal under Storage Accounts.");
        }
        if (fileShareName == null || fileShareName.trim().isEmpty()) {
            LOGGER.error("Validation failed: File Share Name is missing");
            throw new IllegalArgumentException("File Share Name is required. Please enter the name of your file share within the storage account (e.g., 'documents' or 'shared-files'). You can find this in Azure Portal under your Storage Account > File shares.");
        }
        if (!hasSasToken()) {
            LOGGER.error("Validation failed: SAS Token is missing");
            throw new IllegalArgumentException("SAS Token is required. Please generate a SAS token from Azure Portal with 'Read' and 'List' permissions. The token should start with '?sv=' and include all required parameters.");
        }
    }

    @Override
    public String toString() {
        return "AzureFilesAttributes{" +
                "storageAccountName='" + storageAccountName + '\'' +
                ", fileShareName='" + fileShareName + '\'' +
                ", hasSasToken=" + hasSasToken() +
                ", sasTokenExpiry='" + sasTokenExpiry + '\'' +
                '}';
    }
}
