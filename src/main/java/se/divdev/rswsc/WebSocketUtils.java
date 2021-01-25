package se.divdev.rswsc;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class WebSocketUtils {

    static final String SEC_WEBSOCKET_KEY_HEADER = "Sec-WebSocket-Key";
    static final String SEC_WEBSOCKET_ACCEPT_HEADER = "Sec-WebSocket-Accept";

    private static final MessageDigest MD;

    private static final String CONCAT = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    static {
        try {
            MD = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static final String generateSecWebSocketAccept(final String key) {
        String accept = key.concat(CONCAT);
        byte[] sha1 = MD.digest(accept.getBytes());
        return Base64.getEncoder().encodeToString(sha1);
    }

    public static final String generateWebSocketKey() {
        return generateWebSocketKey(16);
    }

    public static final String generateWebSocketKey(final int len) {
        byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) (Math.random() * (127 - 32) + 32);
        }
        return Base64.getEncoder().encodeToString(data);
    }
}
