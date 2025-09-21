package io.seqera.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SharedLibrary.
 */
public class SharedLibraryTest {
    
    @Test
    public void testGetVersion() {
        String version = SharedLibrary.getVersion();
        assertNotNull(version);
        assertEquals("1.0.0", version);
    }
    
    @Test
    public void testGreet() {
        String greeting = SharedLibrary.greet("World");
        assertNotNull(greeting);
        assertTrue(greeting.contains("Hello, World!"));
        assertTrue(greeting.contains("Maven OCI Publish Plugin"));
    }
    
    @Test
    public void testAdd() {
        assertEquals(5, SharedLibrary.add(2, 3));
        assertEquals(0, SharedLibrary.add(-1, 1));
        assertEquals(-5, SharedLibrary.add(-3, -2));
    }
    
    @Test
    public void testGetPublishingInfo() {
        String info = SharedLibrary.getPublishingInfo();
        assertNotNull(info);
        assertTrue(info.contains("Docker Hub"));
        assertTrue(info.contains("OCI artifact"));
    }
}
