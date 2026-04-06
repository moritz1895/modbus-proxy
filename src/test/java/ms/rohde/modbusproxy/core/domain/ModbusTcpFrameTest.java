package ms.rohde.modbusproxy.core.domain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModbusTcpFrameTest {

    // FC 03 Read Holding Registers: txId=0x0001, protocol=0x0000, length=0x0006, unit=0x01, fc=0x03, addr=0x006B, count=0x0003
    private static final byte[] VALID_READ_REQUEST = {
            0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00, 0x6B, 0x00, 0x03
    };

    // FC 03 Response: txId=0x0001, length=0x0009, unit=0x01, fc=0x03, byteCount=0x06, data=3 registers
    private static final byte[] VALID_READ_RESPONSE = {
            0x00, 0x01, 0x00, 0x00, 0x00, 0x09, 0x01, 0x03, 0x06, 0x00, 0x0A, 0x00, 0x0B, 0x00, 0x0C
    };

    @Test
    void readFrame_givenValidReadRequest_thenReturnsCompleteFrame() throws IOException {
        InputStream in = new ByteArrayInputStream(VALID_READ_REQUEST);

        byte[] result = ModbusTcpFrame.readFrame(in);

        assertArrayEquals(VALID_READ_REQUEST, result);
    }

    @Test
    void readFrame_givenValidReadResponse_thenReturnsCompleteFrame() throws IOException {
        InputStream in = new ByteArrayInputStream(VALID_READ_RESPONSE);

        byte[] result = ModbusTcpFrame.readFrame(in);

        assertArrayEquals(VALID_READ_RESPONSE, result);
    }

    @Test
    void readFrame_givenEmptyStream_thenThrowsIOException() {
        InputStream in = new ByteArrayInputStream(new byte[0]);

        assertThrows(IOException.class, () -> ModbusTcpFrame.readFrame(in));
    }

    @Test
    void readFrame_givenPartialHeader_thenThrowsIOException() {
        byte[] partial = {0x00, 0x01, 0x00, 0x00}; // only 4 of 7 header bytes
        InputStream in = new ByteArrayInputStream(partial);

        assertThrows(IOException.class, () -> ModbusTcpFrame.readFrame(in));
    }

    @Test
    void readFrame_givenHeaderWithTruncatedPayload_thenThrowsIOException() {
        // Header says length=6 (5 PDU bytes) but only 2 PDU bytes follow
        byte[] truncated = {0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00};
        InputStream in = new ByteArrayInputStream(truncated);

        assertThrows(IOException.class, () -> ModbusTcpFrame.readFrame(in));
    }

    @Test
    void readFrame_givenLengthFieldZero_thenThrowsIOException() {
        byte[] frame = {0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x01};
        InputStream in = new ByteArrayInputStream(frame);

        assertThrows(IOException.class, () -> ModbusTcpFrame.readFrame(in));
    }

    @Test
    void readFrame_givenLengthFieldOne_thenThrowsIOException() {
        // Length=1 means only Unit ID, no function code – invalid for Modbus
        byte[] frame = {0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x01};
        InputStream in = new ByteArrayInputStream(frame);

        assertThrows(IOException.class, () -> ModbusTcpFrame.readFrame(in));
    }

    @Test
    void readFrame_givenExcessiveLength_thenThrowsIOException() {
        // Length field value 0xFF = 255 > MAX_PDU_LENGTH + 1 = 254
        byte[] frame = {0x00, 0x01, 0x00, 0x00, 0x00, (byte) 0xFF, 0x01};
        InputStream in = new ByteArrayInputStream(frame);

        assertThrows(IOException.class, () -> ModbusTcpFrame.readFrame(in));
    }

    @Test
    void transactionId_givenValidFrame_thenReturnsCorrectId() {
        byte[] frame = {0x01, 0x23, 0x00, 0x00, 0x00, 0x06, 0x01, 0x03, 0x00, 0x00, 0x00, 0x01};

        assertEquals(0x0123, ModbusTcpFrame.transactionId(frame));
    }

    @Test
    void transactionId_givenFrameTooShort_thenReturnsMinusOne() {
        assertEquals(-1, ModbusTcpFrame.transactionId(new byte[]{0x01}));
    }

    @Test
    void unitId_givenValidFrame_thenReturnsCorrectId() {
        byte[] frame = {0x00, 0x01, 0x00, 0x00, 0x00, 0x06, 0x02, 0x03, 0x00, 0x00, 0x00, 0x01};

        assertEquals(2, ModbusTcpFrame.unitId(frame));
    }

    @Test
    void unitId_givenFrameTooShort_thenReturnsMinusOne() {
        assertEquals(-1, ModbusTcpFrame.unitId(new byte[]{0x00, 0x01, 0x00, 0x00}));
    }

    @Test
    void buildExceptionResponse_givenReadRequest_thenReturnsWellFormedExceptionFrame() {
        byte functionCode = 0x03;
        byte exceptionCode = ModbusTcpFrame.ExceptionCode.GATEWAY_TARGET_NO_RESPONSE;

        byte[] exception = ModbusTcpFrame.buildExceptionResponse(VALID_READ_REQUEST, functionCode, exceptionCode);

        assertEquals(9, exception.length);
        assertEquals(0x00, exception[0]); // Transaction ID high (mirrored)
        assertEquals(0x01, exception[1]); // Transaction ID low (mirrored)
        assertEquals(0x00, exception[2]); // Protocol ID high
        assertEquals(0x00, exception[3]); // Protocol ID low
        assertEquals(0x00, exception[4]); // Length high
        assertEquals(0x03, exception[5]); // Length low = 3 (Unit + FC + ExCode)
        assertEquals(0x01, exception[6]); // Unit ID (mirrored)
        assertEquals((byte) 0x83, exception[7]); // FC 0x03 | 0x80
        assertEquals(exceptionCode, exception[8]);
    }

    @Test
    void buildExceptionResponse_givenWriteRequest_thenExceptionFunctionCodeIsCorrect() {
        byte[] writeRequest = {0x00, 0x02, 0x00, 0x00, 0x00, 0x06, 0x01, 0x10, 0x00, 0x00, 0x00, 0x01};
        byte functionCode = 0x10; // FC 16 Write Multiple Registers

        byte[] exception = ModbusTcpFrame.buildExceptionResponse(
                writeRequest, functionCode, ModbusTcpFrame.ExceptionCode.DEVICE_FAILURE);

        assertEquals((byte) 0x90, exception[7]); // 0x10 | 0x80
        assertEquals(ModbusTcpFrame.ExceptionCode.DEVICE_FAILURE, exception[8]);
    }
}
