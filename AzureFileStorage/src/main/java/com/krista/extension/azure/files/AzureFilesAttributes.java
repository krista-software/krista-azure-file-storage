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
import java.util.Map;

@Service
public class AzureFilesAttributes {
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureFilesAttributes.class);

    private String storageAccountName;
    private String fileShareName;
    private String sasToken;

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
                '}';
    }
}
