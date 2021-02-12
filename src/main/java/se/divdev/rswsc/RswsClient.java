package se.divdev.rswsc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static se.divdev.rswsc.WebSocketUtils.SEC_WEBSOCKET_ACCEPT_HEADER;
import static se.divdev.rswsc.WebSocketUtils.SEC_WEBSOCKET_KEY_HEADER;

public class RswsClient implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RswsClient.class);

    private final URI uri;

    private final ScheduledExecutorService executorService;

    private final WebSocketEvent eventHandler;

    private final boolean autoRespondToPing;

    private final int maxFrameSize;

    private final Supplier<SSLSocketFactory> sslSocketFactorySupplier;

    private final String httpVersion;

    private final Map<String, String> headers;

    private final Duration pingInterval;

    private volatile boolean running = true;

    private IO io;

    private final int port;

    RswsClient(final URI uri,
               final ScheduledExecutorService executorService,
               final WebSocketEvent eventHandler,
               final boolean autoRespondToPing,
               final int maxFrameSize,
               final Supplier<SSLSocketFactory> sslSocketFactorySupplier,
               final String httpVersion,
               final Map<String, String> headers,
               final int port,
               final Duration pingInterval) {
        this.uri = uri;
        this.executorService = executorService;
        this.eventHandler = eventHandler;
        this.autoRespondToPing = autoRespondToPing;
        this.maxFrameSize = maxFrameSize;
        this.sslSocketFactorySupplier = sslSocketFactorySupplier;
        this.httpVersion = httpVersion;
        this.headers = headers;
        this.port = port;
        this.pingInterval = pingInterval;
    }

    public void disconnect() throws IOException {
        running = false;
        send(OpCode.CONNECTION_CLOSE);
    }

    @Override
    public void close() throws IOException {
        if (isAlive()) {
            disconnect();
        }
        executorService.shutdownNow();
        IO.close(io);
        running = false;
    }

    private Socket createSocket() throws IOException {
        switch (uri.getScheme().toLowerCase()) {
            case "ws":
                return new Socket(uri.getHost(), port);
            case "wss":
                Socket socket = sslSocketFactorySupplier.get().createSocket(uri.getHost(), port);
                ((SSLSocket) socket).startHandshake();
                return socket;
            default:
                throw new IllegalArgumentException(uri.getScheme() + " is not implemented");
        }
    }

    public RswsClient connect() throws IOException {
        try {
            io = new IO(createSocket());
            LOGGER.debug("Socket connected: {}", io.isAlive());

            io.println("GET " + uri + " " + httpVersion);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                io.println(String.format("%s: %s", entry.getKey(), entry.getValue()));
            }
            io.lf();
            io.commit();

            readAndValidateInitialResponse();
            enablePing();
            return this;
        } catch (Exception e) {
            LOGGER.error("Error connecting to {}", uri, e);
            close();
            throw new IOException(e);
        }
    }

    private void enablePing() {
        if (pingInterval == null) {
            LOGGER.info("No ping interval specified");
            return;
        }
        executorService.scheduleWithFixedDelay(this::ping, pingInterval.toMillis(), pingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public RswsClient configureSocket(final Consumer<Socket> socketConsumer) {
        socketConsumer.accept(io.socket);
        return this;
    }

    private Pattern compile(final String pattern) {
        String escaped = pattern.replace("+", "\\+");
        return Pattern.compile(escaped, Pattern.CASE_INSENSITIVE);
    }

    private void readAndValidateInitialResponse() throws IOException {
        LOGGER.debug("Waiting for initial response:");
        String initialResponse = new String(io.read());
        LOGGER.debug("Read {} bytes: {}", initialResponse.length(), initialResponse);

        String[] lines = initialResponse.split("\r\n|\n");
        String webSocketAccept = WebSocketUtils.generateSecWebSocketAccept(headers.get(SEC_WEBSOCKET_KEY_HEADER));

        List<String> expectedResponseLines = new ArrayList<>();
        expectedResponseLines.add(httpVersion + " 101.*");
        expectedResponseLines.add(SEC_WEBSOCKET_ACCEPT_HEADER + ": " + webSocketAccept);
        expectedResponseLines.add("Connection: Upgrade");
        expectedResponseLines.add("Upgrade: websocket");

        expectedResponseLines.removeIf(line -> Stream.of(lines).anyMatch(response -> compile(line).matcher(response).matches()));

        if (!expectedResponseLines.isEmpty()) {
            throw new IOException("Missing lines in initial response: " + expectedResponseLines);
        }
    }

    public boolean isAlive() {
        return io != null && io.isAlive() && running;
    }

    public CompletableFuture<Void> runAsync() {
        return CompletableFuture.runAsync(this::run, executorService);
    }

    private BiConsumer<Boolean, byte[]> resolveFunction(final WebSocketFrame frame) {
        if (eventHandler == null) {
            return (finalFragment, payload) -> LOGGER.warn("Incoming event, but no handler installed. Final: {}, Payload size: {}", finalFragment, payload.length);
        }
        if (frame.isPingFrame()) {
            return this::onPing;
        } else if (frame.isPongFrame()) {
            return eventHandler::onPong;
        }
        return eventHandler::onData;
    }

    public void run() {
        if (io == null) {
            throw new IllegalStateException("Not connected!");
        }
        try {
            while (isAlive()) {
                WebSocketFrame webSocketFrame = WebSocketFrame.incoming(io.read(maxFrameSize));
                LOGGER.debug("Incoming FrameInfo: {}", webSocketFrame.toString());

                byte[] processedPayload = webSocketFrame.getPayload();

                BiConsumer<Boolean, byte[]> function = resolveFunction(webSocketFrame);
                long expectedLengthOfPayload = webSocketFrame.payloadSize();
                int readLengthOfPayload = 0;
                long additionalBytesToRead;
                if (webSocketFrame.isConnectionClose()) {
                    break;
                }

                do {
                    readLengthOfPayload += processedPayload.length;
                    additionalBytesToRead = expectedLengthOfPayload - readLengthOfPayload;
                    boolean finalFrame = webSocketFrame.isFinalFrame() && additionalBytesToRead == 0;
                    dispatchEvent(function, finalFrame, processedPayload);
                    processedPayload = webSocketFrame.process(io.read(Math.min(additionalBytesToRead, maxFrameSize)), readLengthOfPayload - processedPayload.length);
                } while (additionalBytesToRead > 0);
            }
        } catch (Exception e) {
            if (running) {
                running = false;
                LOGGER.error("Error in websocket client", e);
                throw new RuntimeException(e);
            }
        } finally {
            IO.close(this);
        }
    }

    private void ping() {
        try {
            ping(new byte[0]);
        } catch (IOException exception) {
            LOGGER.error("Error sending ping", exception);
        }
    }

    public void ping(final byte[] payload) throws IOException {
        send(OpCode.PING, payload);
    }

    public void pong(final byte[] payload) throws IOException {
        send(OpCode.PONG, payload);
    }

    public void sendText(final String text) throws IOException {
        send(OpCode.TEXT, text.getBytes());
    }

    public void sendBinary(final byte[] data) throws IOException {
        send(OpCode.BINARY, data);
    }

    private void send(final OpCode opCode, final byte... payload) throws IOException {
        LOGGER.debug("Sending {}", opCode);
        try (InputStream inputStream = new ByteArrayInputStream(payload)) {
            send(opCode, inputStream);
        }
    }

    public void send(final OpCode opCode, final InputStream inputStream) throws IOException {
        byte[] buffer = new byte[maxFrameSize];
        int read;
        boolean first = true;
        do {
            read = inputStream.read(buffer);
            boolean finalFrame = inputStream.available() == 0;
            WebSocketFrame webSocketFrame = WebSocketFrame.outgoing(first ? opCode : OpCode.CONTINUATION);
            byte[] dataToSend = new byte[read > 0 ? read : 0];
            System.arraycopy(buffer, 0, dataToSend, 0, dataToSend.length);
            LOGGER.debug("Sending frame with length: {}, final: {}", dataToSend.length, finalFrame);
            send(webSocketFrame.build(finalFrame, dataToSend));
            first = false;
        } while (inputStream.available() > 0);
    }

    private void send(final byte[] data) {
        executorService.submit(() -> {
            try {
                io.write(data);
                io.commit();
            } catch (IOException exception) {
                LOGGER.error("Error sending data", exception);
            }
        });
    }

    // WebSocket event dispatch
    private void dispatchEvent(final BiConsumer<Boolean, byte[]> consumer, final boolean finalFragment, final byte[] payload) {
        executorService.submit(() -> {
            try {
                consumer.accept(finalFragment, payload);
            } catch (Exception e) {
                LOGGER.error("Error while dispatching data", e);
            }
        });
    }

    private final ByteArrayOutputStream incomingPingPayload = new ByteArrayOutputStream();

    // Special treatment for ping
    private void onPing(final boolean finalFragment, final byte[] payload) {
        eventHandler.onPing(finalFragment, payload);
        if (!autoRespondToPing) {
            return;
        }
        try {
            incomingPingPayload.write(payload);
            if (finalFragment) {
                send(OpCode.PONG, incomingPingPayload.toByteArray());
                incomingPingPayload.reset();
            }
        } catch (IOException exception) {
            LOGGER.error("Error handling ping");
        }
    }
}
