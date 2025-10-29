package com.acme.bitpack;

import java.util.Arrays;

/** Bit Packing SANS chevauchement entre les mots (entiers 32 bits) */
public class BitPackingNoCrossing extends AbstractBitPacker {
    private int[] values;  // Stocke les valeurs originales (pour get() facile)

    public BitPackingNoCrossing(int n, int k) {
        super(n, k);
        this.values = new int[n];
    }

    @Override
    public boolean crossesBoundaries() {
        return false; // pas de chevauchement
    }

    @Override
    public void compress(int[] data) {
        if (data.length != n) throw new IllegalArgumentException("Data length mismatch");
        // 1. Sauvegarde les valeurs originales
        System.arraycopy(data, 0, values, 0, n);
         // 2. Reset le tableau de sortie
        Arrays.fill(this.data, 0);
        // 3. Calcule combien de valeurs tiennent dans 1 entier (32 bits)
        int bitsPerInt = 32;
        int valuesPerInt = bitsPerInt / k; // Ex: si k=12 → 32/12=2 valeurs par entier
        int mask = BitUtils.mask(k);       // Masque pour garder seulement k bits
        // 4. Compresse chaque valeur
        for (int i = 0; i < n; i++) {
            int wordIndex = i / valuesPerInt;
            int bitOffset = (i % valuesPerInt) * k;
            // Place la valeur aux bons bits
            this.data[wordIndex] |= (data[i] & mask) << bitOffset;
        }
    }

    @Override
    public int get(int index) {
        if (index < 0 || index >= n) throw new IndexOutOfBoundsException();
        int bitsPerInt = 32;
        int valuesPerInt = bitsPerInt / k;
        int wordIndex = index / valuesPerInt;
        int bitOffset = (index % valuesPerInt) * k;
        int mask = BitUtils.mask(k);
        return (this.data[wordIndex] >>> bitOffset) & mask;
    }

    @Override
    public void decompress(int[] out) {
        if (out.length != n) throw new IllegalArgumentException("Output length mismatch");
        // Décompresse toutes les valeurs
        for (int i = 0; i < n; i++) {
            out[i] = get(i);  // On Utilise get() pour chaque position
        }
    }
}
