package ok.dht.test.shakhov.storage;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }

}
