package com.acme.bitpack;

import java.util.Locale;
import java.util.Random;

/**
 * Main class - Entry point for the bit packing compression benchmarks
 *
 * This program tests different compression algorithms and measures their performance
 * to determine when compression is beneficial for network transmission.
 */

public final class Main {

     /**
     * Main method - executes compression benchmarks based on command line arguments
     * 
     * Usage: java Main <mode> <n> <valueBits> <seed>
     */

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: Main <mode> <n> <valueBits> <seed>\n"
                    + "  mode: CROSSING | NO_CROSSING | OVERFLOW_CROSSING | OVERFLOW_NO_CROSSING\n"
                    + "  n: number of integers\n"
                    + "  valueBits: each value is uniform in [0, 2^valueBits]\n"
                    + "  seed: RNG seed\n");
            System.exit(2);
        }
        CompressionMode mode = CompressionMode.valueOf(args[0].toUpperCase(java.util.Locale.ROOT));
        int n = parseInt(args[1]);
        int valueBits = parseInt(args[2]);
        long seed = Long.parseLong(args[3]);
        // GENERATE RANDOM TEST DATA
        // Calculate maximum value based on valueBits
        int max = valueBits >= 31 ? Integer.MAX_VALUE : (1 << valueBits) - 1;
        int[] data = new int[n];
        Random rnd = new Random(seed);
        // Fill array with random values in range [0, 2^valueBits]
        for (int i = 0; i < n; i++) {
            data[i] = rnd.nextInt(max + 1);
        }
        // CREATE COMPRESSION ALGORITHM USING FACTORY
        BitPacker packer = BitPackerFactory.create(mode, n, valueBits);
        // RUN BENCHMARKS - test compression performance
        var res = Benchmarks.run(packer, data, Math.min(1_000_000, Math.max(10, n)), seed);
        // NETWORK PARAMETERS (for transmission calculations)
        double B2 = 100e6;  // 100 Mbps network bandwidth
        double t = 0.020;   // 20 ms one-way latency
        // PRINT COMPRESSION RESULTS
        System.out.printf(Locale.ROOT, "Mode=%s, n=%d, k=%d, crossing=%s%n",
                mode, packer.size(), packer.bitsPerValue(), packer.crossesBoundaries());
        System.out.printf(Locale.ROOT, "Compressed size: %.2f KiB (bit-length=%d)%n",
                packer.compressed().length * 4.0 / 1024.0, packer.compressedBitLength());
        System.out.printf(Locale.ROOT, "Times: compress=%.3f ms, get()=%.3f ms, decompress=%.3f ms%n",
                res.compressNs() / 1e6, res.getNs() / 1e6, res.decompressNs() / 1e6);

        System.out.printf(Locale.ROOT, "No-compress total at 20ms, 100 Mbps: %.3f ms%n",
                com.acme.bitpack.Benchmarks.totalSecondsNoCompression(n, B2, t) * 1e3);
        System.out.printf(Locale.ROOT, "With-compress  total at 20ms, 100 Mbps: %.3f ms%n",
                com.acme.bitpack.Benchmarks.totalSecondsWithCompression(res, B2, t) * 1e3);
        System.out.printf(Locale.ROOT, "Break-even bandwidth: %.2f Mbps%n",
                com.acme.bitpack.Benchmarks.breakEvenBandwidthBitsPerSec(n, res) / 1e6);
    }

    private static int parseInt(String s) {
        s = s.replace("_", "");
        return Integer.parseInt(s);
    }
}
