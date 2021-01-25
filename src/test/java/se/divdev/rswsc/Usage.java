package se.divdev.rswsc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

public class Usage implements WebSocketEvent {

    private static final Logger LOGGER = LoggerFactory.getLogger(Usage.class);

    public static void main(String... args) throws URISyntaxException, IOException, InterruptedException {
        URI uri = new URI("ws://localhost:8080/");
        //URI uri = new URI("ws://echo.websocket.org/");
        //URI uri = new URI("wss://echo.websocket.org/");

        Usage usage = new Usage();

        RswsClient client = RswsClientBuilder.newBuilder()
                .withUri(uri)
                .withAutoRespondToPing(true)
                .withEventHandler(usage)
                .build();

        CompletableFuture<Void> cf = client
                .connect()
                .configureSocket(usage::configureSocket)
                .runAsync();

        cf.exceptionally(t -> {
            t.printStackTrace();
            return null;
        });

        int i = 0;
        while (!(cf.isCancelled() || cf.isCompletedExceptionally() || cf.isDone())) {
            String payload = "Hello World: " + i;
            if (i == 1) {
                client.ping(payload.getBytes());
            } else if (i == 2) {
                client.sendBinary(payload.getBytes());
            } else if (i == 5) {
                client.disconnect();
            } else {
                client.sendText(payload);
            }
            Thread.sleep(1_000);
            i++;
        }
        System.out.println("Exiting ...");
    }

    private final ByteArrayOutputStream aggregatedData = new ByteArrayOutputStream();

    private void configureSocket(final Socket socket) {
        // Configure socket here if needed
    }

    @Override
    public void onData(boolean finalFragment, byte[] payload) {
        try {
            aggregatedData.write(payload);
        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            if (finalFragment) {
                LOGGER.info("DATA -> Final: {}, Payload: {}", finalFragment, new String(aggregatedData.toByteArray()));
                aggregatedData.reset();
            }
        }
    }

    @Override
    public void onPing(boolean finalFragment, byte[] payload) {
        LOGGER.info("PING -> Final: {}, Payload: {}", finalFragment, new String(payload));
    }

    @Override
    public void onPong(boolean finalFragment, byte[] payload) {
        LOGGER.info("PONG -> Final: {}, Payload: {}", finalFragment, new String(payload));
    }
}
