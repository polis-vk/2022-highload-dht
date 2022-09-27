package ok.dht.kovalenko.dao.dto;

import ok.dht.kovalenko.dao.utils.DaoUtils;

import java.nio.ByteBuffer;

public record ByteBufferRange(ByteBuffer from, ByteBuffer to) {

    public static ByteBufferRange ALL_RANGE = new ByteBufferRange(null, null);
    public static ByteBufferRange DEFAULT_RANGE = new ByteBufferRange(DaoUtils.EMPTY_BYTEBUFFER, DaoUtils.EMPTY_BYTEBUFFER);

    public ByteBufferRange(ByteBuffer from, ByteBuffer to) {
        this.from = from == null ? DaoUtils.EMPTY_BYTEBUFFER : from;
        this.to = to;
    }
}
