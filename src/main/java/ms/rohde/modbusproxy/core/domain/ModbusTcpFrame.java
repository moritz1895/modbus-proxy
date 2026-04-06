package ms.rohde.modbusproxy.core.domain;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parses and builds Modbus TCP frames (MBAP header + PDU).
 *
 * <p>Modbus TCP frame structure:</p>
 * <pre>
 * ┌────────────────┬─────────────┬──────────┬─────────┬─────────┐
 * │ Transaction ID │ Protocol ID │  Length  │ Unit ID │   PDU   │
 * │    2 bytes     │   2 bytes   │  2 bytes │ 1 byte  │ N bytes │
 * └────────────────┴─────────────┴──────────┴─────────┴─────────┘
 * </pre>
 *
 * <p>The Length field counts the bytes from Unit ID to end of PDU (inclusive).
 * After reading the 7-byte MBAP header, {@code Length - 1} additional PDU bytes follow.</p>
 *
 * <p>This proxy operates as a transparent Layer-4 proxy: frames are forwarded
 * byte-for-byte without interpreting Modbus semantics.</p>
 */
public final class ModbusTcpFrame {

    /** Fixed length of the MBAP header in bytes. */
    public static final int MBAP_HEADER_LENGTH = 7;

    private static final int LENGTH_FIELD_OFFSET = 4;

    /** Maximum PDU length per Modbus specification. */
    public static final int MAX_PDU_LENGTH = 253;

    private ModbusTcpFrame() {}

    /**
     * Reads a complete Modbus TCP frame from the given stream.
     *
     * <p>Reads the 7-byte MBAP header first, extracts the Length field,
     * then reads the remaining PDU bytes.</p>
     *
     * @param in connection stream
     * @return complete frame as byte array (header + PDU)
     * @throws IOException on connection errors or malformed frames
     */
    public static byte[] readFrame(InputStream in) throws IOException {
        byte[] header = readExactly(in, MBAP_HEADER_LENGTH);

        int lengthField = ((header[LENGTH_FIELD_OFFSET] & 0xFF) << 8)
                        | (header[LENGTH_FIELD_OFFSET + 1] & 0xFF);

        // Length field = Unit ID (1 byte, already in header) + PDU bytes
        // Minimum 2: at least Unit ID + function code
        if (lengthField < 2 || lengthField > MAX_PDU_LENGTH + 1) {
            throw new IOException("Invalid Modbus frame length field: " + lengthField
                    + " (valid range: 2–" + (MAX_PDU_LENGTH + 1) + ")");
        }

        int pduBytes = lengthField - 1; // Unit ID is already in header
        byte[] pdu = readExactly(in, pduBytes);

        byte[] frame = new byte[MBAP_HEADER_LENGTH + pduBytes];
        System.arraycopy(header, 0, frame, 0, MBAP_HEADER_LENGTH);
        System.arraycopy(pdu, 0, frame, MBAP_HEADER_LENGTH, pduBytes);
        return frame;
    }

    /**
     * Extracts the Transaction ID from the first two bytes of the frame.
     *
     * @param frame complete Modbus TCP frame
     * @return transaction ID as unsigned 16-bit integer
     */
    public static int transactionId(byte[] frame) {
        if (frame.length < 2) return -1;
        return ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
    }

    /**
     * Extracts the Unit ID (device address) from byte 6 of the frame.
     *
     * @param frame complete Modbus TCP frame
     * @return unit ID as unsigned 8-bit integer
     */
    public static int unitId(byte[] frame) {
        if (frame.length < MBAP_HEADER_LENGTH) return -1;
        return frame[6] & 0xFF;
    }

    /**
     * Builds a Modbus exception response frame.
     *
     * <p>Used when the proxy itself cannot fulfill a request (queue full, upstream
     * unreachable, etc.). The Transaction ID and Unit ID are mirrored from the request.</p>
     *
     * @param requestFrame   original request frame (for Transaction ID and Unit ID)
     * @param functionCode   original function code from the request
     * @param exceptionCode  Modbus exception code (see {@link ExceptionCode})
     * @return 9-byte exception response frame
     */
    public static byte[] buildExceptionResponse(byte[] requestFrame, byte functionCode, byte exceptionCode) {
        byte[] response = new byte[MBAP_HEADER_LENGTH + 2];

        response[0] = requestFrame[0]; // Transaction ID high
        response[1] = requestFrame[1]; // Transaction ID low
        response[2] = 0x00;            // Protocol ID high
        response[3] = 0x00;            // Protocol ID low
        response[4] = 0x00;            // Length high
        response[5] = 0x03;            // Length low: Unit ID (1) + FC (1) + exception code (1)
        response[6] = requestFrame.length > 6 ? requestFrame[6] : 0x01; // Unit ID
        response[7] = (byte) (functionCode | 0x80); // Exception function code
        response[8] = exceptionCode;

        return response;
    }

    /**
     * Reads exactly {@code length} bytes from the stream, blocking until all bytes arrive.
     *
     * @throws IOException if the connection closes before all bytes are read
     */
    private static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read == -1) {
                throw new IOException(
                        "Connection closed after " + offset + " of " + length + " expected bytes");
            }
            offset += read;
        }
        return buffer;
    }

    /** Standard Modbus exception codes relevant to a proxy. */
    public static final class ExceptionCode {
        /** Server Device Failure – generic internal error. */
        public static final byte DEVICE_FAILURE = 0x04;
        /** Gateway Target Device Failed to Respond – upstream unreachable. */
        public static final byte GATEWAY_TARGET_NO_RESPONSE = 0x0B;

        private ExceptionCode() {}
    }
}
