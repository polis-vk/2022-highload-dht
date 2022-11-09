package ok.dht.test.gerasimov.sharding;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shard {
    private static final Pattern URL_PATTERN = Pattern.compile("(^.+://.+):([0-9]+).*");

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
        } else {
            throw new IllegalArgumentException();
        }
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
