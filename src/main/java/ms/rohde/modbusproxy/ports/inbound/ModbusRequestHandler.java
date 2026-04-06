package ms.rohde.modbusproxy.ports.inbound;

import java.util.concurrent.CompletableFuture;

/**
 * Inbound port: accepts a Modbus request from a client and returns a future for the response.
 *
 * <p>The future completes normally with the upstream response frame, or exceptionally
 * if the request could not be fulfilled (queue full, upstream error, timeout).</p>
 */
public interface ModbusRequestHandler {

    /**
     * Submits a Modbus request for upstream dispatch.
     *
     * @param requestFrame complete Modbus TCP frame received from the client
     * @param clientId     human-readable identifier of the originating client (for logging)
     * @return future that will be completed with the upstream response frame
     */
    CompletableFuture<byte[]> submit(byte[] requestFrame, String clientId);
}
