package de.hhu.bsinfo.neutrino.struct;

import de.hhu.bsinfo.neutrino.util.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Struct {

    private final ByteBuffer byteBuffer;
    private final long handle;

    protected Struct(int size) {
        byteBuffer = ByteBuffer.allocateDirect(size);
        byteBuffer.order(ByteOrder.nativeOrder());
        handle = MemoryUtil.getAddress(byteBuffer);
    }

    protected Struct(long handle, int size) {
        byteBuffer = MemoryUtil.wrap(handle, size);
        byteBuffer.order(ByteOrder.nativeOrder());
        this.handle = handle;
    }

    protected final ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public long getHandle() {
        return handle;
    }

    public void free() {
        MemoryUtil.free(handle);
    }
}
