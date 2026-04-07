package ms.rohde.modbusproxy.core.app;

import ms.rohde.modbusproxy.ports.outbound.ErrorLog;
import ms.rohde.modbusproxy.ports.outbound.UpstreamGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestDispatcherTest {

    // FC 03 Read Holding Registers (minimal valid frames)
    private static final byte[] REQUEST = {
            0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00, 0x6B, 0x00, 0x03
    };
    private static final byte[] RESPONSE = {
            0x00, 0x01, 0x00, 0x00, 0x00, 0x09, 0x01, 0x03, 0x06, 0x00, 0x0A, 0x00, 0x0B, 0x00, 0x0C
    };

    @Mock
    private UpstreamGateway upstreamGateway;

    @Mock
    private ErrorLog errorLog;

    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new RequestDispatcher(upstreamGateway, errorLog, 10, 200, 5000);
        dispatcher.start();
    }

    @AfterEach
    void tearDown() {
        dispatcher.stop();
    }

    @Test
    void submit_givenAvailableUpstream_thenFutureCompletesWithResponse() throws Exception {
        when(upstreamGateway.sendAndReceive(REQUEST)).thenReturn(RESPONSE);

        CompletableFuture<byte[]> future = dispatcher.submit(REQUEST, "test-client");

        byte[] result = future.get(2, TimeUnit.SECONDS);
        assertArrayEquals(RESPONSE, result);
    }

    @Test
    void submit_givenUpstreamIOException_thenFutureCompletesExceptionally() throws Exception {
        when(upstreamGateway.sendAndReceive(any()))
                .thenThrow(new IOException("Connection refused"));

        CompletableFuture<byte[]> future = dispatcher.submit(REQUEST, "test-client");

        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> future.get(2, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, ex.getCause());
    }

    @Test
    void submit_givenFullQueue_thenFutureCompletesExceptionallyImmediately() throws Exception {
        int capacity = 2;
        long enqueueTimeoutMs = 50;
        RequestDispatcher smallDispatcher = new RequestDispatcher(upstreamGateway, errorLog, capacity, enqueueTimeoutMs, 5000);
        smallDispatcher.start();

        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch releaseProcessing = new CountDownLatch(1);

        try {
            when(upstreamGateway.sendAndReceive(any())).thenAnswer(inv -> {
                processingStarted.countDown();
                releaseProcessing.await(5, TimeUnit.SECONDS);
                return RESPONSE;
            });

            // First submit: dispatcher picks it up immediately, unblocking the queue
            smallDispatcher.submit(REQUEST, "client-1");
            assertTrue(processingStarted.await(2, TimeUnit.SECONDS),
                    "Dispatcher should have started processing");

            // Fill the queue while dispatcher is blocked
            smallDispatcher.submit(REQUEST, "client-2");
            smallDispatcher.submit(REQUEST, "client-3");

            // This one must fail – queue is full
            CompletableFuture<byte[]> overflow = smallDispatcher.submit(REQUEST, "client-overflow");

            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> overflow.get(1, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
        } finally {
            releaseProcessing.countDown();
            smallDispatcher.stop();
        }
    }

    @Test
    void submit_givenMultipleClients_thenRequestsAreSerialised() throws Exception {
        // All requests should be fulfilled (serial processing guarantees ordering)
        when(upstreamGateway.sendAndReceive(any())).thenReturn(RESPONSE);

        CompletableFuture<byte[]> f1 = dispatcher.submit(REQUEST, "client-1");
        CompletableFuture<byte[]> f2 = dispatcher.submit(REQUEST, "client-2");
        CompletableFuture<byte[]> f3 = dispatcher.submit(REQUEST, "client-3");

        assertArrayEquals(RESPONSE, f1.get(2, TimeUnit.SECONDS));
        assertArrayEquals(RESPONSE, f2.get(2, TimeUnit.SECONDS));
        assertArrayEquals(RESPONSE, f3.get(2, TimeUnit.SECONDS));
    }

    @Test
    void submit_givenUpstreamIOException_thenRecordsUpstreamError() throws Exception {
        when(upstreamGateway.sendAndReceive(any()))
                .thenThrow(new IOException("Connection refused"));

        CompletableFuture<byte[]> future = dispatcher.submit(REQUEST, "test-client");
        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));

        verify(errorLog, timeout(2000)).record(argThat(e ->
                "UPSTREAM_ERROR".equals(e.category()) && "test-client".equals(e.source())));
    }

    @Test
    void queueSize_givenNoRequests_thenReturnsZero() {
        assertEquals(0, dispatcher.queueSize());
    }

    @Test
    void isRunning_afterStart_thenReturnsTrue() {
        assertTrue(dispatcher.isRunning());
    }

    @Test
    void isRunning_afterStop_thenReturnsFalse() {
        dispatcher.stop();
        assertFalse(dispatcher.isRunning());
        // Prevent double-stop in @AfterEach
        dispatcher = new RequestDispatcher(upstreamGateway, errorLog, 10, 200, 5000);
        dispatcher.start();
    }
}
