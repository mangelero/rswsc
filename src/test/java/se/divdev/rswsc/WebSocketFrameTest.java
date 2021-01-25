package se.divdev.rswsc;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class WebSocketFrameTest {
    String payload = "HELLO!";

    @Test
    public void testWebSocketFrameShortMessage() throws Exception {
        WebSocketFrame outgoing = WebSocketFrame.outgoing(OpCode.TEXT);

        byte[] raw = outgoing.build(true, payload.getBytes());
        WebSocketFrame incoming = WebSocketFrame.incoming(raw);
        System.out.println(incoming);

        Assertions.assertEquals(payload, new String(incoming.getPayload()));
        Assertions.assertEquals(OpCode.TEXT, incoming.getOpCode());
    }

    @Test
    public void testWebSocketFrameMediumMessage() throws Exception {
        String payload = new String(new char[50]).replace("\0", this.payload);
        WebSocketFrame outgoing = WebSocketFrame.outgoing(OpCode.TEXT);

        byte[] raw = outgoing.build(true, payload.getBytes());
        WebSocketFrame incoming = WebSocketFrame.incoming(raw);
        System.out.println(incoming);

        Assertions.assertEquals(payload, new String(incoming.getPayload()));
        Assertions.assertEquals(OpCode.TEXT, incoming.getOpCode());
    }

    @Test
    public void testWebSocketFrameLargeMessage() throws Exception {
        String payload = new String(new char[11000]).replace("\0", this.payload);
        WebSocketFrame outgoing = WebSocketFrame.outgoing(OpCode.TEXT);

        byte[] raw = outgoing.build(true, payload.getBytes());
        WebSocketFrame incoming = WebSocketFrame.incoming(raw);
        System.out.println(incoming);

        Assertions.assertEquals(payload, new String(incoming.getPayload()));
        Assertions.assertEquals(OpCode.TEXT, incoming.getOpCode());
    }

    @Test
    public void testFaultyOpCode() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () -> WebSocketFrame.incoming(new byte[]{4, 0, 0x20, 0x20, 0x20, 0x20}));
    }
}
