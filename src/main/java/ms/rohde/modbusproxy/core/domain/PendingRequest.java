package ms.rohde.modbusproxy.core.domain;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a single Modbus request waiting to be dispatched to the upstream device.
 *
 * <p>The {@code responseFuture} acts as the return channel: the {@code RequestDispatcher}
 * completes it once the upstream responds, and the inbound adapter reads the result
 * to write back to the client.</p>
 *
 * @param rawFrame        complete Modbus TCP frame (MBAP header + PDU) received from the client
 * @param responseFuture  return channel for the upstream response
 * @param enqueuedAt      timestamp used to detect requests that expired while waiting in queue
 * @param clientId        human-readable client identifier for logging
 */
public record PendingRequest(
        byte[] rawFrame,
        CompletableFuture<byte[]> responseFuture,
        Instant enqueuedAt,
        String clientId
) {

    /**
     * Creates a new pending request stamped with the current time.
     */
    public static PendingRequest of(byte[] rawFrame, String clientId) {
        return new PendingRequest(rawFrame, new CompletableFuture<>(), Instant.now(), clientId);
    }

    /**
     * Completes the request successfully with the upstream response.
     */
    public void completeWith(byte[] responseFrame) {
        responseFuture.complete(responseFrame);
    }

    /**
     * Fails the request so the waiting client adapter receives an exception
     * instead of blocking indefinitely.
     */
    public void failWith(Exception cause) {
        responseFuture.completeExceptionally(cause);
    }
}
