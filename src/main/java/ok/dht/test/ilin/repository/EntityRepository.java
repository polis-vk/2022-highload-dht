package ok.dht.test.ilin.repository;

import ok.dht.test.ilin.model.Entity;

import java.io.Closeable;

public interface EntityRepository extends Closeable {
    void upsert(Entity value);

    Entity get(String id);

    void delete(String id);
}
