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
import app.krista.extension.impl.anno.*;
import com.krista.extension.azure.files.client.AzureFileRestClient;
import com.krista.extension.azure.files.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Map;

/**
 * Azure Files Extension for Krista
 * <p>
 * Provides secure access to Azure File Shares using SAS Token authentication.
 * This extension supports browsing directories and downloading files from Azure File Storage.
 * <p>
 * Authentication: SAS Token only
 * - Storage Account Name: The name of your Azure storage account
 * - File Share Name: The name of the file share to access
 * - SAS Token: Shared Access Signature token with appropriate permissions
 */
@Java(version = Java.Version.JAVA_21)
@Field.Text(value = Constants.STORAGE_ACCOUNT_NAME, required = true)
@Field.Text(value = Constants.FILE_SHARE_NAME, required = true)
@Field.Text(value = Constants.SAS_TOKEN, required = true, isSecured = true)
@Field.Text(value = Constants.SAS_TOKEN_EXPIRY, required = false)
@Extension(version = "1.0.4", name = "Azure File Service")
@StaticResource(path = "docs", file = "docs")
@ChangeLog(file = "resources/docs/pages/release-notes.md")
public final class AzureFilesExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFilesExtension.class);

    private final Invoker invoker;

    @Inject
    public AzureFilesExtension(Invoker invoker) {
        this.invoker = invoker;

    }


    @InvokerRequest(InvokerRequest.Type.CUSTOM_TABS)
    public Map<String, String> customTabs() {
        return Map.of("Documentation", "static/docs");
    }

    /**
     * Validates SAS token configuration attributes when extension is configured
     */
    @InvokerRequest(InvokerRequest.Type.VALIDATE_ATTRIBUTES)
    public void validateAttribute(Map<String, Object> connectionAttributes) {

        AzureFilesAttributes attributes = AzureFilesAttributes.create(invoker, connectionAttributes);

        // Auto-populate SAS Token Expiry from SAS token if not provided by user
        if (!attributes.isUserProvidedExpiry() && attributes.hasSasTokenExpiry()) {
            connectionAttributes.put(Constants.SAS_TOKEN_EXPIRY, attributes.getSasTokenExpiry());
            LOGGER.info("SAS Token Expiry auto-populated from token: {}", attributes.getSasTokenExpiry());
        }

        validateAndTestConnection(attributes);
    }

    /**
     * Tests SAS token connection to Azure File Share
     */
    @InvokerRequest(InvokerRequest.Type.TEST_CONNECTION)
    public void testConnection() {
        // Get current attributes from invoker (not injected field which may be empty)
        AzureFilesAttributes currentAttributes = new AzureFilesAttributes(invoker.getAttributes());
        validateAndTestConnection(currentAttributes);
    }


    private void validateAndTestConnection(AzureFilesAttributes attributes) {
        try {
            // Validate configuration before attempting connection
            LOGGER.debug("Validating Azure Files configuration: {}", attributes);
            attributes.validateConfiguration();
            attributes.validateSasTokenExpiry();

            // Attempt connection test
            AzureFileRestClient client = new AzureFileRestClient(attributes);
            boolean connectionSuccess = client.testConnection();

            if (!connectionSuccess) {
                throw new RuntimeException("Connection test failed. Please verify your Storage Account Name, File Share Name, and SAS Token are correct. Ensure your SAS token has 'Read' and 'List' permissions and hasn't expired.");
            }

            LOGGER.info("Azure Files connection test successful using SAS Token authentication");
        } catch (IllegalArgumentException cause) {
            // Configuration validation failed
            LOGGER.error("Azure Files configuration validation failed: {}", cause.getMessage());
            throw new RuntimeException(cause.getMessage(), cause);
        } catch (Exception cause) {
            LOGGER.error("Azure Files SAS Token connection test failed: {}", cause.getMessage());
            throw new RuntimeException("Connection test failed: " + cause.getMessage() + ". Please check your configuration and ensure your Azure File Share is accessible.", cause);
        }
    }
}