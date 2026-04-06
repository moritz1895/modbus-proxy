package ms.rohde.modbusproxy.adapters.outbound;

import ms.rohde.modbusproxy.adapters.config.ModbusProxyProperties;
import ms.rohde.modbusproxy.core.domain.ModbusTcpFrame;
import ms.rohde.modbusproxy.ports.outbound.UpstreamGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.net.Socket;

/**
 * Manages the single persistent TCP connection to the SolarEdge inverter.
 *
 * <p>{@link #sendAndReceive} is always called from the single {@code RequestDispatcher}
 * processing thread. A separate reconnect thread monitors the connection and re-establishes
 * it with exponential backoff whenever it drops.</p>
 *
 * <p>If {@link #sendAndReceive} is called while no connection is available, it throws
 * {@link IOException} immediately so the waiting client receives a Modbus exception
 * response without delay.</p>
 */
public class UpstreamConnector implements UpstreamGateway, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(UpstreamConnector.class);

    private final String host;
    private final int port;
    private final int socketTimeoutMs;
    private final long reconnectInitialDelayMs;
    private final long reconnectMaxDelayMs;

    /**
     * The active socket. Written only by the reconnect thread and by
     * {@link #sendAndReceive} on error. Read by both threads. Volatile
     * ensures visibility without additional locking.
     */
    private volatile Socket socket;
    private volatile boolean running;
    private Thread reconnectThread;

    public UpstreamConnector(ModbusProxyProperties.UpstreamProperties config) {
        this.host = config.host();
        this.port = config.port();
        this.socketTimeoutMs = (int) config.requestTimeoutMs();
        this.reconnectInitialDelayMs = config.reconnectInitialDelayMs();
        this.reconnectMaxDelayMs = config.reconnectMaxDelayMs();
    }

    // --- UpstreamGateway ---

    /**
     * Sends the request frame and reads the response over the active connection.
     * Marks the connection as broken on any I/O error, triggering a reconnect.
     *
     * @throws IOException if no connection is available or an I/O error occurs
     */
    @Override
    public byte[] sendAndReceive(byte[] requestFrame) throws IOException {
        Socket current = socket;
        if (current == null || current.isClosed()) {
            throw new IOException("Not connected to upstream " + host + ":" + port);
        }
        try {
            current.getOutputStream().write(requestFrame);
            current.getOutputStream().flush();
            return ModbusTcpFrame.readFrame(current.getInputStream());
        } catch (IOException e) {
            invalidateSocket(current);
            throw e;
        }
    }

    @Override
    public boolean isConnected() {
        Socket s = socket;
        return s != null && !s.isClosed();
    }

    // --- SmartLifecycle ---

    @Override
    public void start() {
        running = true;
        reconnectThread = Thread.ofVirtual()
                .name("upstream-reconnect")
                .start(this::reconnectLoop);
        log.info("UpstreamConnector started – connecting to {}:{}", host, port);
    }

    @Override
    public void stop() {
        running = false;
        if (reconnectThread != null) {
            reconnectThread.interrupt();
        }
        closeSocket(socket);
        socket = null;
        log.info("UpstreamConnector stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 100; // Start before RequestDispatcher (200) and TcpServer (300)
    }

    // --- Reconnect loop ---

    private void reconnectLoop() {
        long delay = reconnectInitialDelayMs;

        while (running) {
            if (isConnected()) {
                delay = reconnectInitialDelayMs; // reset backoff while connected
                sleepOrReturn(500);
                continue;
            }

            log.info("Connecting to upstream {}:{}...", host, port);
            try {
                Socket newSocket = new Socket(host, port);
                newSocket.setSoTimeout(socketTimeoutMs);
                newSocket.setTcpNoDelay(true);
                newSocket.setKeepAlive(true);
                socket = newSocket;
                log.info("Connected to upstream {}:{}", host, port);
                delay = reconnectInitialDelayMs;
            } catch (IOException e) {
                log.warn("Failed to connect to {}:{} – retry in {}ms: {}", host, port, delay, e.getMessage());
                if (!sleepOrReturn(delay)) return;
                delay = Math.min(delay * 2, reconnectMaxDelayMs);
            }
        }
    }

    private void invalidateSocket(Socket current) {
        if (socket == current) {
            socket = null;
        }
        closeSocket(current);
    }

    private static void closeSocket(Socket s) {
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException ignored) {}
        }
    }

    /**
     * Sleeps for {@code millis} milliseconds.
     *
     * @return {@code true} if sleep completed normally, {@code false} if interrupted
     */
    private boolean sleepOrReturn(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
