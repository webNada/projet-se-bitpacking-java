package com.acme.bitpack;

import java.util.Arrays;

/** Overflow bit packing allowing crossing word boundaries. */
public class BitPackingOverflowCrossing extends AbstractBitPacker {
    private int[] values;
    private int[] overflow;
    private int overflowCount;
    private int baseBits;

    public BitPackingOverflowCrossing(int n, int k) {
        super(n, k);
        this.values = new int[n];
        this.overflow = new int[n]; // worst case
        this.baseBits = k - 1; // reserve 1 bit for overflow flag
    }

    @Override
    public boolean crossesBoundaries() {
        return true;
    }

    @Override
    public void compress(int[] data) {
        if (data.length != n) throw new IllegalArgumentException("Data length mismatch");
        System.arraycopy(data, 0, values, 0, n);
        Arrays.fill(this.data, 0);
        Arrays.fill(overflow, 0);
        overflowCount = 0;

        int baseMask = BitUtils.mask(baseBits);
        int mask = BitUtils.mask(k);
        int bitIndex = 0;

        for (int i = 0; i < n; i++) {
            int value = data[i];
            int wordIndex = bitIndex / 32;
            int bitOffset = bitIndex % 32;
            int remainingBits = 32 - bitOffset;

            if (value < (1 << baseBits)) {
                // fits in base bits, store directly with flag 0
                int encoded = value & baseMask;
                if (remainingBits >= k) {
                    this.data[wordIndex] |= encoded << bitOffset;
                } else {
                    int bitsInFirst = remainingBits;
                    int bitsInSecond = k - bitsInFirst;
                    this.data[wordIndex] |= (encoded & BitUtils.mask(bitsInFirst)) << bitOffset;
                    this.data[wordIndex + 1] |= (encoded >>> bitsInFirst) & BitUtils.mask(bitsInSecond);
                }
            } else {
                // overflow, store position in overflow area with flag 1
                overflow[overflowCount++] = value;
                int encoded = ((overflowCount - 1) & baseMask) | (1 << baseBits);
                if (remainingBits >= k) {
                    this.data[wordIndex] |= encoded << bitOffset;
                } else {
                    int bitsInFirst = remainingBits;
                    int bitsInSecond = k - bitsInFirst;
                    this.data[wordIndex] |= (encoded & BitUtils.mask(bitsInFirst)) << bitOffset;
                    this.data[wordIndex + 1] |= (encoded >>> bitsInFirst) & BitUtils.mask(bitsInSecond);
                }
            }
            bitIndex += k;
        }

        // append overflow area
        int overflowStartBitIndex = bitIndex;
        for (int i = 0; i < overflowCount; i++) {
            int wordIndex = overflowStartBitIndex / 32;
            int bitOffset = overflowStartBitIndex % 32;
            int value = overflow[i] & mask;
            int remainingBits = 32 - bitOffset;
            if (remainingBits >= k) {
                if (wordIndex < this.data.length) {
                    this.data[wordIndex] |= value << bitOffset;
                } else {
                    // expand array if needed
                    int[] newData = new int[wordIndex + 1];
                    System.arraycopy(this.data, 0, newData, 0, this.data.length);
                    this.data = newData;
                    this.data[wordIndex] |= value << bitOffset;
                }
            } else {
                int bitsInFirst = remainingBits;
                int bitsInSecond = k - bitsInFirst;
                if (wordIndex < this.data.length) {
                    this.data[wordIndex] |= (value & BitUtils.mask(bitsInFirst)) << bitOffset;
                } else {
                    int[] newData = new int[wordIndex + 2];
                    System.arraycopy(this.data, 0, newData, 0, this.data.length);
                    this.data = newData;
                    this.data[wordIndex] |= (value & BitUtils.mask(bitsInFirst)) << bitOffset;
                }
                if (wordIndex + 1 < this.data.length) {
                    this.data[wordIndex + 1] |= (value >>> bitsInFirst) & BitUtils.mask(bitsInSecond);
                } else {
                    int[] newData = new int[wordIndex + 2];
                    System.arraycopy(this.data, 0, newData, 0, this.data.length);
                    this.data = newData;
                    this.data[wordIndex + 1] |= (value >>> bitsInFirst) & BitUtils.mask(bitsInSecond);
                }
            }
            overflowStartBitIndex += k;
        }
    }

    @Override
    public int get(int index) {
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException();
        int bitIndex = index * k;
        int wordIndex = bitIndex / 32;
        int bitOffset = bitIndex % 32;
        int remainingBits = 32 - bitOffset;
        int value;
        if (remainingBits >= k) {
            value = (this.data[wordIndex] >>> bitOffset) & BitUtils.mask(k);
        } else {
            int bitsInFirst = remainingBits;
            int bitsInSecond = k - bitsInFirst;
            value = (this.data[wordIndex] >>> bitOffset) & BitUtils.mask(bitsInFirst);
            value |= (this.data[wordIndex + 1] & BitUtils.mask(bitsInSecond)) << bitsInFirst;
        }
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
