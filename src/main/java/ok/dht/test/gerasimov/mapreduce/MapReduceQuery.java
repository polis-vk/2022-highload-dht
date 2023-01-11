package ok.dht.test.gerasimov.mapreduce;

import org.iq80.leveldb.DB;

import java.util.List;

public interface MapReduceQuery {
    byte[] map(DB dao);

    String reduce(List<byte[]> list);
}
