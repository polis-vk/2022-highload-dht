package ok.dht.test.nadutkin.impl.utils;

import java.io.Serializable;

public record StoredValue(byte[] value, Long timestamp) implements Serializable {}
