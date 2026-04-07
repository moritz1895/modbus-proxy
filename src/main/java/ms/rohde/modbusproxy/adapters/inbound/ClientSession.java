package ms.rohde.modbusproxy.adapters.inbound;

import ms.rohde.modbusproxy.core.app.ErrorEntry;
import ms.rohde.modbusproxy.core.domain.ModbusTcpFrame;
import ms.rohde.modbusproxy.ports.inbound.ModbusRequestHandler;
import ms.rohde.modbusproxy.ports.outbound.ErrorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handles a single client connection for its entire lifetime.
 *
 * <p>Runs in a virtual thread (started by {@link TcpServer}). Reads Modbus request frames
 * in a loop, submits each to the {@link ModbusRequestHandler}, and writes the response
 * back to the client. On any error or client disconnect the session ends cleanly.</p>
 */
class ClientSession implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);

    private final Socket clientSocket;
    private final String clientId;
    private final ModbusRequestHandler requestHandler;
    private final ErrorLog errorLog;
    private final long requestTimeoutMs;

    ClientSession(Socket clientSocket,
                  String clientId,
                  ModbusRequestHandler requestHandler,
                  ErrorLog errorLog,
                  long requestTimeoutMs) {
        this.clientSocket = clientSocket;
        this.clientId = clientId;
        this.requestHandler = requestHandler;
        this.errorLog = errorLog;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    @Override
    public void run() {
        log.info("Client connected: {}", clientId);
        try (clientSocket) {
            InputStream in = clientSocket.getInputStream();
            OutputStream out = clientSocket.getOutputStream();
            while (!Thread.currentThread().isInterrupted() && !clientSocket.isClosed()) {
                processOneRequest(in, out);
            }
        } catch (IOException e) {
            log.info("Client disconnected: {} ({})", clientId, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Client session interrupted (shutdown): {}", clientId);
        }
        log.info("Session ended: {}", clientId);
    }

    private void processOneRequest(InputStream in, OutputStream out)
            throws IOException, InterruptedException {

        byte[] requestFrame = ModbusTcpFrame.readFrame(in);

        log.debug("Request received (client='{}', txId={}, unitId={})",
                clientId,
                ModbusTcpFrame.transactionId(requestFrame),
                ModbusTcpFrame.unitId(requestFrame));

        CompletableFuture<byte[]> responseFuture = requestHandler.submit(requestFrame, clientId);

        byte[] responseFrame;
        try {
            responseFrame = responseFuture.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Timeout waiting for upstream response (client='{}', txId={})",
                    clientId, ModbusTcpFrame.transactionId(requestFrame));
            errorLog.record(new ErrorEntry(Instant.now(), "CLIENT_TIMEOUT", clientId,
                    "Timeout waiting for upstream response (txId=" + ModbusTcpFrame.transactionId(requestFrame) + ")"));
            responseFuture.cancel(false);
            sendExceptionResponse(out, requestFrame);
            return;
        } catch (ExecutionException e) {
            log.warn("Request failed for client '{}' (txId={}): {}",
                    clientId, ModbusTcpFrame.transactionId(requestFrame), e.getCause().getMessage());
            errorLog.record(new ErrorEntry(Instant.now(), "REQUEST_FAILED", clientId, e.getCause().getMessage()));
            sendExceptionResponse(out, requestFrame);
            return;
        }

        out.write(responseFrame);
        out.flush();

        log.debug("Response sent (client='{}', txId={}, bytes={})",
                clientId,
                ModbusTcpFrame.transactionId(responseFrame),
                responseFrame.length);
    }

    private void sendExceptionResponse(OutputStream out, byte[] requestFrame) throws IOException {
        byte functionCode = requestFrame.length > 7 ? requestFrame[7] : 0x01;
        byte[] exception = ModbusTcpFrame.buildExceptionResponse(
                requestFrame, functionCode, ModbusTcpFrame.ExceptionCode.GATEWAY_TARGET_NO_RESPONSE);
        out.write(exception);
        out.flush();
    }
}
