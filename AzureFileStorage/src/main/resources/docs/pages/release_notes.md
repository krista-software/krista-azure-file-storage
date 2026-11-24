# Release Notes - Krista Azure File Service Extension

## Version 1.0.4

- **Developer Name**: Ramgopal Patidar
- **Krista Service APIs Java**: 1.0.121
- **Global Catalog Version**: GC-2026.4.3
- **Jira**: KE-3149

### Bug Fix

#### KE-3149: Non-ASCII Characters in Filenames Cause Extension Crash
Fixed a crash that occurred when Azure file names contained non-ASCII characters such as `®`, `™`, or Unicode smart quotes (`'`, `"`, `"`). On servers running in non-UTF-8 locales, `java.nio.file.InvalidPathException` was thrown during temp file creation, halting the Krista conversation.

- **Root Cause**: `java.nio.file.Paths.get()` rejected filenames containing characters outside the system's default charset on certain OS/JVM configurations.
- **Fix**: Introduced `FilenameUtil.sanitizeForFs()` — a single, centralized method that strips non-ASCII characters before any on-disk file operations. The original filename is preserved for display, Krista file upload, and logging.
- **Affected Files**: `AzureFileDownloadServiceImpl`, `KristaMediaClient`
- **Regression Coverage**: 6 new unit tests (`NonAsciiFilenames` class in `AzureFileDownloadServiceImplTest`) and 3 integration tests (Orders 7–9 in `AzureFilesIntegrationTest`) covering `®`, `™`, smart quotes, and backslash combinations.

### Enhancement

#### Code Quality & Refactoring
Significant code quality improvements following AI code review recommendations:

- **Inner classes converted to Java records**: `DownloadResult`, `ListingResult`, `FetchResult`, and `AzureHttpErrorHandler.ErrorResponse` replaced with concise, immutable records.
- **Removed dead code**: Eliminated unused wrapper methods, a dead `validateBasePath()` method, delegation debug log statements in `AzureFileServiceImpl`, and a duplicate private `sanitizeForFs()` in `KristaMediaClient`.
- **Centralized filename sanitization**: `FilenameUtil.sanitizeForFs()` is now the single source of truth, removing duplicate implementations.
- **Response key constants**: Hardcoded string keys (`"File Info"`, `"Success"`, `"List Of Files"`, etc.) extracted into `Constants` to prevent typos and improve maintainability.
- **Catalog failure contract enforced**: All catch blocks in `AzureFilesOperationArea` now return a structured `Success=false` response instead of `ExtensionResponseBuilder.failure()`, which was causing Krista conversations to halt at `ReflectInvoker:621`.
- **Non-recursive error propagation fixed**: HTTP 404/401/403 from Azure now correctly surface as `AzureFilesException` on non-recursive listings instead of silently returning an empty list.
- **Null-safe recursive flag**: `Boolean.TRUE.equals(recursiveSearch)` guard added to prevent `NullPointerException` when the recursive parameter is unset.

### Test Coverage

| Test File | Description |
|-----------|-------------|
| `AzureFileDownloadServiceImplTest.java` | Full Mockito rewrite — 18 tests covering path validation, HTTP errors, file size, non-ASCII filenames (KE-3149), and exception handling |
| `AzureFileListingServiceImplTest.java` | Full Mockito rewrite — 16 tests covering file/directory listing, extension filters, HTTP error propagation, and path normalization |
| `AzureFilesIntegrationTest.java` | New integration test — 11 ordered tests against real Azure credentials covering listing, download, KE-3149 non-ASCII regressions, and error handling |

### Files Modified

| File | Changes |
|------|---------|
| `impl/AzureFileDownloadServiceImpl.java` | Non-ASCII filename crash fix; removed wrapper methods; `DownloadResult` → record |
| `impl/AzureFileListingServiceImpl.java` | Fixed HTTP error propagation; removed dead code; `ListingResult`/`FetchResult` → records |
| `impl/AzureFileServiceImpl.java` | Removed unnecessary delegation debug logs |
| `krista/KristaMediaClient.java` | Removed duplicate `sanitizeForFs()`; uses `FilenameUtil.sanitizeForFs()` |
| `catalog/AzureFilesOperationArea.java` | Catalog failure contract fix; null-safe recursive flag; hardcoded keys → Constants |
| `util/FilenameUtil.java` | Added `sanitizeForFs()` as centralized non-ASCII filename sanitizer |
| `util/Constants.java` | Added response key constants and error code constants |
| `util/AzureHttpErrorHandler.java` | `ErrorResponse` inner class → Java record |
| `util/ResponseHandler.java` | Hardcoded string keys replaced with Constants |
| `build.gradle` | Exclude `@Tag("integration")` from default `test` task; added `integrationTest` task |

### Files Added

| File | Description |
|------|-------------|
| `src/test/java/.../integration/AzureFilesIntegrationTest.java` | Integration test suite for real Azure credential validation |
| `src/main/resources/config.properties.test` | Template for integration test credentials (safe to commit) |

---

## Version 1.0.3

- **Developer Name**: Simran Sethi
- **Krista Service APIs Java**: 1.0.121
- **Global Catalog Version**: GC-2026.3.4
- **Jira**: KE-3085

### Enhancement

#### OpenTelemetry Integration
Added OpenTelemetry-based metrics tracking for all catalog operations (List Files, List Directories, Get File).

- **New Service**: `AzureFilesTelemetryService` - Reusable telemetry service that tracks request counts, success/failure counters, and operation duration histograms using OpenTelemetry.
- **Metrics Recorded**:
  - `azure.files.requests` - Total request counter per operation
  - `azure.files.success` - Successful operation counter
  - `azure.files.failure` - Failed operation counter
  - `azure.files.duration` - Operation duration histogram (milliseconds)
- **Integration**: All three catalog operations (`list_files`, `list_directories`, `get_file`) in `AzureFilesOperationArea` now record telemetry on start, success, failure, and exception.

#### Health Check API
Added a new **Health Check** catalog request in the Setup area for monitoring extension health and SAS token status.

- **New Catalog Request**: `Health Check` in `SetupArea` - Checks overall extension health including system metrics and SAS token validity.
- **New Entities**:
  - `Health Status` - Reports system metrics (memory, CPU, threads, uptime) and SAS token status (expiry, validity, days remaining).
  - `Extension Response Meta` - Standardized response metadata with message, response type, error details, and time taken.
- **Health Metrics Collected**: Current/available/total memory (MB), CPU usage percentage, active threads, uptime hours, system status, SAS token expiry, token validity, and days until token expiry.
- **System Status Values**: `HEALTHY`, `DEGRADED (SLOW RESPONSE)`, `UNHEALTHY`, `EXTENSION PROCESS OFFLINE`, `THIRD PARTY SYSTEM OFFLINE`, `NOT_CONFIGURED`.
- **SAS Token Monitoring**: Automatically checks token expiry and flags warnings (<=30 days), alerts (<=7 days), and expired tokens.
- **Connection Test**: Validates connectivity to Azure File Share as part of the health check.

#### Configuration Changes
- **Removed Expiry Validation on Setup Page**: The setup page no longer throws errors for user-provided SAS Token Expiry values. Any value is accepted during configuration; validation is deferred to the Health Check.
- **SAS Token Expiry Field**: Added as a configuration attribute (`Constants.SAS_TOKEN_EXPIRY`). Auto-populates from the SAS token `se` parameter if left empty.

### Files Added

| File | Description |
|------|-------------|
| `catalog/SetupArea.java` | New Setup area with Health Check catalog request |
| `catalog/ExtensionResponseFactory.java` | Factory for creating standardized extension responses |
| `catalog/entities/HealthStatus.java` | Health Status entity with system and token metrics |
| `catalog/entities/ExtensionResponseMeta.java` | Extension Response Meta entity for response metadata |
| `health/HealthCheck.java` | Health check service with system metrics collection, SAS token validation, and connection testing |
| `service/AzureFilesTelemetryService.java` | Reusable OpenTelemetry telemetry service |

### Files Modified

| File | Changes |
|------|---------|
| `catalog/AzureFilesOperationArea.java` | Added telemetry tracking to all three catalog operations |
| `AzureFilesExtension.java` | Registered new services (HealthCheck, TelemetryService, SetupArea, entities) |
| `AzureFilesAttributes.java` | Added SAS Token Expiry field, auto-populate from token, expiry parsing utilities, removed expiry validation from setup |
| `util/Constants.java` | Added `SAS_TOKEN_EXPIRY` constant |
| `build.gradle` | Added OpenTelemetry dependency |

### Test Coverage

| Test File | Description |
|-----------|-------------|
| `AzureFilesOperationAreaTelemetryTest.java` | Tests for telemetry integration in all catalog operations |
| `AzureFilesTelemetryServiceTest.java` | Unit tests for telemetry service (start, success, failure, exception recording) |

---

## Version 1.0.1

- **Developer Name**: Ramgopal Patidar
- **Krista Service APIs Java**: 1.0.117
- **Global Catalog Version**: GC-2025.11.2

### BugFix
**KE-2570**: Azure extension taking extensive time to download files as of 10/2/2025

### Implementation Details

- Disabled wire logging in log4j2.xml and added proper application-level logging.
---

## Version 1.0.1

- **Developer Name**: Ramgopal Patidar
- **Krista Service APIs Java**: 1.0.115

### Initial Release
**KE-2117** -As an Author I need to be able to access Azure File Service via REST

### Implementation Details

- **SAS Token Authentication**: Implemented secure authentication using Azure Shared Access Signature (SAS) tokens with support for Read and List permissions. Provides time-limited, granular access control without exposing storage account keys.

- **Core File Operations**: Developed three primary catalog requests:
  - **List Files**: Browse and filter files by extension with recursive directory search
  - **List Directories**: Explore directory structures with hierarchical navigation
  - **Get File**: Download files with metadata including size, content type, and last modified date

- **REST Client Implementation**: Created `AzureFileRestClient.java` with HTTP/2 support, automatic retry logic (3 attempts with exponential backoff), file size validation (500MB limit), and comprehensive logging with SAS token sanitization for security.

- **Java 21 & Modern Architecture**: Built on Java 21 with HK2 dependency injection, separation of concerns through service layers, and comprehensive JavaDoc documentation. Follows SOLID principles and industry best practices.

