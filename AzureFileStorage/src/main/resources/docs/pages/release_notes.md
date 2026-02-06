# Release Notes - Krista Azure File Service Extension

## Version 1.0.1

- **Developer Name**: Ramgopal Patidar
- **Krista Service APIs Java**: 1.0.117
- **Global Catalog Version**: GC-2025.11.2

### BugFix
**KE-2570**: Azure extension taking extensive time to download files as of 10/2/2025

### Implementation Details

- Disabled wire logging in log4j2.xml and added proper application-level logging.
---

## Version 1.0.0

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

