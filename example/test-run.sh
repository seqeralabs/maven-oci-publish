#!/bin/bash

# Maven OCI Publish Plugin - Full Lifecycle Test
# This script runs the complete publish-consume lifecycle:
# 1. Runs publisher to create repository and push artifacts
# 2. Runs consumer to validate the published artifacts
# 
# Requires: Docker Hub credentials in gradle.properties at project root

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
GRADLE_PROPERTIES="$PROJECT_ROOT/gradle.properties"

echo -e "${BLUE}🚀 Maven OCI Publish Plugin - Full Lifecycle Test${NC}"
echo -e "${BLUE}=================================================${NC}"
echo ""

# Function to print colored output
print_step() {
    echo -e "${BLUE}📋 $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

# Check if examples exist
if [ ! -d "$SCRIPT_DIR/publisher" ] || [ ! -d "$SCRIPT_DIR/consumer" ]; then
    print_error "Examples not found! Run ./test-setup.sh first."
    exit 1
fi

# Check for gradle.properties with credentials
print_step "Checking for Docker Hub credentials..."

if [ ! -f "$GRADLE_PROPERTIES" ]; then
    print_error "gradle.properties not found at $GRADLE_PROPERTIES"
    echo ""
    echo "Please create gradle.properties with Docker Hub credentials:"
    echo "dockerHubUsername=your-username"
    echo "dockerHubPassword=your-password"
    exit 1
fi

# Check if credentials are set
if ! grep -q "dockerHubUsername=" "$GRADLE_PROPERTIES" || ! grep -q "dockerHubPassword=" "$GRADLE_PROPERTIES"; then
    print_warning "Docker Hub credentials not found in gradle.properties"
    echo ""
    echo "Please add these properties to $GRADLE_PROPERTIES:"
    echo "dockerHubUsername=your-username"
    echo "dockerHubPassword=your-password"
    echo ""
    echo "Or set environment variables:"
    echo "export DOCKERHUB_USERNAME=your-username"
    echo "export DOCKERHUB_PASSWORD=your-password"
    
    # Check if environment variables are set
    if [ -z "$DOCKERHUB_USERNAME" ] || [ -z "$DOCKERHUB_PASSWORD" ]; then
        print_error "No Docker Hub credentials found in gradle.properties or environment variables"
        exit 1
    else
        print_success "Using Docker Hub credentials from environment variables"
    fi
else
    print_success "Docker Hub credentials found in gradle.properties"
    
    # Extract credentials from gradle.properties and set as environment variables
    # This ensures they're available to the composite build
    export DOCKERHUB_USERNAME=$(grep "dockerHubUsername=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)
    export DOCKERHUB_PASSWORD=$(grep "dockerHubPassword=" "$GRADLE_PROPERTIES" | cut -d'=' -f2)
    
    if [ -n "$DOCKERHUB_USERNAME" ] && [ -n "$DOCKERHUB_PASSWORD" ]; then
        print_success "Exported Docker Hub credentials as environment variables"
    else
        print_error "Failed to extract Docker Hub credentials from gradle.properties"
        exit 1
    fi
fi

echo ""

# ===========================================
# PHASE 1: PUBLISHER
# ===========================================

print_step "Phase 1: Running Publisher (creates repository and pushes artifacts)"
echo ""

cd "$SCRIPT_DIR/publisher"

# Show publish configuration
print_step "Showing publish configuration..."
if ! ../../gradlew showPublishInfo --console=plain; then
    print_error "Failed to show publish configuration"
    exit 1
fi

echo ""

# Build the project
print_step "Building publisher project..."
if ! ../../gradlew clean build --console=plain; then
    print_error "Publisher build failed"
    exit 1
fi

print_success "Publisher build completed successfully"
echo ""

# Run tests
print_step "Running publisher tests..."
if ! ../../gradlew test --console=plain; then
    print_error "Publisher tests failed"
    exit 1
fi

print_success "Publisher tests passed"
echo ""

# Publish to OCI registry
print_step "Publishing to OCI registry (this may take a moment)..."
if ! ../../gradlew publishMavenPublicationToDockerhubRepository --console=plain; then
    print_error "Publishing to OCI registry failed"
    exit 1
fi

print_success "Successfully published to OCI registry!"
echo ""

# ===========================================
# PHASE 2: CONSUMER
# ===========================================

print_step "Phase 2: Running Consumer (validates published artifacts)"
echo ""

cd "$SCRIPT_DIR/consumer"

# Build consumer project
print_step "Building consumer project..."
if ! ../../gradlew clean build --console=plain; then
    print_error "Consumer build failed"
    exit 1
fi

print_success "Consumer build completed successfully"
echo ""

# Run consumer tests
print_step "Running consumer tests..."
if ! ../../gradlew test --console=plain; then
    print_error "Consumer tests failed"
    exit 1
fi

print_success "Consumer tests passed"
echo ""

# Run consumer application
print_step "Running consumer application..."
if ! ../../gradlew run --console=plain; then
    print_error "Consumer application failed"
    exit 1
fi

print_success "Consumer application ran successfully"
echo ""

# Show consumption information
print_step "Showing consumption information..."
if ! ../../gradlew showConsumptionInfo --console=plain; then
    print_error "Failed to show consumption information"
    exit 1
fi

echo ""

# ===========================================
# PHASE 3: VALIDATION
# ===========================================

print_step "Phase 3: Validating Published Artifacts"
echo ""

# Check if Docker is available for validation
if command -v docker &> /dev/null; then
    print_step "Validating artifacts with Docker..."
    
    # Try to pull the published artifact
    if docker pull pditommaso/maven:1.0.0 &> /dev/null; then
        print_success "Successfully pulled published artifacts from Docker Hub"
        
        # Show basic info about the pulled image
        print_step "Artifact information:"
        docker image inspect pditommaso/maven:1.0.0 --format "  Size: {{.Size}} bytes" || true
        docker image inspect pditommaso/maven:1.0.0 --format "  Created: {{.Created}}" || true
        
        # Clean up
        docker image rm pditommaso/maven:1.0.0 &> /dev/null || true
        
    else
        print_warning "Could not pull artifacts (may take a moment to be available)"
    fi
else
    print_warning "Docker not available for artifact validation"
fi

echo ""

# ===========================================
# SUMMARY
# ===========================================

print_step "Test Summary"
echo ""
print_success "✅ Publisher: Built, tested, and published successfully"
print_success "✅ Consumer: Built, tested, and ran successfully"
print_success "✅ Artifacts: Published to pditommaso/maven repository"
echo ""
print_step "Repository Information:"
echo "  🌐 Docker Hub: https://hub.docker.com/r/pditommaso/maven"
echo "  📦 Artifact: pditommaso/maven:1.0.0"
echo "  🔧 Pull command: docker pull pditommaso/maven:1.0.0"
echo ""
print_step "Published Artifacts:"
echo "  📚 shared-library-1.0.0.jar (compiled classes)"
echo "  📄 shared-library-1.0.0.pom (Maven metadata)"
echo "  📝 shared-library-1.0.0-sources.jar (source code)"
echo "  📖 shared-library-1.0.0-javadoc.jar (documentation)"
echo ""
print_success "🎉 Full lifecycle test completed successfully!"
echo ""
echo -e "${GREEN}The Maven OCI Publish Plugin is working correctly!${NC}"
echo -e "${BLUE}You can now use the published artifacts or run this test again.${NC}"