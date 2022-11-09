package ok.dht.test.gerasimov.sharding;

import one.nio.http.HttpClient;
import one.nio.net.ConnectionString;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shard {
    private static final Pattern URL_PATTERN = Pattern.compile("(^.+://.+):([0-9]+).*");
    private static final String CONNECTION_STRING_PATTERN = "%s:%s?timeout=%d";
    private static final int READ_TIMEOUT_MS = 1000 * 60 * 5;

    private final HttpClient httpClient;
    private final String host;
    private final int port;
    private final AtomicBoolean isAvailable = new AtomicBoolean(true);
    private final int pos;

    public Shard(String url, int pos) {
        this.pos = pos;
        Matcher matcher = URL_PATTERN.matcher(url);
        if (matcher.matches()) {
            this.host = matcher.group(1);
            this.port = Integer.parseInt(matcher.group(2));
            this.httpClient = new HttpClient(
                    new ConnectionString(
                            String.format(
                                    CONNECTION_STRING_PATTERN,
                                    host,
                                    port,
                                    READ_TIMEOUT_MS
                            )
                    )
            );
        } else {
            throw new IllegalArgumentException();
        }
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getPos() {
        return pos;
    }

    public AtomicBoolean isAvailable() {
        return isAvailable;
    }
}
