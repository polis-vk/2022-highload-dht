package ok.dht.test.gerasimov;

import one.nio.serial.Serializer;

import java.io.IOException;

@SuppressWarnings("unchecked")
public final class ObjectMapper {
    private ObjectMapper() {

    }

    public static byte[] serialize(Object obj) throws IOException {
        if (obj == null) {
            return new byte[0];
        }

        return Serializer.serialize(obj);
    }

    public static <T> T deserialize(byte[] data) throws IOException, ClassNotFoundException {
        if (data == null) {
            return null;
        }

        return (T) Serializer.deserialize(data);
    }
}
