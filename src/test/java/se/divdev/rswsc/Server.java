package se.divdev.rswsc;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Server {


    public static void main(String... args) throws Exception {


        ServerSocket server = new ServerSocket(8080);
        Socket socket = server.accept();

        InputStream inputStream = socket.getInputStream();
        OutputStream outputStream = socket.getOutputStream();

        byte[] data = new byte[8192];
        int initial = inputStream.read(data);

        String initialData = new String(data, 0, initial);

        Map<String, String> headers = new HashMap<>();
        for (String row : initialData.split("\r\n")) {
            System.out.println("row: " + row);
            if (row.contains(":")) {
                String[] keyValue = row.split(":");
                headers.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        String key = headers.get("Sec-WebSocket-Key");
        String accept = WebSocketUtils.generateSecWebSocketAccept(key);

        outputStream.write(("HTTP/1.1 101 Switching Protocols\r\n" +
                "Sec-WebSocket-Accept: " + accept + "\r\n" +
                "Connection: Upgrade\r\n" +
                "Upgrade: websocket\r\n\r\n").getBytes());
        outputStream.flush();

        System.out.println("initial " + initial + " bytes -> " + new String(data, 0, initial));


        for (int i = 0; i < 10; i++) {
            byte[] headerBytes = new byte[65535];
            int read = inputStream.read(headerBytes);
            WebSocketFrame webSocketFrame = WebSocketFrame.incoming(headerBytes);

            System.out.println("Got frame: " + webSocketFrame);

            StringBuilder sb = new StringBuilder();
            for (byte b : webSocketFrame.getPayload()) {
                sb.append(String.format("%c(0x%02x) ", (char) b, b));
            }
            System.out.println("Got data: " + sb.toString());

            if (!webSocketFrame.isPongFrame()) {
                // Write ping:
                outputStream.write(new byte[]{(byte) 0x89, 0x05});
                outputStream.write("Hello".getBytes());
                outputStream.flush();
            }
        }


    }

}
