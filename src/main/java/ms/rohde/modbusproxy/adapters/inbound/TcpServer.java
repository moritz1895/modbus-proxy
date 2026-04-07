package ms.rohde.modbusproxy.adapters.inbound;

import ms.rohde.modbusproxy.adapters.config.ModbusProxyProperties;
import ms.rohde.modbusproxy.ports.inbound.ModbusRequestHandler;
import ms.rohde.modbusproxy.ports.outbound.ErrorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accepts incoming TCP connections and spawns a {@link ClientSession} per connection.
 *
 * <p>Each session runs in its own virtual thread (cheap, no pool required). A configurable
 * client limit prevents unbounded resource consumption. The accept loop runs in a
 * dedicated virtual thread and exits cleanly when the {@link ServerSocket} is closed.</p>
 */
public class TcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final int port;
    private final int maxClients;
    private final long requestTimeoutMs;
    private final ModbusRequestHandler requestHandler;
    private final ErrorLog errorLog;

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    private final AtomicInteger activeClients = new AtomicInteger();
    private final AtomicInteger totalClients = new AtomicInteger();

    public TcpServer(ModbusProxyProperties.ProxyProperties proxyConfig,
                     ModbusProxyProperties.UpstreamProperties upstreamConfig,
                     ModbusRequestHandler requestHandler,
                     ErrorLog errorLog) {
        this.port = proxyConfig.port();
        this.maxClients = proxyConfig.maxClients();
        this.requestTimeoutMs = upstreamConfig.requestTimeoutMs();
        this.requestHandler = requestHandler;
        this.errorLog = errorLog;
    }

    @Override
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            running = true;
            acceptThread = Thread.ofVirtual()
                    .name("tcp-accept")
                    .start(this::acceptLoop);
            log.info("TCP server listening on port {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start TCP server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        log.info("Stopping TCP server...");
        running = false;
        try {
            if (serverSocket != null) serverSocket.close(); // interrupts accept()
        } catch (IOException e) {
            log.warn("Error closing server socket: {}", e.getMessage());
        }
        if (acceptThread != null) acceptThread.interrupt();
        log.info("TCP server stopped ({} clients still active)", activeClients.get());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 300; // Start last (after UpstreamConnector=100, RequestDispatcher=200)
    }

    /** Returns the number of currently active client sessions. */
    public int activeClientCount() {
        return activeClients.get();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                onNewConnection(client);
            } catch (IOException e) {
                if (running) log.error("Accept error: {}", e.getMessage());
            }
        }
    }

    private void onNewConnection(Socket client) {
        if (activeClients.get() >= maxClients) {
            log.warn("Max clients ({}) reached – rejecting connection from {}",
                    maxClients, client.getRemoteSocketAddress());
            closeQuietly(client);
            return;
        }

        int clientNumber = totalClients.incrementAndGet();
        String clientId = "client-" + clientNumber + "@" + client.getRemoteSocketAddress();
        activeClients.incrementAndGet();

        ClientSession session = new ClientSession(client, clientId, requestHandler, errorLog, requestTimeoutMs);

        Thread.ofVirtual()
                .name("client-session-" + clientNumber)
                .start(() -> {
                    try {
                        session.run();
                    } finally {
                        activeClients.decrementAndGet();
                    }
                });

        log.info("Client accepted: {} (active: {})", clientId, activeClients.get());
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}
