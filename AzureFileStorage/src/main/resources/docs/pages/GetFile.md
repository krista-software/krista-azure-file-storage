# Get File

## Overview

Retrieves a file along with its downloadable content and metadata using the file path and name.

## Request Details

- **Area**: Azure File Service Operation
- **Type**: QUERY_SYSTEM
- **Retry Support**: ❌ No (Query operations are idempotent and don't require retry logic)

## Input Parameters

| Parameter Name | Type | Required | Description | Example |
|----------------|------|----------|-------------|---------|
| File Path | Text | Yes | The complete path to the file in Azure File Share including filename | "documents/reports/annual-report.pdf" |

### Parameter Details

#### File Path
- **Purpose**: Specifies the complete path to the file within the Azure File Share
- **Format**: Forward slash separated path including filename and extension (e.g., "folder/subfolder/filename.ext")
- **Case Sensitivity**: File and directory names are case-sensitive
- **Special Characters**: Avoid special characters except hyphens, underscores, and dots in filenames
- **File Extension**: Must include a valid file extension

## Output Parameters

| Parameter Name | Type | Description |
|----------------|------|-------------|
| File Info | FreeForm | Map containing file metadata (filename, file size, etc.) |
| Success | Boolean | Indicates whether the operation completed successfully |
| File | File | Krista file object containing the downloadable file content |

### Output Structure

**Successful Response**:
```json
{
  "File Info": {
    "filename": "annual-report.pdf",
    "filesizekb": "1.25 MB",
    "kristafile": "[File Object]"
  },
  "Success": true,
  "File": "[Krista File Object]"
}
```

**Error Response**:
```json
{
  "File Info": {
    "error": "File not found at path: documents/missing-file.pdf",
    "errorCode": "FILE_NOT_FOUND"
  },
  "Success": false,
  "File": null
}
```

### File Info Details

**Success Fields**:
- **filename**: Original filename with extension
- **filesizekb**: Human-readable file size (e.g., "1.25 MB", "512.34 KB")
- **kristafile**: Internal Krista file object reference

**Error Fields**:
- **error**: Human-readable error message
- **errorCode**: Machine-readable error code for programmatic handling

## Validation Rules

| Validation | Error Message | Resolution |
|------------|---------------|------------|
| File path required | "File path is required. Please enter the complete path to your file (e.g., 'documents/report.pdf' or 'images/logo.png')." | Provide a non-empty file path |
| File extension required | "The file '[filename]' doesn't have a file extension. Please ensure your file has a valid extension like .pdf, .docx, .xlsx, or .txt." | Ensure filename includes a valid extension |
| File extension allowed | "The file type '[extension]' is not supported for security reasons. Please use one of these supported formats: [list]" | Use a supported file extension |
| File size limit | "The file '[filename]' is too large ([size] MB). The maximum file size for download is 50 MB. Please use a smaller file or compress it before uploading to Azure File Service." | Use files smaller than 50 MB |
| Empty file | "The file '[filename]' appears to be empty (0 bytes). Please check that the file contains data and try uploading it again to Azure File Service." | Ensure file has content |

## Error Handling

### Input Errors (INPUT_ERROR)
**Cause**: Invalid or malformed input parameters
**Common Scenarios**:
- Null or empty file path
- File path without extension
- Invalid characters in file path

**Resolution**: 
1. Verify file path includes filename and extension
2. Check file path format is correct
3. Ensure no special characters in path

### Logic Errors (LOGIC_ERROR)
**Cause**: Business logic validation failures
**Common Scenarios**:
- File extension not in allowed list
- File size exceeds 50 MB limit
- File is empty (0 bytes)

**Error Messages**:
- "The file type '[extension]' is not supported for security reasons. Please use one of these supported formats: pdf, docx, xlsx, txt, jpg, png, gif, zip, csv, json, xml"
- "The file '[filename]' is too large ([size] MB). The maximum file size for download is 50 MB. Please use a smaller file or compress it before uploading to Azure File Service."
- "The file '[filename]' appears to be empty (0 bytes). Please check that the file contains data and try uploading it again to Azure File Service."

**Resolution**:
1. Use supported file types only
2. Ensure files are under 50 MB
3. Verify files contain data

### System Errors (SYSTEM_ERROR)
**Cause**: Azure service or network issues
**Common Scenarios**:
- Azure File Service service unavailable
- Network connectivity issues
- File download timeout

**Error Messages**:
- "There was a problem downloading the file from Azure File Service. Please check your internet connection and try again. If the issue persists, the file may be corrupted or temporarily unavailable."
- "The file is too large to download (100 MB limit). Please compress the file or use a smaller file. Large files can also be accessed directly through Azure Portal."

**Resolution**:
1. Check Azure service status
2. Verify network connectivity
3. Retry the operation
4. Use smaller files if size-related

### Authorization Errors
**Cause**: Authentication or permission issues
**Common Scenarios**:
- Invalid or expired SAS token
- Insufficient permissions (missing Read permission)
- File not found (404 error)

**Error Messages**:
- "Access denied. Your SAS token doesn't have the required permissions for this operation. Please generate a new SAS token with 'Read' and 'List' permissions, or contact your Azure administrator."
- "The file or directory '[filepath]' was not found in your Azure File Share. Please check the spelling and make sure the path exists. You can use 'List Of Directories' to browse available folders."
- "Authentication failed. Your SAS token is either invalid, expired, or incorrectly formatted. Please generate a new SAS token from Azure Portal and update your extension configuration."

**Resolution**:
1. Verify SAS token includes Read permission
2. Check token expiration date
3. Confirm file path exists and is correct
4. Verify storage account and file share names

## Usage Examples

### Example 1: Download PDF Document

**Input**:
```
File Path: "documents/reports/quarterly-report.pdf"
```

**Output**:
```json
{
  "File Info": {
    "filename": "quarterly-report.pdf",
    "filesizekb": "2.34 MB",
    "kristafile": "[File Object]"
  },
  "Success": true,
  "File": "[Krista File Object]"
}
```

**Result**: Successfully downloads the PDF file with metadata.

### Example 2: Download Excel Spreadsheet

**Input**:
```
File Path: "data/sales/2024-sales-data.xlsx"
```

**Output**:
```json
{
  "File Info": {
    "filename": "2024-sales-data.xlsx",
    "filesizekb": "856.12 KB",
    "kristafile": "[File Object]"
  },
  "Success": true,
  "File": "[Krista File Object]"
}
```

**Result**: Successfully downloads the Excel file with size information.

### Example 3: File Not Found Error

**Input**:
```
File Path: "documents/missing-file.docx"
```

**Output**:
```json
{
  "File Info": {
    "error": "File not found at path: documents/missing-file.docx",
    "errorCode": "FILE_NOT_FOUND"
  },
  "Success": false,
  "File": null
}
```

**Result**: Returns error when file doesn't exist.

## Business Rules

1. **File Size Limit**: Maximum file size is 50 MB for business operations (100 MB technical limit)
2. **Allowed Extensions**: Only files with recognized extensions are downloadable
3. **Path Normalization**: File paths are automatically normalized (backslashes to forward slashes)
4. **Case Sensitivity**: File and directory names are case-sensitive in Azure File Service
5. **Empty Files**: Files with 0 bytes are rejected
6. **Metadata Inclusion**: All successful downloads include file metadata
7. **Krista Integration**: Files are converted to Krista-compatible format automatically

## Limitations

1. **File Size**: Maximum 50 MB per file (business limit)
2. **File Types**: Limited to allowed file extensions for security
3. **Concurrent Downloads**: Subject to Azure File Service API rate limits
4. **Network Timeout**: Downloads timeout after 2 minutes
5. **Memory Usage**: Large files may impact system memory during processing

## Best Practices

### 1. Verify File Existence
Use List Of Files operation to verify file exists before attempting download.

### 2. Check File Size
Review file size in listings before downloading to avoid size limit errors.

### 3. Handle Large Files
For files approaching the 50 MB limit, consider alternative transfer methods.

### 4. Implement Error Handling
Always check the Success field and handle error responses appropriately.

### 5. Cache File Metadata
Store file metadata locally to avoid repeated downloads of the same file.

## Common Use Cases

### 1. Document Download
```
Scenario: Download a specific report for review
File Path: "reports/2024/annual-report.pdf"
Result: PDF file downloaded with metadata for user access
```

### 2. Data File Retrieval
```
Scenario: Get Excel file for data analysis
File Path: "data/exports/customer-data.xlsx"
Result: Excel file available for processing in Krista workflows
```

### 3. Image Asset Download
```
Scenario: Retrieve image file for presentation
File Path: "assets/images/company-logo.png"
Result: Image file downloaded for use in documents or presentations
```

## Related Catalog Requests

- [List Of Files](/pages/ListOfFiles.md) - Find files before downloading
- [List Of Directories](/pages/ListOfDirectories.md) - Navigate to file locations

## Technical Implementation

### Helper Class
- **Class**: AzureFileDownloadServiceImpl
- **Package**: com.krista.extension.azure.files.impl
- **Validation**: File path, extension, and size validation
- **Service**: Azure File Service REST API integration with Krista file conversion

### Telemetry Metrics
- **AZURE_FILES_DOWNLOAD_SUCCESS**: Successful file download operations
- **AZURE_FILES_DOWNLOAD_ERROR**: Failed file download operations
- **AZURE_FILES_DOWNLOAD_SIZE_EXCEEDED**: Downloads rejected due to size limits
- **AZURE_FILES_DOWNLOAD_TIMEOUT**: Downloads that exceeded timeout limits

## Troubleshooting

### File Not Found Errors
**Cause**: Specified file path doesn't exist
**Solution**: 
1. Verify file path spelling and case
2. Use List Of Files to confirm file exists
3. Check directory structure is correct
4. Ensure file hasn't been moved or deleted

### Size Limit Errors
**Cause**: File exceeds 50 MB business limit
**Solution**:
1. Use smaller files when possible
2. Compress files before uploading to Azure
3. Split large files into smaller chunks
4. Consider alternative transfer methods for very large files

### Permission Errors
**Cause**: SAS token lacks Read permissions
**Solution**:
1. Generate new SAS token with Read permission
2. Verify token hasn't expired
3. Check storage account and file share configuration
4. Ensure File service is included in SAS token

### Download Timeout
**Cause**: Network issues or very large files
**Solution**:
1. Check network connectivity
2. Retry the download operation
3. Use smaller files if possible
4. Verify Azure service availability

## See Also

- [Extension Configuration](/pages/ExtensionConfiguration.md) - Setup and configuration
- [Authentication](/pages/authentication.md) - SAS token requirements
- [List Of Files](/pages/ListOfFiles.md) - Find files to download
- [List Of Directories](/pages/ListOfDirectories.md) - Navigate file structure
