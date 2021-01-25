package se.divdev.rswsc;

import java.util.Arrays;

public enum OpCode {
    UNKNOWN(0xFF),
    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    // *  %x3-7 are reserved for further non-control frames
    CONNECTION_CLOSE(0x8),
    PING(0x9),
    PONG(0xA);
    //*  %xB-F are reserved for further control frames

    public final byte value;

    OpCode(final int value) {
        this((byte) value);
    }

    OpCode(final byte value) {
        this.value = value;
    }

    public static OpCode fromValue(final byte value) {
        return Arrays.stream(values())
                .filter(oc -> oc.value == value)
                .findFirst()
                .orElse(UNKNOWN);
    }
}
