package ms.rohde.modbusproxy.core.app;

import ms.rohde.modbusproxy.core.domain.ModbusTcpFrame;
import ms.rohde.modbusproxy.core.domain.PendingRequest;
import ms.rohde.modbusproxy.ports.inbound.ModbusRequestHandler;
import ms.rohde.modbusproxy.ports.outbound.UpstreamGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Serializes Modbus requests from multiple clients into a single upstream connection.
 *
 * <p>This is the core of the proxy. A bounded queue accepts requests from all
 * connected clients. A single processing thread dequeues them one at a time
 * and forwards each to the {@link UpstreamGateway}. This guarantees that the
 * SolarEdge inverter never receives concurrent requests.</p>
 *
 * <p>This class has no dependency on any framework. Lifecycle management
 * ({@code start}/{@code stop}) is delegated to the infrastructure layer.</p>
 */
public class RequestDispatcher implements ModbusRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestDispatcher.class);

    private final UpstreamGateway upstreamGateway;
    private final LinkedBlockingQueue<PendingRequest> queue;
    private final long enqueueTimeoutMs;
    private final long requestTimeoutMs;

    private volatile boolean running;
    private Thread processingThread;

    public RequestDispatcher(UpstreamGateway upstreamGateway,
                             int queueCapacity,
                             long enqueueTimeoutMs,
                             long requestTimeoutMs) {
        this.upstreamGateway = upstreamGateway;
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.enqueueTimeoutMs = enqueueTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /**
     * Enqueues the request and returns a future that will be completed
     * with the upstream response. If the queue is full, the future is
     * completed exceptionally immediately.
     */
    @Override
    public CompletableFuture<byte[]> submit(byte[] requestFrame, String clientId) {
        PendingRequest pending = PendingRequest.of(requestFrame, clientId);
        try {
            boolean accepted = queue.offer(pending, enqueueTimeoutMs, TimeUnit.MILLISECONDS);
            if (!accepted) {
                log.warn("Queue full – rejecting request from '{}'", clientId);
                pending.failWith(new IllegalStateException("Modbus request queue is full"));
            } else {
                log.debug("Request enqueued (client='{}', queueSize={})", clientId, queue.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.failWith(new IllegalStateException("Interrupted while waiting for queue slot"));
        }
        return pending.responseFuture();
    }

    /** Starts the processing thread. Must be called before any requests are submitted. */
    public void start() {
        running = true;
        processingThread = Thread.ofVirtual()
                .name("modbus-dispatcher")
                .start(this::processLoop);
        log.info("RequestDispatcher started (queueCapacity={}, enqueueTimeoutMs={}, requestTimeoutMs={})",
                queue.remainingCapacity() + queue.size(), enqueueTimeoutMs, requestTimeoutMs);
    }

    /** Stops the processing thread and fails all pending requests in the queue. */
    public void stop() {
        running = false;
        if (processingThread != null) {
            processingThread.interrupt();
        }
        drainQueue(new IllegalStateException("Proxy is shutting down"));
        log.info("RequestDispatcher stopped");
    }

    public boolean isRunning() {
        return running;
    }

    /** Returns the current number of requests waiting in the queue. */
    public int queueSize() {
        return queue.size();
    }

    private void processLoop() {
        while (running) {
            try {
                PendingRequest pending = queue.take();
                processRequest(pending);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.debug("Processing loop ended");
    }

    private void processRequest(PendingRequest pending) {
        long ageMs = Instant.now().toEpochMilli() - pending.enqueuedAt().toEpochMilli();
        if (ageMs > requestTimeoutMs) {
            log.warn("Request from '{}' expired in queue after {}ms – discarding", pending.clientId(), ageMs);
            pending.failWith(new TimeoutException("Request expired after " + ageMs + "ms in queue"));
            return;
        }

        log.debug("Forwarding request (client='{}', txId={}, bytes={})",
                pending.clientId(),
                ModbusTcpFrame.transactionId(pending.rawFrame()),
                pending.rawFrame().length);

        try {
            byte[] response = upstreamGateway.sendAndReceive(pending.rawFrame());
            pending.completeWith(response);
            log.debug("Response received (client='{}', txId={}, bytes={})",
                    pending.clientId(),
                    ModbusTcpFrame.transactionId(response),
                    response.length);
        } catch (IOException e) {
            log.warn("Upstream error for client '{}': {}", pending.clientId(), e.getMessage());
            pending.failWith(e);
        }
    }

    private void drainQueue(Exception cause) {
        PendingRequest pending;
        int count = 0;
        while ((pending = queue.poll()) != null) {
            pending.failWith(cause);
            count++;
        }
        if (count > 0) {
            log.warn("Drained {} pending requests from queue on shutdown", count);
        }
    }
}
