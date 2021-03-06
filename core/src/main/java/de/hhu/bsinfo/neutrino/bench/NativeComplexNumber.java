package de.hhu.bsinfo.neutrino.bench;

import de.hhu.bsinfo.neutrino.struct.field.NativeDouble;
import de.hhu.bsinfo.neutrino.struct.Struct;
import de.hhu.bsinfo.neutrino.struct.LinkNative;

@LinkNative("complex_t")
public class NativeComplexNumber extends Struct {

    public final NativeDouble real = doubleField("real");
    public final NativeDouble imaginary = doubleField("imaginary");

    public NativeComplexNumber() {}

    public NativeComplexNumber(double real, double imaginary) {
        this.real.set(real);
        this.imaginary.set(imaginary);
    }

    public NativeComplexNumber(long handle) {
        super(handle);
    }
}
