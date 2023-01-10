package ok.dht.test.yasevich.service;

import ok.dht.test.yasevich.chunking.ChunkedResponse;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.concurrent.Executor;

class CustomHttpSession extends HttpSession {
    private volatile Response inWork;
    private final Executor executor;

    public CustomHttpSession(Socket socket, HttpServer server, Executor executor) {
        super(socket, server);
        this.executor = executor;
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        inWork = response;
        if (response instanceof ChunkedResponse chunkedResponse) {
            writeAsync(chunkedResponse);
        } else {
            super.writeResponse(response, includeBody);
        }
    }

    @Override
    protected void processWrite() throws Exception {
        if (inWork instanceof ChunkedResponse chunkedResponse) {
            writeAsync(chunkedResponse);
        } else {
            super.processWrite();
        }
    }

    private void writeAsync(ChunkedResponse chunkedResponse) {
        executor.execute(() -> {
            try {
                socket.write(chunkedResponse.getChunks());
                if (chunkedResponse.isDone()) {
                    processDone();
                }
            } catch (Exception e) {
                ServiceImpl.LOGGER.error("Error when proccesing chunkedResponse in session " + this);
                ServiceImpl.sendResponse(this, new Response(Response.INTERNAL_ERROR, Response.EMPTY));
            }
        });
    }

    private void processDone() {
        if (closing) {
            close();
        } else {
            listen(READABLE);
        }
    }
}
