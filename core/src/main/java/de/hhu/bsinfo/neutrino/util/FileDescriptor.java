package de.hhu.bsinfo.neutrino.util;

import java.io.Closeable;
import java.io.IOException;

public class FileDescriptor implements Closeable {

    private final int handle;

    private FileDescriptor(int handle) {
        this.handle = handle;
    }

    public int get() {
        return handle;
    }

    public static FileDescriptor create(int fd) {
        return new FileDescriptor(fd);
    }

    private static native int close0(int fd);

    @Override
    public void close() throws IOException {
        if (close0(handle) != 0) {
            throw new IOException("closing file descriptor failed");
        }
    }

    private static native int setMode0(int fd, int mode);

    public void setMode(OpenMode mode) {
        setMode0(handle, mode.value);
    }

    public enum OpenMode implements Flag {
        NONBLOCK(0x0004), APPEND(0x0008), SHLOCK(0x0010), EXLOCK(0x0020), ASYNC(0x0040), FSYNC(0x0080);

        private final int value;

        OpenMode(int value) {
            this.value = value;
        }

        @Override
        public long getValue() {
            return value;
        }
    }
}