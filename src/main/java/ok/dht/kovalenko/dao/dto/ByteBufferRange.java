package ok.dht.kovalenko.dao.dto;

import ok.dht.kovalenko.dao.utils.DaoUtils;

import java.nio.ByteBuffer;

public record ByteBufferRange(ByteBuffer from, ByteBuffer to) {

    public ByteBufferRange(ByteBuffer from, ByteBuffer to) {
        this.from = from == null ? DaoUtils.EMPTY_BYTEBUFFER : from;
        this.to = to;
    }
}
