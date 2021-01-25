package se.divdev.rswsc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface WebSocketEvent {

    Logger LOGGER = LoggerFactory.getLogger(WebSocketEvent.class);

    default void onData(boolean finalFragment, byte[] payload) {
        LOGGER.info("onData method not implemented. Got {} bytes, Final frame: {}", payload.length, finalFragment);
    }

    default void onPing(boolean finalFragment, byte[] payload) {
        LOGGER.info("onPing method not implemented. Got {} bytes, Final frame: {}", payload.length, finalFragment);
    }

    default void onPong(boolean finalFragment, byte[] payload) {
        LOGGER.info("onPong method not implemented. Got {} bytes, Final frame: {}", payload.length, finalFragment);
    }
}
