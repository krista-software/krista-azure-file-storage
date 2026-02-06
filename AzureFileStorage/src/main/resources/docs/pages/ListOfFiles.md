# List Of Files

## Overview

Lists all files in an Azure File Share directory based on a specific file extension filter, with recursive search starting from a given folder path.

## Request Details

- **Area**: Azure File Service Operation
- **Type**: QUERY_SYSTEM
- **Retry Support**: ❌ No (Query operations are idempotent and don't require retry logic)

## Input Parameters

| Parameter Name | Type | Required | Description | Example |
|----------------|------|----------|-------------|---------|
| File Path | Text | Yes | The path in Azure File Share to list files from | "documents/reports" |
| File Extensions Filter | Text | No | Comma-separated list of file extensions to filter by | "pdf,docx,xlsx" |
| Recursive Search | Boolean | No | Whether to search recursively through subdirectories | true |

### Parameter Details

#### File Path
- **Purpose**: Specifies the directory path within the Azure File Share to search for files
- **Format**: Forward slash separated path (e.g., "folder/subfolder")
- **Root Directory**: Use empty string "" or "/" to search from the root of the file share
- **Case Sensitivity**: Path names are case-sensitive
- **Special Characters**: Avoid special characters except hyphens and underscores

#### File Extensions Filter
- **Purpose**: Limits results to files with specific extensions
- **Format**: Comma-separated list without dots (e.g., "pdf,docx" not ".pdf,.docx")
- **Case Insensitive**: Extensions are matched case-insensitively
- **No Filter**: Leave empty or null to include all file types
- **Common Extensions**: pdf, docx, xlsx, pptx, txt, jpg, png, gif, zip

#### Recursive Search
- **Purpose**: Controls whether to search subdirectories
- **Default**: false (search current directory only)
- **Performance**: Non-recursive searches are faster for large directory structures
- **Depth Limit**: Recursive searches are limited to 10 levels deep for performance

## Output Parameters

| Parameter Name | Type | Description |
|----------------|------|-------------|
| List Of Files | [ Composite ] | Array of file paths found in the specified directory |
| Success | Boolean | Indicates whether the operation completed successfully |

### Output Structure

**Successful Response**:
```json
{
  "List Of Files": [
    {"List Of Files": "documents/report1.pdf"},
    {"List Of Files": "documents/report2.docx"},
    {"List Of Files": "documents/data.xlsx"}
  ],
  "Success": true
}
```

**Empty Directory Response**:
```json
{
  "List Of Files": [],
  "Success": true
}
```

**Error Response**:
```json
{
  "List Of Files": [],
  "Success": false
}
```

## Validation Rules

| Validation | Error Message | Resolution |
|------------|---------------|------------|
| File path format | "File path is required. Please enter the complete path to your file (e.g., 'documents/report.pdf' or 'images/logo.png')." | Provide a non-empty file path |
| Path length | "File path is too long. Maximum length is 1024 characters." | Use shorter path names |
| Invalid characters | "File path contains invalid characters." | Remove special characters except / - _ |
| Extension format | "Invalid file extension format in filter." | Use comma-separated extensions without dots |

## Error Handling

### Input Errors (INPUT_ERROR)
**Cause**: Invalid or malformed input parameters
**Common Scenarios**:
- Null or empty file path when required
- Invalid characters in file path
- Malformed extension filter

**Resolution**:
1. Verify file path is properly formatted
2. Check extension filter uses comma-separated format
3. Ensure no special characters in paths

### Logic Errors (LOGIC_ERROR)
**Cause**: Business logic validation failures
**Common Scenarios**:
- Path exceeds maximum depth limit
- Too many results (over 10,000 files)
- Timeout during recursive search

**Error Messages**:
- "The operation timed out after 300 seconds because the directory structure is very large. Please try: 1) Turn off 'Recursive Search', 2) Use a more specific folder path, or 3) Add file extension filters to reduce results."
- "Results limited to 10,000 items for performance. To see more files, try searching in a specific folder or use file extension filters to narrow your search."

**Resolution**:
1. Use non-recursive search for large directories
2. Apply file extension filters to reduce results
3. Search more specific subdirectories

### System Errors (SYSTEM_ERROR)
**Cause**: Azure service or network issues
**Common Scenarios**:
- Azure File Service service unavailable
- Network connectivity issues
- Storage account access problems

**Error Messages**:
- "An unexpected error occurred while browsing your files. Please try again in a few minutes. If the problem continues, please contact support."

**Resolution**:
1. Check Azure service status
2. Verify network connectivity
3. Confirm SAS token is valid and not expired

### Authorization Errors
**Cause**: Authentication or permission issues
**Common Scenarios**:
- Invalid or expired SAS token
- Insufficient permissions (missing List permission)
- Incorrect storage account or file share name

**Error Messages**:
- "Access denied. Your SAS token doesn't have the required permissions for this operation. Please generate a new SAS token with 'Read' and 'List' permissions, or contact your Azure administrator."
- "The file or directory was not found in your Azure File Share. Please check the spelling and make sure the path exists. You can use 'List Of Directories' to browse available folders."

**Resolution**:
1. Verify SAS token includes List permission
2. Check token expiration date
3. Confirm storage account and file share names are correct

## Usage Examples

### Example 1: List All Files in Root Directory

**Input**:
```
File Path: ""
File Extensions Filter: null
Recursive Search: false
```

**Output**:
```json
{
  "List Of Files": [
    {"List Of Files": "readme.txt"},
    {"List Of Files": "config.json"},
    {"List Of Files": "data.csv"}
  ],
  "Success": true
}
```

**Result**: Returns all files in the root directory of the file share.

### Example 2: Find PDF Files Recursively

**Input**:
```
File Path: "documents"
File Extensions Filter: "pdf"
Recursive Search: true
```

**Output**:
```json
{
  "List Of Files": [
    {"List Of Files": "documents/reports/annual-report.pdf"},
    {"List Of Files": "documents/manuals/user-guide.pdf"},
    {"List Of Files": "documents/archive/old-report.pdf"}
  ],
  "Success": true
}
```

**Result**: Finds all PDF files in the documents directory and all subdirectories.

### Example 3: Office Documents in Specific Folder

**Input**:
```
File Path: "projects/2024"
File Extensions Filter: "docx,xlsx,pptx"
Recursive Search: false
```

**Output**:
```json
{
  "List Of Files": [
    {"List Of Files": "projects/2024/proposal.docx"},
    {"List Of Files": "projects/2024/budget.xlsx"},
    {"List Of Files": "projects/2024/presentation.pptx"}
  ],
  "Success": true
}
```

**Result**: Returns Microsoft Office documents in the specific 2024 project folder only.

## Business Rules

1. **Path Normalization**: File paths are automatically normalized (backslashes converted to forward slashes, leading/trailing slashes removed)
2. **Case Sensitivity**: File and directory names are case-sensitive in Azure File Service
3. **Extension Matching**: File extensions are matched case-insensitively
4. **Result Ordering**: Files are returned in alphabetical order by full path
5. **Empty Directories**: Empty directories are not included in file listings
6. **Hidden Files**: Files starting with "." are included in results
7. **Maximum Results**: Results are limited to 10,000 files to prevent performance issues
8. **Timeout Protection**: Operations timeout after 5 minutes to prevent hanging requests

## Limitations

1. **File Count Limit**: Maximum 10,000 files returned per request
2. **Recursive Depth**: Maximum 10 levels of directory recursion
3. **Path Length**: Maximum 1,024 characters per file path
4. **Timeout**: 5-minute timeout for listing operations
5. **Extension Filter**: Maximum 50 extensions per filter
6. **Performance**: Large directories (>1000 files) may have slower response times

## Best Practices

### 1. Optimize Search Scope
Use specific directory paths instead of searching from root to improve performance and reduce result size.

### 2. Apply Extension Filters
Use file extension filters to limit results to relevant file types and improve response times.

### 3. Use Non-Recursive for Large Directories
For directories with many subdirectories, use non-recursive search and navigate manually to avoid timeouts.

### 4. Handle Empty Results
Check the Success field and handle empty result arrays appropriately in your application logic.

### 5. Implement Pagination Logic
For large result sets, consider implementing client-side pagination or filtering to manage user experience.

## Common Use Cases

### 1. Document Discovery
```
Scenario: Find all PDF reports in the documents folder
File Path: "documents/reports"
Extension Filter: "pdf"
Recursive: true
Result: List of all PDF files in reports and subfolders
```

### 2. Media File Management
```
Scenario: List all images in the media directory
File Path: "media/images"
Extension Filter: "jpg,png,gif,bmp"
Recursive: false
Result: All image files in the specific directory
```

### 3. Project File Overview
```
Scenario: Get all Office documents for a specific project
File Path: "projects/website-redesign"
Extension Filter: "docx,xlsx,pptx"
Recursive: true
Result: All Office documents in project folder and subfolders
```

## Related Catalog Requests

- [List Of Directories](/pages/ListOfDirectories.md) - Browse directory structures
- [Get File](/pages/GetFile.md) - Download specific files found in listings

## Technical Implementation

### Helper Class
- **Class**: AzureFileListingServiceImpl
- **Package**: com.krista.extension.azure.files.impl
- **Validation**: Path normalization and extension filter parsing
- **Service**: Azure File Service REST API integration

### Telemetry Metrics
- **AZURE_FILES_LIST_FILES_SUCCESS**: Successful file listing operations
- **AZURE_FILES_LIST_FILES_ERROR**: Failed file listing operations
- **AZURE_FILES_LIST_FILES_TIMEOUT**: Operations that exceeded timeout limits
- **AZURE_FILES_LIST_FILES_TRUNCATED**: Results truncated due to size limits

## Troubleshooting

### Performance Issues
**Cause**: Large directory structures or too many files
**Solution**:
1. Use more specific file paths
2. Apply extension filters
3. Use non-recursive search
4. Break large searches into smaller chunks

### Empty Results
**Cause**: No files match criteria or directory doesn't exist
**Solution**:
1. Verify directory path exists
2. Check file extension filter spelling
3. Try broader search criteria
4. Verify SAS token has List permissions

### Timeout Errors
**Cause**: Operation takes longer than 5 minutes
**Solution**:
1. Use non-recursive search
2. Search smaller directory sections
3. Apply more restrictive extension filters
4. Check network connectivity

## See Also

- [Extension Configuration](/pages/ExtensionConfiguration.md) - Setup and configuration
- [Authentication](/pages/authentication.md) - SAS token requirements
- [List Of Directories](/pages/ListOfDirectories.md) - Directory browsing operations
- [Get File](/pages/GetFile.md) - File download operations

