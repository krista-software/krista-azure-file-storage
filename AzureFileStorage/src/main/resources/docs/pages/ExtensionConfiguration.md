# Extension Configuration

## Overview

The Azure File Service Extension requires configuration of three essential parameters to connect to your Azure File Share. This configuration uses SAS Token authentication to provide secure, time-limited access to your Azure storage resources.

## Configuration Parameters

| Parameter | Type | Required | Description | Example |
|-----------|------|----------|-------------|---------|
| Storage Account Name | Text | Yes | The name of your Azure storage account | "mystorageaccount" |
| File Share Name | Text | Yes | The name of the file share within the storage account | "documents" |
| SAS Token | Text (Secured) | Yes | Shared Access Signature token with Read and List permissions | "?sv=2024-11-04&ss=f&srt=sco&sp=rl&se=2024-12-31T23:59:59Z..." |

### Parameter Details

#### Storage Account Name
- **Format**: 3-24 characters, lowercase letters and numbers only
- **Purpose**: Identifies your Azure storage account in the global namespace
- **Validation**: Must be a valid Azure storage account name
- **Example**: If your storage URL is `https://mystorageaccount.file.core.windows.net`, then the account name is `mystorageaccount`

#### File Share Name  
- **Format**: 3-63 characters, lowercase letters, numbers, and hyphens
- **Purpose**: Specifies which file share within the storage account to access
- **Validation**: Must exist within the specified storage account
- **Example**: `documents`, `project-files`, `shared-resources`

#### SAS Token
- **Format**: Query string starting with `?sv=` containing signed parameters
- **Purpose**: Provides secure, time-limited access with specific permissions
- **Security**: Stored securely and encrypted in Krista platform
- **Required Permissions**: Read (r) and List (l) at minimum

## Step-by-Step Setup

### 1. Access Extension Configuration
1. Log into your Krista platform
2. Navigate to **Extensions** → **Azure File Service**
3. Click the **Setup** tab
4. Locate the configuration form

### 2. Enter Storage Account Information
1. **Storage Account Name**: Enter your Azure storage account name
   - Find this in Azure Portal under Storage Accounts
   - Use only the account name, not the full URL
2. **File Share Name**: Enter the target file share name
   - Must exist within the specified storage account
   - Case-sensitive, use exact name from Azure Portal

### 3. Configure SAS Token
1. **Generate SAS Token**: Follow the [Authentication Guide](/pages/authentication.md) to create a proper SAS token
2. **Enter Token**: Paste the complete SAS token including the `?` prefix
3. **Verify Format**: Ensure the token starts with `?sv=` and contains all required parameters

### 4. Validate Configuration
1. Click **Validate Attributes** to check parameter format and connectivity
2. Review any validation errors and correct them
3. Ensure all required fields are properly filled

### 5. Test Connection
1. Click **Test Connection** to verify Azure File Share access
2. Confirm successful connection before saving
3. Address any connection errors before proceeding

### 6. Save Configuration
1. Click **Save Changes** to store the configuration
2. Verify the configuration is saved successfully
3. The extension is now ready for use

## Authentication Type Selection

The Azure File Service Extension uses **SAS Token authentication** exclusively. This method provides:

- **Security**: Time-limited access with specific permissions
- **Simplicity**: No complex OAuth flows or service principals required  
- **Flexibility**: Easy token rotation and permission management
- **Performance**: Direct access without additional authentication overhead

> **📝 Note**: While the extension code supports OAuth 2.0 and Connection String authentication, only SAS Token authentication is currently enabled and tested for production use.

## Security Considerations

### SAS Token Security
- **Minimal Permissions**: Grant only Read and List permissions unless additional operations are needed
- **Time Limits**: Set the shortest practical expiration time (recommended: 30-90 days)
- **HTTPS Only**: Always configure SAS tokens to require HTTPS protocol
- **IP Restrictions**: Consider restricting access to specific IP ranges if applicable
- **Regular Rotation**: Generate new tokens before expiration and update configuration

### Storage Account Security
- **Access Keys**: Never use storage account access keys directly in the extension
- **Network Access**: Configure storage account firewall rules if needed
- **Monitoring**: Enable Azure Storage logging and monitoring
- **Backup**: Ensure proper backup policies for your file shares

### Krista Platform Security
- **Secure Storage**: SAS tokens are encrypted and stored securely in Krista
- **Access Control**: Limit extension configuration access to authorized users
- **Audit Trail**: Monitor extension configuration changes through Krista logs

## Troubleshooting

### Configuration Validation Errors

**"Storage Account Name is required. Please enter the name of your Azure storage account..."**
- **Cause**: Storage account name field is empty or contains only whitespace
- **Resolution**: Enter a valid Azure storage account name (3-24 characters, lowercase). Find this in Azure Portal under Storage Accounts.

**"File Share Name is required. Please enter the name of your file share..."**
- **Cause**: File share name field is empty or contains only whitespace
- **Resolution**: Enter a valid file share name that exists in your storage account. Find this in Azure Portal under your Storage Account > File shares.

**"SAS Token is required. Please generate a SAS token from Azure Portal..."**
- **Cause**: SAS token field is empty or contains only whitespace
- **Resolution**: Generate a SAS token from Azure Portal with 'Read' and 'List' permissions. The token should start with '?sv=' and include all required parameters.

### Connection Test Failures

**"Connection test failed. Please verify your Storage Account Name, File Share Name, and SAS Token are correct..."**
- **Cause**: Invalid configuration parameters, expired token, or insufficient permissions
- **Resolution**:
  1. Verify SAS token format starts with `?sv=`
  2. Check token expiration date
  3. Ensure Read and List permissions are granted
  4. Verify storage account and file share names are correct

**"The file or directory was not found in your Azure File Share..."**
- **Cause**: Specified file share doesn't exist in the storage account
- **Resolution**:
  1. Verify file share name spelling and case
  2. Check that file share exists in Azure Portal
  3. Use 'List Of Directories' to browse available folders

**"Access denied. Your SAS token doesn't have the required permissions..."**
- **Cause**: SAS token lacks required permissions or has expired
- **Resolution**:
  1. Generate new SAS token with Read and List permissions
  2. Verify token hasn't expired
  3. Check that token includes all required resource types (Service, Container, Object)

### Common Configuration Issues

**Token Format Errors**
- Ensure SAS token includes the `?` prefix
- Copy the complete token from Azure Portal
- Avoid truncating or modifying the token string

**Permission Issues**
- Verify SAS token includes Read (r) and List (l) permissions
- Check that File service is selected in SAS token generation
- Ensure all resource types (Service, Container, Object) are included

**Network Connectivity**
- Verify internet connectivity from Krista platform
- Check if corporate firewalls block Azure storage endpoints
- Ensure HTTPS protocol is allowed

## Best Practices

### 1. Token Management
- Generate tokens with specific expiration dates
- Set up calendar reminders for token renewal
- Use descriptive names when generating multiple tokens
- Document token purposes and expiration dates

### 2. Security Hardening
- Use shortest practical token expiration times
- Restrict SAS token permissions to minimum required
- Enable Azure Storage logging for audit trails
- Regularly review and rotate access credentials

### 3. Configuration Management
- Document configuration parameters in your organization's knowledge base
- Test configuration changes in non-production environments first
- Maintain backup copies of working configurations
- Use consistent naming conventions for storage accounts and file shares

### 4. Monitoring and Maintenance
- Monitor extension usage through Krista platform logs
- Set up alerts for authentication failures
- Regularly review Azure Storage access logs
- Plan for token renewal before expiration

## See Also

- [Authentication Guide](/pages/authentication.md) - Detailed SAS token generation instructions
- [Creating Azure App](/pages/CreatingAzureApp.md) - Azure Storage Account setup guide
- [List Of Files](/pages/ListOfFiles.md) - File listing operations
- [Get File](/pages/GetFile.md) - File download operations
