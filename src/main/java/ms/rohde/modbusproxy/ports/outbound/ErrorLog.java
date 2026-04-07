package ms.rohde.modbusproxy.ports.outbound;

import ms.rohde.modbusproxy.core.app.ErrorEntry;

/**
 * Outbound port for recording proxy errors.
 *
 * <p>Implementations are responsible for persisting or buffering entries
 * so they can be exposed via monitoring endpoints.</p>
 */
public interface ErrorLog {

    /**
     * Records an error entry.
     *
     * @param entry the error to record
     */
    void record(ErrorEntry entry);
}
