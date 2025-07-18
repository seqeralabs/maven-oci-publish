package com.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example application demonstrating Maven OCI publishing.
 */
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    
    public static void main(String[] args) {
        logger.info("Hello from Maven OCI Publish example!");
        
        Application app = new Application();
        String result = app.greet("World");
        logger.info(result);
    }
    
    /**
     * Creates a greeting message.
     * @param name the name to greet
     * @return the greeting message
     */
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}