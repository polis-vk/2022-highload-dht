package ok.dht.test.galeev.dao.utils;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.galeev.dao.MSConverter;

import java.nio.charset.StandardCharsets;

public class StringByteConverter implements MSConverter<String, byte[]> {
    @Override
    public String getKeyFromMS(MemorySegment ms) {
        return (ms == null) ? null : new String(ms.asReadOnly().toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] getValFromMS(MemorySegment ms) {
        return (ms == null) ? null : ms.asReadOnly().toByteArray();
    }

    @Override
    public MemorySegment getMSFromKey(String key) {
        return (key == null) ? null : MemorySegment.ofArray(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public MemorySegment getMSFromVal(byte[] val) {
        return (val == null) ? null : MemorySegment.ofArray(val);
    }
}
