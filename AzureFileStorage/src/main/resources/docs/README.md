# Azure File Service Extension

## Overview

The Azure File Service Extension provides seamless integration between Krista platform and Azure File Share storage, enabling users to browse directories, list files, and download content directly through Krista conversations. This extension offers secure, efficient access to your Azure File Share resources with comprehensive error handling and validation.

## Key Features

- **📁 List Files** - Browse and filter files with extension-based filtering and recursive search
- **📂 List Directories** - Explore directory structures with recursive traversal capabilities
- **⬇️ Download Files** - Retrieve files as Krista-compatible file objects with metadata
- **🔐 Secure Authentication** - SAS Token authentication with time-limited access control
- **⚡ High Performance** - Optimized for large directory structures with timeout protection
- **🛡️ File Validation** - Built-in security with allowed file type restrictions and size limits
- **🩺 Health Check** - Monitor extension health, system metrics, and SAS token expiry status
- **📊 Telemetry** - OpenTelemetry-based metrics tracking for all catalog operations

## Quick Start Guide

1. **Create Azure Storage Account** - Set up your Azure Storage Account and File Share
2. **Generate SAS Token** - Create secure access token with appropriate permissions
3. **Configure Extension** - Install and configure the extension in Krista platform
4. **Test Operations** - Verify connectivity with catalog requests

👉 **[Start with Extension Configuration](pages/ExtensionConfiguration.md)** for detailed setup instructions.

## Documentation Structure

### Getting Started
- **[Extension Configuration](pages/ExtensionConfiguration.md)** - Complete setup and configuration guide
- **[Authentication](pages/authentication.md)** - SAS Token authentication and security
- **[Creating Azure App](pages/CreatingAzureApp.md)** - Azure Storage Account and File Share setup

### File Operations
- **[List Of Files](pages/ListOfFiles.md)** - Browse and filter files in directories
- **[List Of Directories](pages/ListOfDirectories.md)** - Explore directory structures
- **[Get File](pages/GetFile.md)** - Download files with metadata

### Monitoring
- **[Health Check](pages/HealthCheck.md)** - Extension health monitoring, system metrics, and SAS token status

### Additional Resources
- **[Release Notes](pages/release_notes.md)** - Version history and updates

## Support & Resources

- **Extension Version**: 1.0.3
- **Krista Service APIs Java**: 1.0.121
- **Global Catalog Version**: GC-2026.3.3
- **Developer**: Simran Sethi

### Quick Links
- [Extension Configuration](pages/ExtensionConfiguration.md) - Start here for setup
- [Authentication Guide](pages/authentication.md) - Security and token management
- [File Operations](pages/ListOfFiles.md) - Browse and download files
- [Troubleshooting](pages/ExtensionConfiguration.md#troubleshooting) - Common issues and solutions