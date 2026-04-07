package ms.rohde.modbusproxy.core.app;

import java.time.Instant;

/**
 * Immutable record of a single proxy error, captured for operational visibility.
 *
 * @param timestamp when the error occurred
 * @param category  short label identifying the error type (e.g. UPSTREAM_ERROR)
 * @param source    originating context, typically a client identifier or "upstream"
 * @param message   human-readable error description
 */
public record ErrorEntry(Instant timestamp, String category, String source, String message) {}
