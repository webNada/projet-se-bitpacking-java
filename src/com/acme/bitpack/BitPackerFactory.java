package com.acme.bitpack;

public final class BitPackerFactory {
    private BitPackerFactory() {}

    public static BitPacker create(CompressionMode mode, int n, int k) {
        return switch (mode) {
            case NO_CROSSING -> new BitPackingNoCrossing(n, k);
            case CROSSING -> new BitPackingCrossing(n, k);
            case OVERFLOW_NO_CROSSING -> new BitPackingOverflowNoCrossing(n, k);
            case OVERFLOW_CROSSING -> new BitPackingOverflowCrossing(n, k);
        };
    }
}
