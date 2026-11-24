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

package com.krista.extension.azure.files.catalog.entities;

import app.krista.extension.impl.anno.*;

@Domain(id = "catEntryDomain_b77b40c4-1319-4b32-9b55-07d360453974",
        name = "Cloud Storage",
        ecosystemId = "catEntryEcosystem_84b53163-327b-4b1b-8c96-9334d292f9f5",
        ecosystemName = "Essentials",
        ecosystemVersion = "5b250296-4743-4dd2-9c4c-1c6412f7e969")
@Entity(name = "Extension Response Meta", id = "localDomainEntity_b2c3d4e5-f6a7-8901-bcde-fa2345678901", primaryKey = "Message", supportStore = false, options = {})
public class ExtensionResponseMeta {

    @Searchable
    @ToString
    @Field(name = "Message", required = true, type = "RichText", attributes = {@Attribute(name = "visualWidth", value = "M"), @Attribute(name = "toolTip", value = "'Detailed message on extension response'")}, options = {})
    public String message;

    @Field(name = "Technical Detailed Error Report", type = "RichText", required = false, attributes = {@Attribute(name = "visualWidth", value = "L"), @Attribute(name = "toolTip", value = "'Technical error details for troubleshooting'")}, options = {})
    public String technicalDetailedErrorReport;

    @Field.PickOne(name = "Response Type", values = {"SUCCESS", "UNHEALTHY", "INPUT_ERROR", "AUTHENTICATION_ERROR", "SYSTEM_ERROR"}, required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Response type to categorize the result'")}, options = {})
    public String responseType;

    @Field(name = "Time Taken In Seconds", type = "Number", required = true, attributes = {@Attribute(name = "visualWidth", value = "S")}, options = {})
    public Double timeTakenInSeconds;
}
