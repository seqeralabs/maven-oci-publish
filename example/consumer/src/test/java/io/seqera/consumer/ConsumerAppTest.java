package io.seqera.consumer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.seqera.shared.SharedLibrary;

/**
 * Tests for ConsumerApp.
 */
public class ConsumerAppTest {
    
    @Test
    public void testConsumerAppCreation() {
        ConsumerApp app = new ConsumerApp();
        assertNotNull(app);
    }
    
    @Test
    public void testDemonstrateUsage() {
        ConsumerApp app = new ConsumerApp();
        // This should not throw an exception
        assertDoesNotThrow(() -> app.demonstrateUsage());
    }
    
    @Test
    public void testSharedLibraryIntegration() {
        // Test that we can use the SharedLibrary from the published OCI artifact
        assertEquals("1.0.0", SharedLibrary.getVersion());
        assertTrue(SharedLibrary.greet("Test").contains("Hello, Test!"));
        assertEquals(8, SharedLibrary.add(3, 5));
        assertTrue(SharedLibrary.getPublishingInfo().contains("Docker Hub"));
    }
}
