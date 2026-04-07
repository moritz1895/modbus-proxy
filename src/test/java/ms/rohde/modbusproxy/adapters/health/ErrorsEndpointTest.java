package ms.rohde.modbusproxy.adapters.health;

import ms.rohde.modbusproxy.core.app.ErrorEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErrorsEndpointTest {

    @Test
    void errors_givenEmptyRegistry_thenReturnsEmptyList() {
        ErrorRegistry registry = mock(ErrorRegistry.class);
        when(registry.recent()).thenReturn(List.of());
        ErrorsEndpoint endpoint = new ErrorsEndpoint(registry);

        ErrorsEndpoint.ErrorsDescriptor result = endpoint.errors();

        assertTrue(result.errors().isEmpty());
    }

    @Test
    void errors_givenRegistryWithEntries_thenReturnsAllEntries() {
        ErrorRegistry registry = mock(ErrorRegistry.class);
        List<ErrorEntry> entries = List.of(
                new ErrorEntry(Instant.now(), "UPSTREAM_ERROR", "client-1", "Connection lost"),
                new ErrorEntry(Instant.now(), "QUEUE_FULL", "client-2", "Queue is full")
        );
        when(registry.recent()).thenReturn(entries);
        ErrorsEndpoint endpoint = new ErrorsEndpoint(registry);

        ErrorsEndpoint.ErrorsDescriptor result = endpoint.errors();

        assertEquals(2, result.errors().size());
        assertEquals("UPSTREAM_ERROR", result.errors().get(0).category());
        assertEquals("QUEUE_FULL", result.errors().get(1).category());
    }
}
