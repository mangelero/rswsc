package se.divdev.rswsc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

class IO implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(IO.class);

    private final ThreadLocal<ByteArrayOutputStream> outputBuffers = ThreadLocal.withInitial(ByteArrayOutputStream::new);


    final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public IO(final Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    void commit() throws IOException {
        if (socket == null || !socket.isConnected() || socket.isClosed() || socket.isOutputShutdown()) {
            throw new IllegalStateException("Not connected!");
        }
        synchronized (this.outputStream) {
            outputBuffers.get().writeTo(this.outputStream);
            this.outputStream.flush();
            LOGGER.debug("Flushing outputstream");
            outputBuffers.get().reset();
        }
    }

    void println(final String line) throws IOException {
        write(line.concat("\r\n").getBytes());
    }

    void lf() throws IOException {
        println("");
    }

    void write(final byte[] data) throws IOException {
        outputBuffers.get().write(data);
    }

    byte[] read() throws IOException {
        return read(65536);
    }

    byte[] read(final long length) throws IOException {
        synchronized (this.inputStream) {
            if (length <= 0) {
                return new byte[0];
            }
            byte[] buffer = new byte[(int) length];
            int bytesRead = inputStream.read(buffer);
            if (bytesRead <= 0) {
                return new byte[0];
            }
            byte[] result = new byte[bytesRead];
            System.arraycopy(buffer, 0, result, 0, result.length);
            return result;
        }
    }

    @Override
    public void close() throws IOException {
        close(inputStream);
        close(outputStream);
        close(socket);
    }

    static void close(final Closeable closeable) {
        if (closeable == null) {
            LOGGER.debug("Not closing null closable");
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.debug("Error closing closable", e);
        }
    }

    boolean isAlive() {
        if (socket.isClosed() || !socket.isConnected()) {
            return false;
        }
        return true;
    }
}
