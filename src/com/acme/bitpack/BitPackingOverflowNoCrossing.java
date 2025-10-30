package com.acme.bitpack;

import java.util.Arrays;

/** Overflow bit packing without crossing word boundaries. */
public class BitPackingOverflowNoCrossing extends AbstractBitPacker {
    private int[] values;
    private int[] overflow;
    private int overflowCount;
    private int baseBits;

    public BitPackingOverflowNoCrossing(int n, int k) {
        super(n, k);
        this.values = new int[n];
        this.overflow = new int[n]; // worst case
        this.baseBits = k - 1; // reserve 1 bit for overflow flag
    }

    @Override
    public boolean crossesBoundaries() {
        return false;
    }

    @Override
    public void compress(int[] data) {
        if (data.length != n) throw new IllegalArgumentException("Data length mismatch");
        System.arraycopy(data, 0, values, 0, n);
        Arrays.fill(this.data, 0);
        Arrays.fill(overflow, 0);
        overflowCount = 0;

        int baseMask = BitUtils.mask(baseBits);
        int bitsPerInt = 32;
        int valuesPerInt = bitsPerInt / k;
        int mask = BitUtils.mask(k);

        for (int i = 0; i < n; i++) {
            int value = data[i];
            if (value < (1 << baseBits)) {
                // fits in base bits, store directly with flag 0
                int wordIndex = i / valuesPerInt;
                int bitOffset = (i % valuesPerInt) * k;
                this.data[wordIndex] |= (value & baseMask) << bitOffset;
            } else {
                // overflow, store position in overflow area with flag 1
                overflow[overflowCount++] = value;
                int wordIndex = i / valuesPerInt;
                int bitOffset = (i % valuesPerInt) * k;
                this.data[wordIndex] |= ((overflowCount - 1) & baseMask) << bitOffset;
                this.data[wordIndex] |= (1 << (bitOffset + baseBits)); // set overflow flag
            }
        }

        // append overflow area
        int overflowStartIndex = BitUtils.ceilDiv(n * k, 32);
        for (int i = 0; i < overflowCount; i++) {
            int wordIndex = overflowStartIndex + (i * k / 32);
            int bitOffset = (i * k) % 32;
            int value = overflow[i] & mask;
            if (wordIndex >= this.data.length) {
                int[] newData = new int[wordIndex + 1];
                System.arraycopy(this.data, 0, newData, 0, this.data.length);
                this.data = newData;
            }
            if (bitOffset + k <= 32) {
                this.data[wordIndex] |= value << bitOffset;
            } else {
                // since no crossing, we need to ensure it fits, but for simplicity, expand if needed
                if (wordIndex + 1 >= this.data.length) {
                    int[] newData = new int[wordIndex + 2];
                    System.arraycopy(this.data, 0, newData, 0, this.data.length);
                    this.data = newData;
                }
                this.data[wordIndex] |= (value & BitUtils.mask(32 - bitOffset)) << bitOffset;
                this.data[wordIndex + 1] |= (value >>> (32 - bitOffset)) & BitUtils.mask(k - (32 - bitOffset));
            }
        }
    }

    @Override
    public int get(int index) {
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException();
        int bitsPerInt = 32;
        int valuesPerInt = bitsPerInt / k;
        int wordIndex = index / valuesPerInt;
        int bitOffset = (index % valuesPerInt) * k;
        int value = (this.data[wordIndex] >>> bitOffset) & BitUtils.mask(k);
        int flag = (value >>> baseBits) & 1;
        int baseValue = value & BitUtils.mask(baseBits);
        if (flag == 0) {
            return baseValue;
        } else {
            return overflow[baseValue];
        }
    }

    @Override
    public void decompress(int[] out) {
        if (out.length != n) throw new IllegalArgumentException("Output length mismatch");
        for (int i = 0; i < n; i++) {
            out[i] = get(i);
        }
    }

    // Use default from AbstractBitPacker
}
