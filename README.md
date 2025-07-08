# YAMLAnalyser

A Java-based security analysis tool that scans YAML configuration files from support tickets to identify secrets and credentials that require rotation.

## Overview

This tool automates the process of analyzing .yaml configuration files to detect potentially compromised secrets, passwords, and credentials. It generates separate reports for customers and support engineers with appropriate remediation guidance.

## Features

- **Automated Ticket Processing**: Downloads diagnostic files from support tickets
- **YAML Configuration Analysis**: Parses complex nested YAML structures to identify secrets
- **Dual Output Generation**: Creates customer-facing and engineer-only reports
- **Secret Classification**: Differentiates between customer-rotatable and engineer-only secrets
- **Freshdesk Integration**: Automatically uploads results and updates tickets via API
- **URL Credential Detection**: Identifies embedded credentials in HTTP/HTTPS URLs using regex patterns

## Architecture

### Core Components

- **TicketAnalyzer**: Main class handling ticket processing workflow
- **Product**: Model class representing different product types and their secret mappings
- **WSLCommandRunner**: Utility for executing shell commands via WSL
- **SyncSafely Integration**: File download/upload functionality

### Secret Detection

The tool identifies secrets in two categories:

1. **Site-wide Secrets**: Configuration parameters that affect entire deployments
2. **Product-specific Secrets**: Credentials tied to individual product instances

## Usage

### Setup

1. Set the `FRESHDESK_API_KEY` environment variable
2. Configure the `DOMAIN` constant for your Freshdesk instance
3. Update product mappings in `setProducts()` method
4. Configure site-wide secret paths in `setSecretMapSiteWide()`

### Running the Analysis

The tool will:
1. Download YAML files from specified tickets
2. Analyze configurations for secrets
3. Generate customer and engineer reports
4. Upload results back to the ticket
5. Send appropriate notifications

## Output Files

- **`{filename}_customer.txt`**: Customer-facing report with actionable items
- **`{filename}_engineer.txt`**: Internal report for support engineers

## Dependencies

- Java 11+
- SnakeYAML for YAML parsing
- JSON Simple for JSON handling
- Joda Time for date handling
- WSL environment for shell command execution

## Configuration

### Product Types
Currently supports analysis for:
- `product1` and `product2` (update `setProducts()` method)

## Notes

- Requires WSL environment for file operations
- Handles nested lists and maps in YAML structures

## Security Considerations

- API keys stored as environment variables
- Sensitive information separated between customer and engineer outputs
- Private notes used for internal communications
