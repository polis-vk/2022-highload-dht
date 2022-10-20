package ok.dht.test.labazov.dao;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }

}
