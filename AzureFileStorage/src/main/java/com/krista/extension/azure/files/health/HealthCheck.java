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

package com.krista.extension.azure.files.health;

import app.krista.extension.executor.ExtensionResponse;
import app.krista.extension.executor.Invoker;
import com.krista.extension.azure.files.AzureFilesAttributes;
import com.krista.extension.azure.files.catalog.ExtensionResponseFactory;
import com.krista.extension.azure.files.catalog.entities.ExtensionResponseMeta;
import com.krista.extension.azure.files.catalog.entities.HealthStatus;
import com.krista.extension.azure.files.client.AzureFileRestClient;
import com.krista.extension.azure.files.service.AzureFilesTelemetryService;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class HealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheck.class);
    private static final String METRIC_PREFIX = "azure.files.health";
    private final Instant startTime = Instant.now();
    private long lastHealthCheckTime = 0;

    private final Invoker invoker;
    private final AzureFilesTelemetryService telemetryService;

    @Inject
    public HealthCheck(Invoker invoker, AzureFilesTelemetryService telemetryService) {
        this.invoker = invoker;
        this.telemetryService = telemetryService;
        LOGGER.debug("Azure Files HealthCheck service initialized");
    }

    public ExtensionResponse checkHealth() {
        long startTime = System.currentTimeMillis();
        String operationId = "azure_files_health_check_" + startTime;
        LOGGER.debug("Performing Azure Files health check with operation ID: {}", operationId);
        lastHealthCheckTime = lastHealthCheckTime == 0 ? System.currentTimeMillis() : lastHealthCheckTime;

        // Record health check request start
        AzureFilesTelemetryService.TelemetryContext telemetryContext =
                telemetryService.recordRequestStart(METRIC_PREFIX, "health_check");

        // Always read fresh attributes from the invoker to pick up latest config changes
        AzureFilesAttributes attributes = new AzureFilesAttributes(invoker.getAttributes());
        LOGGER.info("Loaded fresh attributes for health check: {}", attributes);

        Map<String, Object> healthData = new HashMap<>();
        String healthSummaryMessage = "";

        try {
            healthData = collectSystemMetrics();
            healthSummaryMessage = healthSummaryMessage + "Collected system metrics.\n";
            healthData.put("LastHealthCheckTime", lastHealthCheckTime);

            if (!attributes.hasSasToken()) {
                LOGGER.info("Azure Files is not configured for invoker ID: {}", invoker.getInvokerId());
                healthData.put("SystemStatus", "NOT_CONFIGURED");
                healthData.put("Message", "Azure Files is not configured");
                healthData.put("SasTokenValid", false);

                // Record not_configured status
                recordHealthMetrics("NOT_CONFIGURED", false, 0);
            } else {
                // Validate SAS token expiry - use the field value first, fall back to parsing from token
                String systemStatus = "HEALTHY";
                boolean sasTokenValid = true;
                String sasTokenExpiry = attributes.hasSasTokenExpiry()
                        ? attributes.getSasTokenExpiry()
                        : AzureFilesAttributes.parseSasTokenExpiry(attributes.getSasToken());
                String daysUntilExpiry = "";
                LOGGER.info("SAS Token Expiry being used for health check: {}", sasTokenExpiry);

                healthData.put("SasTokenExpiry", sasTokenExpiry);

                if (!sasTokenExpiry.isEmpty()) {
                    try {
                        ZonedDateTime expiryDate = ZonedDateTime.parse(sasTokenExpiry, DateTimeFormatter.ISO_DATE_TIME);
                        ZonedDateTime now = ZonedDateTime.now();
                        long daysRemaining = ChronoUnit.DAYS.between(now, expiryDate);
                        daysUntilExpiry = String.valueOf(daysRemaining);

                        if (expiryDate.isBefore(now)) {
                            systemStatus = "UNHEALTHY";
                            sasTokenValid = false;
                            healthSummaryMessage = healthSummaryMessage
                                    + "ALERT: SAS Token has EXPIRED on " + sasTokenExpiry
                                    + ". Please generate a new SAS token from Azure Portal immediately.\n";
                            LOGGER.error("ALERT: SAS Token has expired: expiry={}", sasTokenExpiry);
                        } else if (daysRemaining <= 7) {
                            systemStatus = "UNHEALTHY";
                            healthSummaryMessage = healthSummaryMessage
                                    + "ALERT: SAS Token is expiring in " + daysRemaining + " day(s) on " + sasTokenExpiry
                                    + ". Please generate a new SAS token from Azure Portal before it expires.\n";
                            LOGGER.warn("ALERT: SAS Token expiring soon - {} day(s) remaining, expiry={}", daysRemaining, sasTokenExpiry);
                        } else if (daysRemaining <= 30) {
                            healthSummaryMessage = healthSummaryMessage
                                    + "WARNING: SAS Token expires in " + daysRemaining + " days on " + sasTokenExpiry
                                    + ". Consider renewing it soon.\n";
                            LOGGER.info("SAS Token expiry approaching: {} days remaining", daysRemaining);
                        } else {
                            healthSummaryMessage = healthSummaryMessage
                                    + "SAS Token is valid. Expires in " + daysRemaining + " days.\n";
                        }
                    } catch (DateTimeParseException e) {
                        healthSummaryMessage = healthSummaryMessage + "Could not parse SAS token expiry date.\n";
                        LOGGER.warn("Could not parse SAS token expiry date: {}", e.getMessage());
                    }
                } else {
                    healthSummaryMessage = healthSummaryMessage + "SAS Token does not contain an expiry date.\n";
                    LOGGER.warn("SAS Token does not contain an expiry date");
                }

                // Test connection to Azure File Share
                try {
                    AzureFileRestClient client = new AzureFileRestClient(attributes);
                    boolean connectionSuccess = client.testConnection();
                    if (!connectionSuccess) {
                        systemStatus = "UNHEALTHY";
                        sasTokenValid = false;
                        healthSummaryMessage = healthSummaryMessage + "Connection test to Azure File Share failed.\n";
                        LOGGER.error("Azure File Share connection test failed");
                    } else {
                        healthSummaryMessage = healthSummaryMessage + "Connection to Azure File Share successful.\n";
                    }
                } catch (Exception e) {
                    LOGGER.error("Connection test error: {}", e.getMessage(), e);
                    healthSummaryMessage = healthSummaryMessage + "Connection test error: " + e.getMessage() + "\n";
                    String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (errorMessage.contains("network") || errorMessage.contains("connection") || errorMessage.contains("timeout")) {
                        systemStatus = "THIRD PARTY SYSTEM OFFLINE";
                    } else {
                        systemStatus = "UNHEALTHY";
                    }
                    sasTokenValid = false;
                }

                healthData.put("SystemStatus", systemStatus);
                healthData.put("SasTokenValid", sasTokenValid);
                healthData.put("SasTokenDaysUntilExpiry", daysUntilExpiry);

                // Record health check metrics for SigNoz alerting
                long days = daysUntilExpiry.isEmpty() ? 0 : Long.parseLong(daysUntilExpiry);
                recordHealthMetrics(systemStatus, sasTokenValid, days);

                LOGGER.info("Health data populated - SasTokenExpiry={}, SasTokenValid={}, SasTokenDaysUntilExpiry={}, SystemStatus={}",
                        healthData.get("SasTokenExpiry"), healthData.get("SasTokenValid"),
                        healthData.get("SasTokenDaysUntilExpiry"), healthData.get("SystemStatus"));
            }

            healthSummaryMessage = healthSummaryMessage + "Health check completed.\n";
            telemetryService.recordSuccess(telemetryContext);
            return toExtensionResponse(healthData, null, startTime, healthSummaryMessage);

        } catch (Exception cause) {
            LOGGER.error("Health check failed for operation {}: {}", operationId, cause.getMessage(), cause);
            healthSummaryMessage = healthSummaryMessage + "Error: " + cause.getMessage() + "\n";
            telemetryService.recordException(telemetryContext, cause);
            return toExtensionResponse(healthData, cause, startTime, healthSummaryMessage);
        }
    }

    /**
     * Records health-specific metrics for SigNoz alerting.
     * <p>
     * Metrics recorded:
     * - azure.files.health.status: Counter per system status (HEALTHY, UNHEALTHY, NOT_CONFIGURED, etc.)
     * - azure.files.health.token_expired: Counter incremented when token is invalid
     * - azure.files.health.token_days_remaining: Duration recording of days until token expiry
     * - azure.files.health.connection_failed: Counter incremented when connection test fails
     */
    private void recordHealthMetrics(String systemStatus, boolean tokenValid, long daysUntilExpiry) {
        // Record health status counter - SigNoz alert: azure_files_health.status where status != HEALTHY
        Map<String, String> statusAttributes = new HashMap<>();
        statusAttributes.put("status", systemStatus);
        telemetryService.incrementCounter(METRIC_PREFIX + ".status", statusAttributes);

        // Record token expired counter - SigNoz alert: azure_files_health.token_expired > 0
        if (!tokenValid) {
            Map<String, String> tokenAttributes = new HashMap<>();
            tokenAttributes.put("operation", "token_expired");
            telemetryService.incrementCounter(METRIC_PREFIX + ".token_expired", tokenAttributes);
        }

        // Record days remaining counter - SigNoz alert: azure_files_health.token_days_remaining < 7
        Map<String, String> expiryAttributes = new HashMap<>();
        expiryAttributes.put("days_remaining", String.valueOf(daysUntilExpiry));
        telemetryService.incrementCounter(METRIC_PREFIX + ".token_days_remaining", expiryAttributes);

        // Record connection failure - SigNoz alert: azure_files_health.connection_failed > 0
        if ("THIRD PARTY SYSTEM OFFLINE".equals(systemStatus)) {
            Map<String, String> connAttributes = new HashMap<>();
            connAttributes.put("reason", "offline");
            telemetryService.incrementCounter(METRIC_PREFIX + ".connection_failed", connAttributes);
        }
    }

    private Map<String, Object> collectSystemMetrics() {
        Map<String, Object> healthData = new HashMap<>();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        long availableMemory = maxMemory - usedMemory;
        double cpuUsage = getApproximateCpuUsage();

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        Duration uptime = Duration.between(startTime, Instant.now());
        double uptimeHours = Math.round((uptime.toMillis() / (1000.0 * 60 * 60)) * 100.0) / 100.0;

        healthData.put("CurrentMemoryUsageMB", (double) usedMemory);
        healthData.put("AvailableMemoryMB", (double) availableMemory);
        healthData.put("TotalMemoryMB", (double) maxMemory);
        healthData.put("CpuUsagePercentage", cpuUsage);
        healthData.put("ActiveThreads", (double) threadCount);
        healthData.put("UptimeHours", uptimeHours);
        return healthData;
    }

    private double getApproximateCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                double cpuUsage = sunOsBean.getProcessCpuLoad() * 100;
                if (cpuUsage >= 0 && cpuUsage <= 100) {
                    return Math.round(cpuUsage * 100.0) / 100.0;
                }
                double systemCpuUsage = sunOsBean.getSystemCpuLoad() * 100;
                if (systemCpuUsage >= 0 && systemCpuUsage <= 100) {
                    return Math.round(systemCpuUsage * 100.0) / 100.0;
                }
            }
            double load = osBean.getSystemLoadAverage();
            int processors = Runtime.getRuntime().availableProcessors();
            if (load < 0) {
                return 15.0;
            }
            double usage = (load / processors) * 100;
            return Math.min(Math.round(usage * 100.0) / 100.0, 100.0);
        } catch (Exception cause) {
            LOGGER.error("Error getting CPU usage", cause);
            return 15.0;
        }
    }

    private ExtensionResponse toExtensionResponse(Map<String, Object> healthData, Exception exception, long startTime, String healthSummaryMessage) {
        HealthStatus healthStatus = buildHealthStatus(healthData);
        double timeTakenInSeconds = (System.currentTimeMillis() - startTime) / 1000.0;

        try {
            boolean isHealthy = "HEALTHY".equals(healthStatus.systemStatus);
            Map<String, Object> extensionResponse = buildExtensionResponse(isHealthy, healthStatus, timeTakenInSeconds, exception, healthSummaryMessage);
            LOGGER.info("Health check response created in {}s, status={}", timeTakenInSeconds, healthStatus.systemStatus);
            return ExtensionResponseFactory.create(extensionResponse);
        } catch (Exception cause) {
            LOGGER.error("Error creating health response: {}", cause.getMessage(), cause);
            healthStatus.systemStatus = "UNHEALTHY";
            Map<String, Object> extensionResponse = buildExtensionResponse(false, healthStatus, timeTakenInSeconds, cause, healthSummaryMessage);
            return ExtensionResponseFactory.create(extensionResponse);
        }
    }

    private HealthStatus buildHealthStatus(Map<String, Object> healthData) {
        LOGGER.info("Building HealthStatus from healthData keys: {}", healthData.keySet());

        HealthStatus healthStatus = new HealthStatus();
        healthStatus.extensionName = "Azure File Service";
        healthStatus.lastHealthCheckTime = (Long) healthData.getOrDefault("LastHealthCheckTime", System.currentTimeMillis());
        healthStatus.currentMemoryUsageMB = convertToDouble(healthData.get("CurrentMemoryUsageMB"));
        healthStatus.availableMemoryMB = convertToDouble(healthData.get("AvailableMemoryMB"));
        healthStatus.totalMemoryMB = convertToDouble(healthData.get("TotalMemoryMB"));
        healthStatus.cPUUsagePercentage = convertToDouble(healthData.get("CpuUsagePercentage"));
        healthStatus.activeThreads = convertToDouble(healthData.get("ActiveThreads"));
        healthStatus.uptimeHours = convertToDouble(healthData.get("UptimeHours"));
        healthStatus.systemStatus = (String) healthData.getOrDefault("SystemStatus", "UNKNOWN");
        healthStatus.tokenExpiry = (String) healthData.getOrDefault("SasTokenExpiry", "");
        healthStatus.tokenValid = (Boolean) healthData.getOrDefault("SasTokenValid", false);
        healthStatus.daysUntilTokenExpiry = (String) healthData.getOrDefault("SasTokenDaysUntilExpiry", "");

        LOGGER.info("HealthStatus built - tokenExpiry={}, tokenValid={}, daysUntilTokenExpiry={}, systemStatus={}",
                healthStatus.tokenExpiry, healthStatus.tokenValid, healthStatus.daysUntilTokenExpiry, healthStatus.systemStatus);
        return healthStatus;
    }

    private double convertToDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception cause) {
            return 0.0;
        }
    }

    private Map<String, Object> buildExtensionResponse(boolean isHealthy, HealthStatus healthStatus, double timeTakenInSeconds, Exception exception, String healthSummaryMessage) {
        String responseMessage;
        if (isHealthy) {
            responseMessage = "Health check completed successfully. All systems operational.\n"
                    + "Token Expiry: " + healthStatus.tokenExpiry + "\n"
                    + "Token Valid: " + healthStatus.tokenValid + "\n"
                    + "Days Until Expiry: " + healthStatus.daysUntilTokenExpiry;
        } else {
            responseMessage = "System status: " + healthStatus.systemStatus + "\n" + healthSummaryMessage;
        }

        ExtensionResponseMeta meta = new ExtensionResponseMeta();
        meta.message = responseMessage;
        meta.technicalDetailedErrorReport = exception != null ? Arrays.toString(exception.getStackTrace()) : "";
        meta.responseType = isHealthy ? "SUCCESS" : "UNHEALTHY";
        meta.timeTakenInSeconds = timeTakenInSeconds;

        if (!isHealthy) {
            String errorMessage = exception != null ? exception.getMessage() : "";
            String sasExpiryAlert = "";
            if (healthStatus.tokenExpiry != null && !healthStatus.tokenExpiry.isEmpty()) {
                sasExpiryAlert = "Token Expiry: " + healthStatus.tokenExpiry + "\n"
                        + "Days Until Expiry: " + healthStatus.daysUntilTokenExpiry + "\n"
                        + "Token Valid: " + healthStatus.tokenValid + "\n";
            }
            String emailBody = "ALERT: Azure File Service Health Check Failed\n\n"
                    + "System Status: " + healthStatus.systemStatus + "\n"
                    + sasExpiryAlert
                    + healthSummaryMessage + "\n"
                    + "Extension Name: " + healthStatus.extensionName
                    + ", Invoker Id: " + invoker.getInvokerId()
                    + ", Invoker Name: " + invoker.getInvokerName() + "\n"
                    + "Technical Detailed Error Report: " + meta.technicalDetailedErrorReport + "\n"
                    + "Error Message: " + errorMessage;
            LOGGER.debug("Health check alert details: {}", emailBody);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("Health Status", healthStatus);
        response.put("Is Healthy", isHealthy);
        response.put("Extension Response Meta", meta);
        return response;
    }
}
