package ok.dht.test.kurdyukov.utils;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@SuppressWarnings("unchecked")
public final class ObjectMapper {
    private ObjectMapper() {

    }

    public static byte[] serialize(@Nonnull Object obj) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(out)) {
            os.writeObject(obj);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(@Nonnull byte[] data) {
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (T) is.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
