package ok.dht.test.komissarov.utils;

public final class Validator {

    private static final String EMPTY_KEY = "";

    public boolean validate(String key) {
        return key != null && key.equals(EMPTY_KEY);
    }

}
