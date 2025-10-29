package com.acme.bitpack;

import static com.acme.bitpack.BitUtils.*;

/**
 on sépare les petites et grandes valeurs.
 - Petites valeurs : stockées directement  
 - Grandes valeurs : envoyées dans une "zone débordement"
 */
public final class BitPackingOverflow extends AbstractBitPacker {
    private final boolean headerCrossing;  // Si le header peut chevaucher les entiers

    // Variables de configuration :
    private int b, m, idxBits, slotW, kOv;// Nombre de bits pour petites valeurs,Nombre de valeurs dans l'overflow,Bits nécessaires pour indexer l'overflow,Largeur d'un slot (1 bit tag + max(b, idxBits)),Bits nécessaires pour les valeurs overflow,Taille totale du header en bits,Slots par entier dans le header
    private long headerBits;
    private int perWordHeader;

    // Ajouter un constructeur avec paramètres n et k
    public BitPackingOverflow(int n, int k, boolean headerCrossing) { 
        super(n, k);
        this.headerCrossing = headerCrossing; 
    }

    @Override public boolean crossesBoundaries() { return headerCrossing; }

    @Override public void compress(int[] input) {
        if (input == null) throw new IllegalArgumentException("input is null");
        n = input.length;
        // 1. Trouver la valeur max pour connaître les bits nécessaires
        int max = 0;
        for (int v : input) {
            if (v < 0) throw new IllegalArgumentException("Negative values not supported by default.");
            max = Math.max(max, v);
        }
        int maxBits = bitsRequiredNonNegative(max);

        // 2. RECHERCHE DE LA MEILLEURE CONFIGURATION
        // On teste différentes tailles de "petites valeurs" pour trouver le meilleur compromis
        long bestCost = Long.MAX_VALUE; int bestB = Math.max(1, maxBits / 2);// Meilleur b trouvé
        int bestM = 0, bestIdx = 0, bestKOv = 0, bestSlotW = 0;
        for (int bb = 1; bb <= Math.max(1, maxBits - 1); bb++) {
            // Calculer combien de valeurs vont dans l'overflow
            int maxSmall = (bb >= 31) ? Integer.MAX_VALUE : ((1 << bb) - 1);
            int mLocal = 0; int kOvLocal = 0;
            // Compter les grandes valeurs et trouver le max des bits nécessaires
            for (int v : input) if (v > maxSmall) { mLocal++; kOvLocal = Math.max(kOvLocal, bitsRequiredNonNegative(v)); }
            int idxLocal = ceilLog2(mLocal); // Bits pour indexer l'overflow
            int slotWLocal = 1 + Math.max(bb, idxLocal); // 1 bit tag + données

            // Calcul du coût total
            long headerCost = (long) n * slotWLocal;
            if (!headerCrossing) {
                int perWord = Math.max(1, 32 / slotWLocal);
                int words = ceilDiv(n, perWord);
                headerCost = (long) words * 32L; // Coût aligné aux entiers
            }
            long cost = headerCost + (long) mLocal * kOvLocal;
            if (cost < bestCost) { bestCost = cost; bestB = bb; bestM = mLocal; bestIdx = idxLocal; bestKOv = kOvLocal; bestSlotW = slotWLocal; }
        }
        // 3. VERIFIER SI LA COMPRESSION OVERFLOW EST UTILE
        long plain = (long) n * maxBits; // Coût sans overflow
        if (bestCost >= plain) {
            // Utiliser le constructeur avec paramètres
            BitPacker delegate = new BitPackingCrossing(n, maxBits);
            delegate.compress(input);
            this.n = delegate.size();
            this.k = delegate.bitsPerValue();
            this.data = delegate.compressed();
            this.b = this.k; this.m = 0; this.idxBits = 0; this.slotW = this.k; this.kOv = 0;
            this.perWordHeader = Math.max(1, 32 / slotW);
            this.headerBits = (long) n * k;
            return;
        }

        // 4. CONFIGURATION FINALE
        this.b = bestB; this.m = bestM; this.idxBits = bestIdx; this.slotW = bestSlotW; this.kOv = bestKOv;
        this.k = slotW;
        this.perWordHeader = Math.max(1, 32 / slotW);
        // Calcul de la taille du header
        if (headerCrossing) this.headerBits = (long) n * slotW;
        else { int words = ceilDiv(n, perWordHeader); this.headerBits = (long) words * 32L; }
        // Allocation du tableau de sortie
        long totalBits = headerBits + (long) m * kOv;
        int wordsTotal = ceilDiv((int) (totalBits & 0x7FFFFFFF), 32);
        data = new int[wordsTotal];
        // 5. COMPRESSION RÉELLE
        int[] overflow = new int[m];
        int maxSmall = (b >= 31) ? Integer.MAX_VALUE : ((1 << b) - 1);
        int writeIdx = 0; // Index dans l'overflow
        // Premier passage : écrire le header
        for (int i = 0; i < n; i++) {
            int v = input[i];
            boolean isOv = v > maxSmall;
            int payloadWidth = slotW - 1;
            int payload;
            if (!isOv) payload = v & mask(b);
            else { payload = writeIdx; overflow[writeIdx++] = v; }
            int tag = isOv ? 1 : 0;
            int slotVal = (payload & mask(payloadWidth)) << 1 | tag;
            long pos = headerCrossing ? (long) i * slotW : headerPosNoCross(i);
            writeBits(slotVal, slotW, pos);
        }
        long q = headerBits;
        for (int i = 0; i < m; i++) { writeBits(overflow[i] & mask(kOv), kOv, q); q += kOv; }
    }

    private long headerPosNoCross(int index) { int word = index / perWordHeader; int slot = index % perWordHeader; return ((long) word * 32L) + ((long) slot * slotW); }

    private void writeBits(int value, int width, long bitPos) {
        int w = (int) (bitPos >>> 5), off = (int) (bitPos & 31);
        data[w] |= (value << off);
        int spill = off + width - 32;
        if (spill > 0) data[w + 1] |= (value >>> (width - spill));
    }

    @Override public void decompress(int[] out) { if (out == null || out.length != n) throw new IllegalArgumentException("out length must be " + n); for (int i = 0; i < n; i++) out[i] = get(i); }

    @Override public int get(int index) {
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException();
        long pos = headerCrossing ? (long) index * slotW : headerPosNoCross(index);
        int slot = readBits(slotW, pos);
        int tag = slot & 1;
        int payload = slot >>> 1;
        if (tag == 0) return payload & mask(b);
        int j = payload & mask(idxBits);
        long start = headerBits + (long) j * kOv;
        return readBits(kOv, start) & mask(kOv);
    }

    private int readBits(int width, long bitPos) {
        int w = (int) (bitPos >>> 5), off = (int) (bitPos & 31);
        int low = data[w] >>> off; int bitsInLow = 32 - off;
        if (width <= bitsInLow) return low & mask(width);
        int hi = data[w + 1] & mask(width - bitsInLow);
        return (low | (hi << bitsInLow)) & mask(width);
    }
}