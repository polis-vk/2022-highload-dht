package ok.dht.test.shik.serialization;

import ok.dht.test.shik.model.DBValue;

public interface ByteArraySerializer {

    byte[] serialize(DBValue dbValue);

    DBValue deserialize(byte[] bytes);
}
