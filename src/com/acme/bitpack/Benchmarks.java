package com.acme.bitpack;

import java.util.Arrays;
import java.util.Random;

/** Simple micro-benchmarks with a clear protocol. */
public final class Benchmarks {
    public record Result(long compressNs, long getNs, long decompressNs, long compressedBits) {}

    /*
     * Runs warm-up and measured iterations.
     * @param packer codec
     * @param data input values (non-negative)
     * @param probes number of random get() probes
     */
    public static Result run(BitPacker packer, int[] data, int probes, long seed) {
        // Warm up JIT
        for (int i = 0; i < 3; i++) cycle(packer, data, probes, seed);
        // Measured
        return cycle(packer, data, probes, seed + 1);
    }
    
    /**
     * One complete benchmark cycle: compress → read → decompress
     */
    private static Result cycle(BitPacker packer, int[] data, int probes, long seed) {
        Random rnd = new Random(seed);
        //COMPRESSION MEASUREMENT
        long t0 = System.nanoTime();
        packer.compress(data);
        long t1 = System.nanoTime();
        //RANDOM READ MEASUREMENT (tests direct access)
        int s = 0;  // Dummy variable to prevent optimization
        for (int i = 0; i < probes; i++) s ^= packer.get(rnd.nextInt(data.length));// Random XOR
        long t2 = System.nanoTime();
        //DECOMPRESSION MEASUREMENT
        int[] out = new int[data.length];
        packer.decompress(out);
        long t3 = System.nanoTime();
        //VERIFICATION: decompression should return original data
        if (!Arrays.equals(out, data)) throw new AssertionError("decompress mismatch: " + s);
        //RETURN RESULTS
        return new Result(t1 - t0, t2 - t1, t3 - t2, packer.compressedBitLength());
    }
      /**
     * Calculates total time WITHOUT compression
     */
    public static double totalSecondsNoCompression(int n, double bandwidthBitsPerSec, double latencySeconds) {
        return latencySeconds + (32.0 * n) / bandwidthBitsPerSec;
    }
    /**
     * Calculates total time WITH compression
     */
    public static double totalSecondsWithCompression(Result r, double bandwidthBitsPerSec, double latencySeconds) {
        return latencySeconds + (r.compressNs + r.decompressNs) / 1e9 + (r.compressedBits) / bandwidthBitsPerSec;
    }

    /** Break-even bandwidth below which compression outperforms raw (ignoring latency, which cancels). */
    public static double breakEvenBandwidthBitsPerSec(int n, Result r) {
        long savedBits = 32L * n - r.compressedBits;
        if (savedBits <= 0) return Double.POSITIVE_INFINITY; // never worth it
        double compSeconds = (r.compressNs + r.decompressNs) / 1e9;
        return savedBits / compSeconds;
    }
}

