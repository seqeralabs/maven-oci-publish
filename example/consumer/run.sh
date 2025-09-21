#!/bin/bash

# Set 15-second HTTP timeout for faster failure detection
export GRADLE_OPTS="-Djdk.httpclient.keepalive.timeout=15 -Dorg.gradle.internal.http.connectionTimeout=15000 -Dorg.gradle.internal.http.socketTimeout=15000"

../../gradlew test
