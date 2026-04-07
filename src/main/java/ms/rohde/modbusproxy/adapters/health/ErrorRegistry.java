package ms.rohde.modbusproxy.adapters.health;

import ms.rohde.modbusproxy.core.app.ErrorEntry;
import ms.rohde.modbusproxy.ports.outbound.ErrorLog;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;

/**
 * In-memory ring buffer that retains the most recent {@value #MAX_ERRORS} proxy errors.
 *
 * <p>Once the capacity is reached, the oldest entry is evicted to make room for the new one.
 * All operations are thread-safe via synchronised access on {@code this}.</p>
 */
@Component
public class ErrorRegistry implements ErrorLog {

    private static final int MAX_ERRORS = 10;

    private final ArrayDeque<ErrorEntry> errors = new ArrayDeque<>(MAX_ERRORS);

    @Override
    public synchronized void record(ErrorEntry entry) {
        if (errors.size() >= MAX_ERRORS) {
            errors.pollFirst();
        }
        errors.addLast(entry);
    }

    /**
     * Returns a snapshot of recent errors in chronological order (oldest first).
     */
    public synchronized List<ErrorEntry> recent() {
        return List.copyOf(errors);
    }
}
