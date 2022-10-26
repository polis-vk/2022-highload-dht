package ok.dht.test.monakhov;

import ok.dht.ServiceConfig;
import one.nio.server.AcceptorConfig;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.annotation.Nonnull;

public final class ServiceUtils {
    public static final String TIMESTAMP_HEADER = "TimeStamp: ";
    public static String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";
    public static final int QUEUE_SIZE = 1000;

    private ServiceUtils() {
    }

    public static <T extends Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static boolean isInvalidReplica(String ack, String from) {
        return (ack == null && from != null) || (ack != null && from == null);
    }

    public static AsyncHttpServerConfig createConfigFromPort(ServiceConfig serviceConfig) {
        AsyncHttpServerConfig httpConfig = new AsyncHttpServerConfig();
        httpConfig.clusterUrls = serviceConfig.clusterUrls();
        httpConfig.selfUrl = serviceConfig.selfUrl();
        httpConfig.workersNumber = Runtime.getRuntime().availableProcessors();
        httpConfig.queueSize = QUEUE_SIZE;
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = serviceConfig.selfPort();
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }

    public static byte[] serialize(@Nonnull Object obj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(out)) {
            os.writeObject(obj);
            return out.toByteArray();
        }
    }

    public static <T> T deserialize(@Nonnull byte[] data) throws IOException, ClassNotFoundException {
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (T) is.readObject();
        }
    }
}
