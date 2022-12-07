package ok.dht.test.gerasimov.mapreduce;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class JavaCodeToMapReduceQuery {

    private JavaCodeToMapReduceQuery() {
    }

    public static byte[] transform(Class<?> clazz) {
        try {
            String path = clazz.getCanonicalName().replace(".", "/").concat(".class");
            byte[] bytes = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(path)).readAllBytes();
            String string = new String(bytes, StandardCharsets.UTF_8);
            return string.getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Can not load map reduce query", e);
        }
    }
}
