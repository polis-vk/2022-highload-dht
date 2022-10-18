package ok.dht.test.monakhov;

import one.nio.http.HttpClient;
import one.nio.net.ConnectionString;

import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncHttpClient extends HttpClient {
    private final AtomicBoolean isAvailable = new AtomicBoolean(true);

    public AsyncHttpClient(ConnectionString conn) {
        super(conn);
    }

    public AtomicBoolean available() {return isAvailable;}
}
