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
@Entity(name = "Health Status", id = "localDomainEntity_a1f2b3c4-d5e6-7890-abcd-ef1234567890", primaryKey = "Extension Name", supportStore = false, options = {})
public class HealthStatus {

    @Searchable
    @ToString
    @Field.Text(name = "Extension Name", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Name of the extension reporting health status'")}, options = {})
    public String extensionName;

    @Searchable
    @ToString
    @Field(name = "Current Memory Usage MB", type = "Number", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Current memory usage in megabytes'")}, options = {})
    public Double currentMemoryUsageMB;

    @Searchable
    @ToString
    @Field(name = "Available Memory MB", type = "Number", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Available memory in megabytes'")}, options = {})
    public Double availableMemoryMB;

    @Searchable
    @ToString
    @Field(name = "Total Memory MB", type = "Number", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Total memory allocated in megabytes'")}, options = {})
    public Double totalMemoryMB;

    @Searchable
    @ToString
    @Field(name = "CPU Usage Percentage", type = "Number", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Current CPU usage as a percentage'")}, options = {})
    public Double cPUUsagePercentage;

    @Searchable
    @ToString
    @Field(name = "Active Threads", type = "Number", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Number of active threads'")}, options = {})
    public Double activeThreads;

    @Searchable
    @ToString
    @Field(name = "Uptime Hours", type = "Number", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'System uptime in hours'")}, options = {})
    public Double uptimeHours;

    @Searchable
    @ToString
    @Field.PickOne(name = "System Status", values = {"HEALTHY", "DEGRADED (SLOW RESPONSE)", "UNHEALTHY", "EXTENSION PROCESS OFFLINE", "THIRD PARTY SYSTEM OFFLINE", "NOT_CONFIGURED"}, required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Overall system status'")}, options = {})
    public String systemStatus;

    @Searchable
    @ToString
    @Field(name = "Last Health Check Time", type = "Time", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Timestamp of the last health check'")}, options = {})
    public Long lastHealthCheckTime;

    @Searchable
    @ToString
    @Field.Text(name = "Token Expiry", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Expiry date of the SAS token'")}, options = {})
    public String tokenExpiry;

    @Searchable
    @ToString
    @Field.Boolean(name = "Token Valid", required = true, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Whether the SAS token is currently valid and not expired'")}, options = {})
    public Boolean tokenValid;

    @Searchable
    @ToString
    @Field.Text(name = "Days Until Token Expiry", required = false, attributes = {@Attribute(name = "visualWidth", value = "S"), @Attribute(name = "toolTip", value = "'Number of days until SAS token expires'")}, options = {})
    public String daysUntilTokenExpiry;
}
