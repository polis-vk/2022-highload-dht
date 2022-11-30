package ok.dht.test.kuleshov;

public final class Validator {
    private Validator() {

    }

    public static boolean isCorrectId(String id) {
        return id != null && !id.isBlank();
    }
}
