# Health Check

## Overview

The Health Check catalog request monitors the overall health of the Azure File Service extension. It collects system metrics, validates SAS token status, and tests connectivity to the Azure File Share. This request is available in the **Setup** area.

## Request Details

| Property | Value |
|----------|-------|
| **Name** | Health Check |
| **Area** | Setup |
| **Type** | Change System |
| **Description** | Checks the health of the Azure File Service extension including SAS token validity and expiry status |

## Response Fields

### Is Healthy

| Property | Value |
|----------|-------|
| **Type** | Boolean |
| **Required** | No |
| **Description** | `true` if the extension is fully healthy, `false` otherwise |

### Health Status Entity

The Health Status entity provides detailed system and token metrics.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| Extension Name | Text | Yes | Name of the extension (`Azure File Service`) |
| Current Memory Usage MB | Number | Yes | Current heap memory usage in megabytes |
| Available Memory MB | Number | Yes | Available heap memory in megabytes |
| Total Memory MB | Number | Yes | Total allocated heap memory in megabytes |
| CPU Usage Percentage | Number | Yes | Current CPU usage as a percentage |
| Active Threads | Number | Yes | Number of active threads in the JVM |
| Uptime Hours | Number | Yes | Extension uptime in hours |
| System Status | PickOne | Yes | Overall system status (see values below) |
| Last Health Check Time | Time | Yes | Timestamp of the last health check |
| Token Expiry | Text | No | SAS token expiry date in ISO 8601 format |
| Token Valid | Boolean | Yes | Whether the SAS token is currently valid |
| Days Until Token Expiry | Text | No | Number of days until the SAS token expires |

#### System Status Values

| Value | Meaning |
|-------|---------|
| `HEALTHY` | All systems operational, SAS token valid |
| `UNHEALTHY` | SAS token expired/expiring soon, or connection test failed |
| `NOT_CONFIGURED` | Extension has not been configured with a SAS token |
| `THIRD PARTY SYSTEM OFFLINE` | Network/connection error reaching Azure |
| `DEGRADED (SLOW RESPONSE)` | System responding but with degraded performance |
| `EXTENSION PROCESS OFFLINE` | Extension process is not running |

### Extension Response Meta Entity

Standardized response metadata for all extension operations.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| Message | RichText | Yes | Detailed message about the health check result |
| Technical Detailed Error Report | RichText | No | Stack trace or technical error details for troubleshooting |
| Response Type | PickOne | Yes | `SUCCESS`, `UNHEALTHY`, `INPUT_ERROR`, `AUTHENTICATION_ERROR`, or `SYSTEM_ERROR` |
| Time Taken In Seconds | Number | Yes | Duration of the health check in seconds |

## SAS Token Validation on Attribute Save

In addition to health check monitoring, SAS token expiry is also validated when attributes are saved:

| Condition | Behavior |
|-----------|----------|
| Expiry field provided but doesn't match token's `se` parameter | Validation error with mismatch details |
| Expiry field empty and token expires within 7 days | Alert prompting user to add expiry date or renew token |
| Token already expired | Validation error regardless of expiry field |
| Invalid expiry date format | Validation error with expected format |

> **Note**: If the SAS Token Expiry field is left empty and the token has more than 7 days remaining, the expiry is auto-populated from the token without any alert.

## SAS Token Monitoring

The health check automatically evaluates SAS token status:

| Condition | System Status | Message |
|-----------|---------------|---------|
| Token expired | `UNHEALTHY` | ALERT: SAS Token has EXPIRED. Generate a new token immediately. |
| Expires within 7 days | `UNHEALTHY` | ALERT: SAS Token is expiring in N day(s). Generate a new token before it expires. |
| Expires within 30 days | `HEALTHY` | WARNING: SAS Token expires in N days. Consider renewing it soon. |
| More than 30 days remaining | `HEALTHY` | SAS Token is valid. Expires in N days. |
| No expiry date found | `HEALTHY` | SAS Token does not contain an expiry date. |

## Connection Test

As part of the health check, a connection test is performed against the Azure File Share. If the connection fails:

- **Network/Connection/Timeout errors** set status to `THIRD PARTY SYSTEM OFFLINE`
- **Other errors** (auth failures, invalid config) set status to `UNHEALTHY`
- **Successful connection** confirms the extension can reach Azure File Share

## Example Response

### Healthy System

```
Health check completed successfully. All systems operational.
Token Expiry: 2027-07-01T03:49:20Z
Token Valid: true
Days Until Expiry: 465
```

### Unhealthy System (Expired Token)

```
System status: UNHEALTHY
Collected system metrics.
ALERT: SAS Token has EXPIRED on 2025-01-15T00:00:00Z. Please generate a new SAS token from Azure Portal immediately.
Connection test to Azure File Share failed.
Health check completed.
```

### Not Configured

```
System status: NOT_CONFIGURED
Collected system metrics.
Azure Files is not configured.
Health check completed.
```

## Usage

1. Navigate to **Extensions** > **Azure File Service** > **Setup**
2. Run the **Health Check** request
3. Review the **Is Healthy** field for a quick status check
4. Examine the **Health Status** entity for detailed metrics
5. Check the **Extension Response Meta** for additional context and any error details

## See Also

- [Extension Configuration](ExtensionConfiguration.md) - Setup and configuration guide
- [Authentication](authentication.md) - SAS Token management
- [Release Notes](release_notes.md) - Version history
