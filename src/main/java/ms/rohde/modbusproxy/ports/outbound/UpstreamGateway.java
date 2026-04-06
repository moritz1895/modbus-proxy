package ms.rohde.modbusproxy.ports.outbound;

import java.io.IOException;

/**
 * Outbound port: sends one Modbus request to the upstream device and returns its response.
 *
 * <p>Implementations are responsible for connection management. This method is always
 * called from a single thread, so no internal synchronization is required.</p>
 */
public interface UpstreamGateway {

    /**
     * Sends a complete Modbus TCP frame to the upstream device and reads the response.
     *
     * @param requestFrame complete Modbus TCP frame to forward
     * @return complete response frame received from the upstream device
     * @throws IOException if the upstream is unreachable or the connection is lost
     */
    byte[] sendAndReceive(byte[] requestFrame) throws IOException;

    /**
     * Returns {@code true} if the upstream device is currently reachable.
     * Used for health reporting only; do not use for request routing decisions.
     */
    boolean isConnected();
}
