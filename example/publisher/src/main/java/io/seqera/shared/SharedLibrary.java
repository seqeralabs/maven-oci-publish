package io.seqera.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A shared library to demonstrate OCI publishing and consumption.
 */
public class SharedLibrary {
    
    private static final Logger logger = LoggerFactory.getLogger(SharedLibrary.class);
    
    /**
     * Returns the version of this library.
     * @return the version string
     */
    public static String getVersion() {
        return "1.0.0";
    }
    
    /**
     * Returns a greeting message.
     * @param name the name to greet
     * @return a greeting message
     */
    public static String greet(String name) {
        String message = "Hello, " + name + "! This library was published via Maven OCI Publish Plugin.";
        logger.info("Generated greeting: {}", message);
        return message;
    }
    
    /**
     * Performs a simple calculation.
     * @param a first number
     * @param b second number
     * @return the sum of a and b
     */
    public static int add(int a, int b) {
        int result = a + b;
        logger.debug("Adding {} + {} = {}", a, b, result);
        return result;
    }
    
    /**
     * Returns information about the publishing mechanism.
     * @return publishing info
     */
    public static String getPublishingInfo() {
        return "Published to Docker Hub (registry-1.docker.io) as OCI artifact";
    }
}
