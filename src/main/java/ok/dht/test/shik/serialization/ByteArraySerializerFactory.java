package ok.dht.test.shik.serialization;

public final class ByteArraySerializerFactory {

    public static final int LATEST_VERSION = 1;

    private ByteArraySerializerFactory() {

    }

    public static ByteArraySerializer latest() {
        return new ByteArraySerializerImpl();
    }
}
