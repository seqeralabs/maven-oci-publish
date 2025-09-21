package io.seqera.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.seqera.shared.SharedLibrary;

/**
 * Example application that consumes the shared library published via OCI.
 */
public class ConsumerApp {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsumerApp.class);
    
    public static void main(String[] args) {
        logger.info("Starting OCI Consumer Example App");
        
        try {
            // Use the SharedLibrary from the published OCI artifact
            logger.info("Using SharedLibrary version: {}", SharedLibrary.getVersion());
            
            String greeting = SharedLibrary.greet("OCI Consumer");
            logger.info("Received greeting: {}", greeting);
            
            int result = SharedLibrary.add(10, 20);
            logger.info("Calculation result: 10 + 20 = {}", result);
            
            String info = SharedLibrary.getPublishingInfo();
            logger.info("Publishing info: {}", info);
            
            logger.info("✅ Successfully consumed library from OCI registry!");
            
        } catch (Exception e) {
            logger.error("❌ Failed to use SharedLibrary: {}", e.getMessage());
            logger.error("Make sure to run './gradlew downloadOciArtifacts' first");
            throw e;
        }
    }
    
    /**
     * Demonstrates usage of the shared library.
     */
    public void demonstrateUsage() {
        logger.info("Demonstrating SharedLibrary usage:");
        
        // Test all methods from the shared library
        logger.info("Version: {}", SharedLibrary.getVersion());
        logger.info("Greeting: {}", SharedLibrary.greet("Demo User"));
        logger.info("Math: 5 + 3 = {}", SharedLibrary.add(5, 3));
        logger.info("Info: {}", SharedLibrary.getPublishingInfo());
    }
}
