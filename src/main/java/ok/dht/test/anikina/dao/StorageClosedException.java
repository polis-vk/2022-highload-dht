package ok.dht.test.anikina.dao;

public class StorageClosedException extends RuntimeException {
    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }
}
