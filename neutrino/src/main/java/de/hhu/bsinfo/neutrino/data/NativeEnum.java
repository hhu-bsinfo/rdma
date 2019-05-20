package de.hhu.bsinfo.neutrino.data;

import java.nio.ByteBuffer;

public class NativeEnum<T extends Enum<T>> extends NativeDataType {

    private final EnumConverter<T> converter;

    public NativeEnum(ByteBuffer byteBuffer, int offset, EnumConverter<T> converter) {
        super(byteBuffer, offset);
        this.converter = converter;
    }

    public void set(final T value) {
        getByteBuffer().putInt(getOffset(), converter.toInt(value));
    }

    public T get() {
        return converter.toEnum(getByteBuffer().getInt(getOffset()));
    }

    @Override
    public String toString() {
        return super.toString() + " " + get().name();
    }
}