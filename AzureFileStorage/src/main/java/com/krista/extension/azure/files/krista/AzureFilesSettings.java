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

public class AzureFilesSettings {

    private final String storageAccountName;
    private final String fileShareName;
    private final String sasToken;

    public AzureFilesSettings() {
        this.storageAccountName = "vamssolution";
        this.fileShareName = "applicationFiles";
        this.sasToken = "REDACTED_SECRET";
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
}
