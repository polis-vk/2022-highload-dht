package ok.dht.test.kazakov.dao;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }

}
