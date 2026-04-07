package ms.rohde.modbusproxy.adapters.health;

import ms.rohde.modbusproxy.core.app.ErrorEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ErrorRegistryTest {

    private ErrorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ErrorRegistry();
    }

    @Test
    void recent_givenNoErrors_thenReturnsEmptyList() {
        assertTrue(registry.recent().isEmpty());
    }

    @Test
    void record_givenSingleError_thenReturnsIt() {
        ErrorEntry entry = new ErrorEntry(Instant.now(), "UPSTREAM_ERROR", "client-1", "Connection refused");

        registry.record(entry);

        List<ErrorEntry> result = registry.recent();
        assertEquals(1, result.size());
        assertSame(entry, result.getFirst());
    }

    @Test
    void record_givenTenErrors_thenReturnsAllTen() {
        for (int i = 0; i < 10; i++) {
            registry.record(new ErrorEntry(Instant.now(), "UPSTREAM_ERROR", "client-" + i, "error " + i));
        }

        assertEquals(10, registry.recent().size());
    }

    @Test
    void record_givenElevenErrors_thenEvictsOldestAndKeepsTen() {
        for (int i = 0; i < 10; i++) {
            registry.record(new ErrorEntry(Instant.now(), "UPSTREAM_ERROR", "client-" + i, "old error " + i));
        }
        ErrorEntry eleventh = new ErrorEntry(Instant.now(), "UPSTREAM_ERROR", "client-10", "new error");

        registry.record(eleventh);

        List<ErrorEntry> result = registry.recent();
        assertEquals(10, result.size());
        assertSame(eleventh, result.getLast());
        assertEquals("old error 1", result.getFirst().message());
    }

    @Test
    void recent_givenErrors_thenReturnsInChronologicalOrder() {
        Instant t1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T10:00:01Z");
        Instant t3 = Instant.parse("2026-01-01T10:00:02Z");

        registry.record(new ErrorEntry(t1, "UPSTREAM_ERROR", "client", "first"));
        registry.record(new ErrorEntry(t2, "QUEUE_FULL", "client", "second"));
        registry.record(new ErrorEntry(t3, "CLIENT_TIMEOUT", "client", "third"));

        List<ErrorEntry> result = registry.recent();
        assertEquals(t1, result.get(0).timestamp());
        assertEquals(t2, result.get(1).timestamp());
        assertEquals(t3, result.get(2).timestamp());
    }
}
