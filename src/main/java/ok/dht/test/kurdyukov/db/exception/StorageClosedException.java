package ok.dht.test.kurdyukov.db.exception;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }
}
