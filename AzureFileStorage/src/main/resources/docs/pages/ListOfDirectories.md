# List Of Directories

## Overview

Retrieves a list of all directories in an Azure File Share with recursive search, including the complete directory paths.

## Request Details

- **Area**: Azure File Service Operation
- **Type**: QUERY_SYSTEM
- **Retry Support**: ❌ No (Query operations are idempotent and don't require retry logic)

## Input Parameters

| Parameter Name | Type | Required | Description | Example |
|----------------|------|----------|-------------|---------|
| File Path | Text | Yes | The path in Azure File Share to list directories from | "documents/projects" |
| Recursive Search | Boolean | No | Whether to search recursively through subdirectories | true |

### Parameter Details

#### File Path
- **Purpose**: Specifies the directory path within the Azure File Share to search for subdirectories
- **Format**: Forward slash separated path (e.g., "folder/subfolder")
- **Root Directory**: Use empty string "" or "/" to search from the root of the file share
- **Case Sensitivity**: Path names are case-sensitive
- **Special Characters**: Avoid special characters except hyphens and underscores

#### Recursive Search
- **Purpose**: Controls whether to search subdirectories recursively
- **Default**: false (search current directory only)
- **Performance**: Non-recursive searches are faster for large directory structures
- **Depth Limit**: Recursive searches are limited to 10 levels deep for performance

## Output Parameters

| Parameter Name | Type | Description |
|----------------|------|-------------|
| List Of Directories | [ Composite ] | Array of directory paths found in the specified location |
| Success | Boolean | Indicates whether the operation completed successfully |

### Output Structure

**Successful Response**:
```json
{
  "List Of Directories": [
    {"List Of Directories": "documents/reports"},
    {"List Of Directories": "documents/templates"},
    {"List Of Directories": "documents/archive"}
  ],
  "Success": true
}
```

**Empty Directory Response**:
```json
{
  "List Of Directories": [],
  "Success": true
}
```

**Error Response**:
```json
{
  "List Of Directories": [],
  "Success": false
}
```

## Validation Rules

| Validation | Error Message | Resolution |
|------------|---------------|------------|
| File path format | "File path is required. Please enter the complete path to your file (e.g., 'documents/report.pdf' or 'images/logo.png')." | Provide a non-empty file path |
| Path length | "File path is too long. Maximum length is 1024 characters." | Use shorter path names |
| Invalid characters | "File path contains invalid characters." | Remove special characters except / - _ |
| Path depth | "Directory depth exceeds maximum limit of 10 levels." | Use shallower directory structures |

## Error Handling

### Input Errors (INPUT_ERROR)
**Cause**: Invalid or malformed input parameters
**Common Scenarios**:
- Null or empty file path when required
- Invalid characters in file path
- Path exceeds maximum length

**Resolution**: 
1. Verify file path is properly formatted
2. Ensure no special characters in paths
3. Use shorter path names if needed

### Logic Errors (LOGIC_ERROR)
**Cause**: Business logic validation failures
**Common Scenarios**:
- Path exceeds maximum depth limit
- Too many results (over 10,000 directories)
- Timeout during recursive search

**Error Messages**:
- "The operation timed out after 300 seconds because the directory structure is very large. Please try: 1) Turn off 'Recursive Search', 2) Use a more specific folder path, or 3) Add file extension filters to reduce results."
- "Results limited to 10,000 items for performance. To see more files, try searching in a specific folder or use file extension filters to narrow your search."

**Resolution**:
1. Use non-recursive search for large directory structures
2. Search more specific subdirectories
3. Break large searches into smaller chunks

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

### Example 1: List Root Level Directories

**Input**:
```
File Path: ""
Recursive Search: false
```

**Output**:
```json
{
  "List Of Directories": [
    {"List Of Directories": "documents"},
    {"List Of Directories": "images"},
    {"List Of Directories": "projects"}
  ],
  "Success": true
}
```

**Result**: Returns all directories in the root of the file share.

### Example 2: Recursive Directory Search

**Input**:
```
File Path: "projects"
Recursive Search: true
```

**Output**:
```json
{
  "List Of Directories": [
    {"List Of Directories": "projects/2023"},
    {"List Of Directories": "projects/2024"},
    {"List Of Directories": "projects/2024/website"},
    {"List Of Directories": "projects/2024/mobile-app"},
    {"List Of Directories": "projects/archive"}
  ],
  "Success": true
}
```

**Result**: Finds all directories in the projects folder and all subdirectories.

### Example 3: Specific Subdirectory Search

**Input**:
```
File Path: "documents/reports"
Recursive Search: false
```

**Output**:
```json
{
  "List Of Directories": [
    {"List Of Directories": "documents/reports/2023"},
    {"List Of Directories": "documents/reports/2024"},
    {"List Of Directories": "documents/reports/quarterly"}
  ],
  "Success": true
}
```

**Result**: Returns directories only within the specific reports folder.

## Business Rules

1. **Path Normalization**: Directory paths are automatically normalized (backslashes converted to forward slashes, leading/trailing slashes removed)
2. **Case Sensitivity**: Directory names are case-sensitive in Azure File Service
3. **Result Ordering**: Directories are returned in alphabetical order by full path
4. **Empty Directories**: Empty directories are included in results
5. **Hidden Directories**: Directories starting with "." are included in results
6. **Maximum Results**: Results are limited to 10,000 directories to prevent performance issues
7. **Timeout Protection**: Operations timeout after 5 minutes to prevent hanging requests
8. **Depth Limitation**: Recursive searches are limited to 10 directory levels

## Limitations

1. **Directory Count Limit**: Maximum 10,000 directories returned per request
2. **Recursive Depth**: Maximum 10 levels of directory recursion
3. **Path Length**: Maximum 1,024 characters per directory path
4. **Timeout**: 5-minute timeout for listing operations
5. **Performance**: Large directory structures (>1000 directories) may have slower response times

## Best Practices

### 1. Start with Specific Paths
Use specific directory paths instead of searching from root to improve performance and reduce result size.

### 2. Use Non-Recursive for Large Structures
For file shares with many nested directories, use non-recursive search and navigate manually to avoid timeouts.

### 3. Handle Empty Results
Check the Success field and handle empty result arrays appropriately in your application logic.

### 4. Implement Navigation Logic
Use directory listings to build navigation interfaces for browsing file share contents.

### 5. Cache Results
Consider caching directory listings for frequently accessed paths to improve user experience.

## Common Use Cases

### 1. File Share Navigation
```
Scenario: Build a directory tree for user navigation
File Path: ""
Recursive: true
Result: Complete directory structure for navigation interface
```

### 2. Project Structure Overview
```
Scenario: View all project directories for a specific year
File Path: "projects/2024"
Recursive: false
Result: All project directories within the 2024 folder
```

### 3. Archive Organization
```
Scenario: Find all archive directories across the file share
File Path: ""
Recursive: true
Result: All directories that can be used for organizing archived content
```

## Related Catalog Requests

- [List Of Files](/pages/ListOfFiles.md) - List files within discovered directories
- [Get File](/pages/GetFile.md) - Download files from specific directories

## Technical Implementation

### Helper Class
- **Class**: AzureFileListingServiceImpl
- **Package**: com.krista.extension.azure.files.impl
- **Validation**: Path normalization and depth validation
- **Service**: Azure File Service REST API integration

### Telemetry Metrics
- **AZURE_FILES_LIST_DIRECTORIES_SUCCESS**: Successful directory listing operations
- **AZURE_FILES_LIST_DIRECTORIES_ERROR**: Failed directory listing operations
- **AZURE_FILES_LIST_DIRECTORIES_TIMEOUT**: Operations that exceeded timeout limits
- **AZURE_FILES_LIST_DIRECTORIES_TRUNCATED**: Results truncated due to size limits

## Troubleshooting

### Performance Issues
**Cause**: Large directory structures or too many nested directories
**Solution**: 
1. Use more specific directory paths
2. Use non-recursive search
3. Break large searches into smaller chunks
4. Consider pagination for large result sets

### Empty Results
**Cause**: No subdirectories exist or directory doesn't exist
**Solution**:
1. Verify directory path exists
2. Check path spelling and case sensitivity
3. Try broader search criteria
4. Verify SAS token has List permissions

### Timeout Errors
**Cause**: Operation takes longer than 5 minutes
**Solution**:
1. Use non-recursive search
2. Search smaller directory sections
3. Check network connectivity
4. Verify directory structure isn't excessively deep

## See Also

- [Extension Configuration](/pages/ExtensionConfiguration.md) - Setup and configuration
- [Authentication](/pages/authentication.md) - SAS token requirements
- [List Of Files](/pages/ListOfFiles.md) - File listing operations
- [Get File](/pages/GetFile.md) - File download operations
