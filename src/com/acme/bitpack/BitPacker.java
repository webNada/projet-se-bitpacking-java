package com.acme.bitpack;

public interface BitPacker {
    int size();
    int bitsPerValue();
    int[] compressed();
    long compressedBitLength();
    boolean crossesBoundaries();
    void compress(int[] data);
    int get(int index);
    void decompress(int[] out);
}
