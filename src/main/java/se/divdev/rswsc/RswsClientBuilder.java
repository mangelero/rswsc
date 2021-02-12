package se.divdev.rswsc;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import static se.divdev.rswsc.WebSocketUtils.SEC_WEBSOCKET_KEY_HEADER;

public class RswsClientBuilder {

    private static final int DEFAULT_MAX_FRAME_SIZE = 1024 * 1024;
    private static final String DEFAULT_HTTP_VERSION = "HTTP/1.1";
    private static final Supplier<SSLSocketFactory> DEFAULT_SSL_FACTORY = () -> (SSLSocketFactory) SSLSocketFactory.getDefault();

    private final URI uri;

    private final ScheduledExecutorService executorService;

    private final WebSocketEvent eventHandler;

    private final boolean autoRespondToPing;

    private final int maxFrameSize;

    private final Supplier<SSLSocketFactory> sslSocketFactorySupplier;

    private final String httpVersion;

    private final Map<String, String> headers;

    private final Duration pingInterval;

    private RswsClientBuilder(final URI uri,
                              final ScheduledExecutorService executorService,
                              final WebSocketEvent eventHandler,
                              final boolean autoRespondToPing,
                              final int maxFrameSize,
                              final Supplier<SSLSocketFactory> sslSocketFactorySupplier,
                              final String httpVersion,
                              final Map<String, String> headers,
                              final Duration pingInterval) {
        this.uri = uri;
        this.executorService = executorService;
        this.eventHandler = eventHandler;
        this.autoRespondToPing = autoRespondToPing;
        this.maxFrameSize = maxFrameSize;
        this.sslSocketFactorySupplier = sslSocketFactorySupplier;
        this.httpVersion = httpVersion;
        this.headers = headers;
        this.pingInterval = pingInterval;
    }

    private int getPort() {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        if (uri.getScheme().equalsIgnoreCase("ws")) {
            return 80;
        } else if (uri.getScheme().equalsIgnoreCase("wss")) {
            return 443;
        }
        return 0;
    }

    public static RswsClientBuilder newBuilder() {
        return newBuilder(null);
    }

    public static RswsClientBuilder newBuilder(final URI uri) {
        ThreadFactory defaultThreadFactory = r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        };

        RswsClientBuilder builder = new RswsClientBuilder(
                uri,
                Executors.newScheduledThreadPool(2, defaultThreadFactory),
                null,
                true,
                DEFAULT_MAX_FRAME_SIZE,
                DEFAULT_SSL_FACTORY,
                DEFAULT_HTTP_VERSION,
                new LinkedHashMap<>(),
                null
        );

        return builder.withHeader("User-Agent", "rswsc/1.0.0");
    }

    public RswsClientBuilder withUri(final URI uri) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withExecutorService(final ScheduledExecutorService executorService) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withEventHandler(final WebSocketEvent eventHandler) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withAutoRespondToPing(final boolean autoRespondToPing) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withMaxFrameSize(final int maxFrameSize) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withSslSocketFactorySupplier(final Supplier<SSLSocketFactory> sslSocketFactorySupplier) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withHttpVersion(final String httpVersion) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withPingInterval(final Duration pingInterval) {
        return new RswsClientBuilder(uri, executorService, eventHandler, autoRespondToPing, maxFrameSize, sslSocketFactorySupplier, httpVersion, headers, pingInterval);
    }

    public RswsClientBuilder withHeader(final String key, final String value) {
        this.headers.put(key, value);
        return this;
    }

    public RswsClient build() {

        // Force these headers
        withHeader("Connection", "Upgrade");
        withHeader("Upgrade", "websocket");
        withHeader("Sec-WebSocket-Version", "13");
        withHeader("Host", uri.getHost() + ":" + getPort());
        withHeader(SEC_WEBSOCKET_KEY_HEADER, WebSocketUtils.generateWebSocketKey());

        return new RswsClient(
                uri,
                executorService,
                eventHandler,
                autoRespondToPing,
                maxFrameSize,
                sslSocketFactorySupplier,
                httpVersion,
                headers,
                getPort(),
                pingInterval
        );
    }
}
