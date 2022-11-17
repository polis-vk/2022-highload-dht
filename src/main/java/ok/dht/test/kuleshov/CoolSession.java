package ok.dht.test.kuleshov;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.Iterator;

public class CoolSession extends HttpSession {
    Iterator<byte[]> iterator;
    boolean isChunked;

    public CoolSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public void writeChunks(Iterator<byte[]> iterator) throws IOException {
        isChunked = true;
        this.iterator = iterator;

        while (iterator.hasNext() && queueHead == null) {
                byte[] chunk = iterator.next();
                write(chunk, 0, chunk.length);
        }

        if (!iterator.hasNext()) {
            isChunked = false;
            super.scheduleClose();
        }
    }

    @Override
    protected void processWrite() throws Exception {
        if (isChunked) {
            writeChunks(iterator);
        } else {
            super.processWrite();
        }
    }
}
