package com.acme.bitpack;

/** Base commune aux implémentations. */
abstract class AbstractBitPacker implements BitPacker {
    protected int n;        // NON final
    protected int k;        // NON final
    protected int[] data;   // buffer compressé

    // Constructeur avec paramètres
    protected AbstractBitPacker(int n, int k) {
        this.n = n;
        this.k = k;
    }

    @Override public int size() { return n; }
    @Override public int bitsPerValue() { return k; }
    @Override public int[] compressed() { return data == null ? null : data.clone(); }
    @Override public long compressedBitLength() { return (long) (data == null ? 0 : data.length) * 32L; }
}