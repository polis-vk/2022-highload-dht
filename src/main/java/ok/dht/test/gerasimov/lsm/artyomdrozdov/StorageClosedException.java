package ok.dht.test.gerasimov.lsm.artyomdrozdov;

public class StorageClosedException extends RuntimeException {

    public StorageClosedException(Throwable causedBy) {
        super(causedBy);
    }
}
