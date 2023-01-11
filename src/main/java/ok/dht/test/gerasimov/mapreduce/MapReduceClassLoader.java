package ok.dht.test.gerasimov.mapreduce;

public class MapReduceClassLoader extends ClassLoader {
    public Class<?> defineClass(String name, byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }
}
