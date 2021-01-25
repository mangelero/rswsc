package se.divdev.rswsc;

public class BitUtils {

    public static boolean getBit(final byte b, int bit) {
        return (b & (1 << bit)) > 0;
    }

    public static byte enableBit(final byte b, final int bit) {
        return (byte) (b | (1 << bit));
    }

    public static byte disableBit(final byte b, final int bit) {
        return (byte) (b & ~(1 << bit));
    }

    public static byte setBit(final byte b, final int bit, final boolean value) {
        if (value) {
            return enableBit(b, bit);
        } else {
            return disableBit(b, bit);
        }
    }

    public static boolean[] decode(final byte b) {
        boolean[] result = new boolean[8];
        for (int i = 0; i < 8; i++) {
            result[i] = getBit(b, i);
        }
        return result;
    }

    public static String bitsString(final byte b) {
        return bitsString(decode(b));
    }

    public static String bitsString(final boolean[] bits) {
        return bitsString(bits, 0, bits.length);
    }

    public static String bitsString(final boolean[] bits, int offset, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            boolean bit = bits[i + offset];
            sb.append(bit ? 1 : 0).append(" ");
        }
        return sb.toString().trim();
    }

    public static byte extract(byte b, int offset, int length) {
        byte result = 0;
        for (int i = 0; i < length; i++) {
            boolean bit = getBit(b, 7 - offset - i);
            result = setBit(result, length - i - 1, bit);
        }
        return result;
    }
}
