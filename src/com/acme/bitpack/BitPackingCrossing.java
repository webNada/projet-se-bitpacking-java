package com.acme.bitpack;

import static com.acme.bitpack.BitUtils.*;

/** Bit packing qui autorise le chevauchement entre 2 mots de 32 bits. */
public final class BitPackingCrossing extends AbstractBitPacker {

    public BitPackingCrossing(int n, int k) {
        super(n, k);
    }

    @Override public boolean crossesBoundaries() { return true; }

    @Override public void compress(int[] input) {
        if (input == null) throw new IllegalArgumentException("input is null");
        n = input.length;
        int max = 0;
        for (int v : input) {
            if (v < 0) throw new IllegalArgumentException("Negative values not supported");
            max = Math.max(max, v);
        }
        k = bitsRequiredNonNegative(max);
        long totalBits = (long) n * k;
        int words = ceilDiv((int) (totalBits & 0x7FFFFFFF), 32);
        data = new int[words];
        long p = 0;
        int msk = mask(k);
        for (int v : input) {
            int vv = v & msk;
            int w = (int) (p >>> 5);
            int off = (int) (p & 31);
            data[w] |= vv << off;
            int spill = off + k - 32;
            if (spill > 0) data[w + 1] |= vv >>> (k - spill);
            p += k;
        }
    }

    @Override public void decompress(int[] out) {
        if (out == null || out.length != n) throw new IllegalArgumentException("out length must be " + n);
        for (int i = 0; i < n; i++) out[i] = get(i);
    }

    @Override public int get(int index) {
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException();
        long bitIndex = (long) index * k;
        int w = (int) (bitIndex >>> 5);
        int off = (int) (bitIndex & 31);
        int low = data[w] >>> off;
        int bitsInLow = 32 - off;
        if (k <= bitsInLow) return low & mask(k);
        int hi = data[w + 1] & mask(k - bitsInLow);
        return (low | (hi << bitsInLow)) & mask(k);
    }
}