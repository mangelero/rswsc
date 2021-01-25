package se.divdev.rswsc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

class WebSocketFrame {

    private static final Random RANDOM = new Random();

    private final byte[] headerBytes = new byte[2];
    private final byte[] lengthBytes;
    private final byte[] mask;
    private final byte[] payload;

    private final AtomicInteger index = new AtomicInteger(0);

    // 5.2.  Base Framing Protocol
    //
    //   This wire format for the data transfer part is described by the ABNF
    //   [RFC5234] given in detail in this section.  (Note that, unlike in
    //   other sections of this document, the ABNF in this section is
    //   operating on groups of bits.  The length of each group of bits is
    //   indicated in a comment.  When encoded on the wire, the most
    //   significant bit is the leftmost in the ABNF).  A high-level overview
    //   of the framing is given in the following figure.  In a case of
    //   conflict between the figure below and the ABNF specified later in
    //   this section, the figure is authoritative.
    //
    //      0                   1                   2                   3
    //      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    //     +-+-+-+-+-------+-+-------------+-------------------------------+
    //     |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
    //     |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
    //     |N|V|V|V|       |S|             |   (if payload len==126/127)   |
    //     | |1|2|3|       |K|             |                               |
    //     +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
    //     |     Extended payload length continued, if payload len == 127  |
    //     + - - - - - - - - - - - - - - - +-------------------------------+
    //     |                               |Masking-key, if MASK set to 1  |
    //     +-------------------------------+-------------------------------+
    //     | Masking-key (continued)       |          Payload Data         |
    //     +-------------------------------- - - - - - - - - - - - - - - - +
    //     :                     Payload Data continued ...                :
    //     + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
    //     |                     Payload Data continued ...                |
    //     +---------------------------------------------------------------+
    //
    //   FIN:  1 bit
    //
    //      Indicates that this is the final fragment in a message.  The first
    //      fragment MAY also be the final fragment.
    //
    //   RSV1, RSV2, RSV3:  1 bit each
    //
    //      MUST be 0 unless an extension is negotiated that defines meanings
    //      for non-zero values.  If a nonzero value is received and none of
    //      the negotiated extensions defines the meaning of such a nonzero
    //      value, the receiving endpoint MUST _Fail the WebSocket
    //      Connection_.

    private WebSocketFrame(final byte[] frameData) throws IOException {
        if (frameData.length < 2) {
            throw new IOException("Expected at least 2 bytes of FrameInfo");
        }
        System.arraycopy(frameData, index.getAndAdd(this.headerBytes.length), this.headerBytes, 0, this.headerBytes.length);

        this.lengthBytes = new byte[numberOfLengthBytes()];
        if (this.lengthBytes.length > 0) {
            System.arraycopy(frameData, index.getAndAdd(this.lengthBytes.length), this.lengthBytes, 0, this.lengthBytes.length);
        }

        this.mask = new byte[isMasked() ? 4 : 0];
        if (this.mask.length > 0) {
            System.arraycopy(frameData, index.getAndAdd(this.mask.length), this.mask, 0, this.mask.length);
        }

        byte[] maskedPayload = new byte[frameData.length - index.get()];
        System.arraycopy(frameData, index.get(), maskedPayload, 0, maskedPayload.length);

        this.payload = process(maskedPayload, 0);
        if (getOpCode() == OpCode.UNKNOWN) {
            throw new IllegalArgumentException("Unknown OpCode: " + String.format("0x%02X", getOpCodeValue()));
        }
    }

     static WebSocketFrame incoming(final byte[] frameData) throws IOException {
        return new WebSocketFrame(frameData);
    }

     static WebSocketFrame outgoing(final OpCode opCode) throws IOException {
        byte[] initial = new byte[6];
        Arrays.fill(initial, (byte) 0x0);

        // Enable masking
        initial[1] = BitUtils.enableBit(initial[1], 7);
        byte[] mask = new byte[4];
        RANDOM.nextBytes(mask);
        System.arraycopy(mask, 0, initial, 2, mask.length);

        boolean[] opCodeBytes = BitUtils.decode(opCode.value);
        for (int i = 0; i < 4; i++) {
            initial[0] = BitUtils.setBit(initial[0], i, opCodeBytes[i]);
        }

        return new WebSocketFrame(initial);
    }

    private byte getOpCodeValue() {
        return BitUtils.extract(headerBytes[0], 4, 4);
    }

     OpCode getOpCode() {
        return OpCode.fromValue(getOpCodeValue());
    }

     long payloadSize() {
        int length = getLength();
        if (length < 126) {
            return length;
        }
        return calculateLengthToRead(this.lengthBytes);
    }

    /**
     * Calculate the number of bytes to read to fully read the body
     *
     * @return number of bytes
     */
     long additionalBytesToRead() {
        return payloadSize() - this.payload.length;
    }

    private int numberOfLengthBytes() {
        int length = getLength();
        if (length >= 126) {
            return length == 126 ? 2 : 8;
        }
        return 0;
    }

    private long calculateLengthToRead(byte[] lengthBytes) {
        long length = 0;
        for (int i = 0; i < lengthBytes.length; i++) {
            length = (length << 8) + (lengthBytes[i] & 0xFF);
        }
        return length;
    }

    private byte[] lengthInBytes(final long length, final int numberOfBytes) {
        if (numberOfBytes == 0) {
            return new byte[0];
        }
        byte[] result = new byte[numberOfBytes];
        for (int i = 0; i < numberOfBytes; i++) {
            result[i] = (byte) (length >> ((numberOfBytes - i - 1) * 8));
        }
        return result;
    }

     boolean isMasked() {
        return BitUtils.getBit(headerBytes[1], 7);
    }

    private int getLength() {
        return headerBytes[1] & 127;
    }

    boolean isFinalFrame() {
        return BitUtils.getBit(headerBytes[0], 7);
    }

     boolean isConnectionClose() {
        return getOpCode() == OpCode.CONNECTION_CLOSE;
    }

    boolean isPingFrame() {
        return getOpCode() == OpCode.PING;
    }

    public boolean isPongFrame() {
        return getOpCode() == OpCode.PONG;
    }

    byte[] build(final boolean finalFrame, final byte... payload) throws IOException {

        byte[] headerBytes = Arrays.copyOf(this.headerBytes, this.headerBytes.length);

        headerBytes[0] = BitUtils.setBit(headerBytes[0], 7, finalFrame);

        int length = payload.length;
        int numberOfLengthBytes = length < 126 ? 0 : length > 0xFFFF ? 8 : 2;
        byte[] lengthBytes = lengthInBytes(length, numberOfLengthBytes);

        // If length is > 2^16 then set to 127, > 125 then set it to 126, otherwise length
        int singleLengthByte = lengthBytes.length == 2 ? 126 : lengthBytes.length == 8 ? 127 : length;
        boolean[] bits = BitUtils.decode((byte) singleLengthByte);
        for (int i = 0; i < bits.length - 1; i++) {
            headerBytes[1] = BitUtils.setBit(headerBytes[1], i, bits[i]);
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(headerBytes);
            output.write(lengthBytes);
            output.write(this.mask);
            if (payload != null && payload.length > 0) {
                output.write(process(payload, 0));
            }
            return output.toByteArray();
        }
    }

    public byte[] getPayload() {
        return this.payload;
    }

    public byte[] process(final byte[] data, int maskIndex) {
        if (!isMasked()) {
            return data;
        }
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ mask[(maskIndex + i) % 4]);
        }
        return result;
    }

    @Override
    public String toString() {
        return "FrameInfo{" +
                "length=" + getLength() +
                "\nsizeToRead=" + payloadSize() +
                "\nadditionalBytesToRead=" + additionalBytesToRead() +
                "\npayloadSize=" + this.payload.length +
                "\nfirstByte=" + String.format("0x%02x", headerBytes[0]) +
                "\nfirstByte=" + BitUtils.bitsString(headerBytes[0]) +
                "\nsecondByte=" + BitUtils.bitsString(headerBytes[1]) +
                "\nisFinalFrame=" + isFinalFrame() +
                "\nopCode=" + getOpCode() +
                "\nmasked=" + isMasked() +
                '}';
    }
}
