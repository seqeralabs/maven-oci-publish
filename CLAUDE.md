# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Gradle plugin that enables publishing Maven artifacts to OCI-compliant registries using the ORAS (OCI Registry as Storage) Java SDK. The plugin provides a DSL similar to Gradle's maven-publish plugin but targets OCI registries instead of traditional Maven repositories.

Project inspired to https://github.com/Tosan/oras-maven-plugin.

## Build and Test Commands

```bash
# Build the plugin
./gradlew build

# Run unit tests
./gradlew test

# Run functional tests (integration tests)
./gradlew functionalTest

# Run all tests
./gradlew check

# Run a specific test
./gradlew test --tests "MavenOciPublishPluginTest"
./gradlew functionalTest --tests "MavenOciPublishPluginIntegrationTest"

# Apply plugin to example project
cd example && ./gradlew publishToOciRegistries --dry-run
```

## Architecture

### Core Components

The plugin follows a standard Gradle plugin architecture with these key components:

- **MavenOciPublishPlugin** - Main plugin class that applies the plugin, creates DSL extension, and generates publishing tasks
- **MavenOciPublishingExtension** - Provides the `mavenOci` DSL block for configuration
- **OciPublication** - Domain object representing what to publish (similar to MavenPublication)
- **OciRepository** - Domain object representing where to publish (OCI registry configuration)
- **PublishToOciRepositoryTask** - Task implementation that performs the actual publishing using ORAS Java SDK

### Plugin Flow

1. Plugin applies and creates `mavenOci` DSL extension
2. Users configure publications and repositories in build scripts
3. After project evaluation, plugin creates publishing tasks for each publication-repository combination
4. Tasks use ORAS Java SDK to push Maven artifacts to OCI registries with proper media types

### Key Dependencies

- **ORAS Java SDK** (`land.oras:oras-java-sdk:0.2.15`) - Core OCI registry operations
- **Spock Framework** (`org.spockframework:spock-core:2.2-groovy-3.0`) - Testing framework
- Requires Java 17+ and Gradle 6.0+

## Test Structure

### Unit Tests (`plugin/src/test/groovy/`)
- Basic plugin registration and extension creation
- Uses Spock framework with Gradle's `ProjectBuilder`

### Functional Tests (`plugin/src/functionalTest/groovy/`)
- Integration tests using Gradle TestKit
- Tests task creation, plugin integration, and configuration handling
- Runs against real Gradle builds in temporary directories

### Example Project (`example/`)
- Demonstrates plugin usage patterns
- Shows integration with standard Maven publishing
- Includes examples for GitHub Container Registry and local registry

## Development Notes

- The plugin auto-applies the `maven-publish` plugin and builds upon it
- Task naming follows Gradle conventions: `publish{Publication}To{Repository}Repository`
- Uses Gradle's Provider API for lazy evaluation and configuration
- Integrates with Gradle's software component system (`components.java`)
- Supports various authentication methods including Docker credentials and environment variables
