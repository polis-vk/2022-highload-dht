package ok.dht.test.skroba.dao.exceptions;

public class StorageClosedException extends RuntimeException {
    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }
}
