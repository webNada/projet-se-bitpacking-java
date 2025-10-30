package com.acme.bitpack;

final class BitUtils {
    private BitUtils() {}

    static int bitsRequiredNonNegative(int max) {
        if (max <= 0) return 1; // represent zero with 1 bit minimum
        return 32 - Integer.numberOfLeadingZeros(max);
    }

    static int ceilDiv(int a, int b) { return (a + b - 1) / b; }

    static int mask(int bits) {
        return bits >= 32 ? -1 : (1 << bits) - 1;
    }

    static int ceilLog2(int x) {
        if (x <= 1) return 0;
        return 32 - Integer.numberOfLeadingZeros(x - 1);
    }
}
