package ok.dht.test.kurdyukov.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@SuppressWarnings("unchecked")
public class ObjectMapper {
    private ObjectMapper() {

    }

    public static byte[] serialize(Object obj) throws IOException {
        if (obj == null) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(out)) {
            os.writeObject(obj);
            return out.toByteArray();
        }
    }

    public static <T> T deserialize(byte[] data) throws IOException, ClassNotFoundException {
        if (data == null) {
            return null;
        }

        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (T) is.readObject();
        }
    }
}
