package fsst;

import java.util.Arrays;

public class SymbolMap {
    // smallest size that incurs no precision loss
    // lookup table using the next two bytes (65536 codes), or just the next single
    // byte
    final int hashTabSize = 1 << Symbol.FSST_HASH_LOG2SIZE;
    // in the hash table, the gcl field contains (low-to-high)
    // garbageBits:16,code:12,length:4
    // high bits of gcl (len=8,code=FSST_CODE_MASK) indicates free bucket
    static final int FSST_GCL_FREE = ((8 << 28) | (((int) Symbol.FSST_CODE_MASK) << 16));
    short[] shortCodes = new short[65536]; // shortCode[X] contains code for 2-byte symbol, contains 1-byte code X&255
                                           // if there is no 2-byte symbol

    // 'symbols' is the current symbol table symbol[code].symbol is the max 8-byte
    // 'symbol' for single-byte 'code'
    Symbol[] symbols = new Symbol[4096];

    // replicate long symbols in hashTab (avoid indirection).
    Symbol[] hashTab = new Symbol[hashTabSize]; // used for all symbols of 3 and more bytes

    int symbolCount; // amount of symbols in the map (max 4096)
    boolean zeroTerminated; // whether we are expecting zero-terminated strings (we then also produce
                            // zero-terminated compressed strings)
    short[] lenHisto = new short[8]; // lenHisto[x] is the amount of symbols of byte-length (x+1) in this SymbolMap

    public SymbolMap() {
        symbolCount = 256;
        zeroTerminated = false;

        // stuff done once at startup
        Symbol unused = new Symbol(0, Symbol.FSST_CODE_MASK); // single-char symbol, exception code

        for (int i = 0; i < 256; i++) {
            // NOTE: The Java byte type is signed, and casting an integer to a byte in the
            // range of 128 to 255 will result in a negative value. If the Symbol
            // constructor expects values in the range 0 to 255, we will need to special
            // case this within the Symbol constructor
            symbols[i] = new Symbol((byte) i, i); // single-byte symbol
        }
        for (int i = 256; i < 4096; i++) {
            symbols[i] = unused; // all other symbols are unused.
        }

        // stuff done when re-using a symbolmap during the search for the best map
        this.clear(); // clears the arrays (shortCodes and hashTab) and histo
    }

    void clear() {
        Symbol s = new Symbol(); // Assuming a default constructor exists
        s.gcl = FSST_GCL_FREE; // marks empty in hashtab
        s.gain = 0;

        for (int i = 0; i < hashTabSize; i++)
            hashTab[i] = s;

        for (int i = 0; i < 65536; i++)
            shortCodes[i] = (short) (4096 | (i & 255)); // single-byte symbol

        Arrays.fill(lenHisto, (short) 0); // all unused
        lenHisto[0] = (short) (symbolCount = 256); // no need to clean symbols[] as no symbols are used
    }

    int load() {
        int ret = 0;
        for (int i = 0; i < hashTabSize; i++)
            ret += Utils.boolToInt(hashTab[i].gcl < FSST_GCL_FREE);
        return ret;
    }

    boolean hashInsert(Symbol s) {
        // TODO Check that the long conversions are safe and correct and that the int
        // cast is safe
        int idx = (int) (s.hash() & (hashTabSize - 1));
        boolean taken = (hashTab[idx].gcl < FSST_GCL_FREE);
        if (taken)
            return false; // collision in hash table
        hashTab[idx].gcl = s.gcl;
        hashTab[idx].gain = 0;

        // Assuming symbol is represented as a 64-bit long in Java
        long symbolMask = 0xFFFFFFFFFFFFFFFFL >>> (byte) s.gcl;
        for (int i = 0; i < 8; i++) {
            // TODO: Is this cast safe?
            hashTab[idx].symbol[i] = (byte) (s.symbol[i] & symbolMask);
        }

        return true;
    }

    boolean add(Symbol s) {
        if (symbolCount >= 4096) {
            throw new AssertionError();
        }

        int len = s.length();
        if (len <= 1) {
            throw new AssertionError();
        }

        s.setCodeLength(symbolCount, len);

        if (len == 2) {
            if (shortCodes[s.first2()] != (short) (4096 + s.first())) {
                throw new AssertionError(); // cannot be in use
            }
            shortCodes[s.first2()] = (short) (8192 + symbolCount); // 8192 = (len == 2) << 12
        } else if (!hashInsert(s)) {
            return false;
        }

        symbols[symbolCount++] = s;
        lenHisto[len - 1]++;
        return true;
    }

    short hashFind(Symbol s) {
        long idx = s.hash() & (hashTabSize - 1);
        long symbolMask = 0xFFFFFFFFFFFFFFFFL >>> (byte) hashTab[(int) idx].gcl;
        for (int i = 0; i < 8; i++) {
            // TODO: Is this cast safe?
            s.symbol[i] = (byte) (s.symbol[i] & symbolMask);
        }
        if (hashTab[(int) idx].gcl < FSST_GCL_FREE &&
                hashTab[(int) idx].symbol == (s.symbol)) {
            return (short) (hashTab[(int) idx].gcl >>> 16); // matched a long symbol
        }
        return 0;
    }

    short findExpansion(Symbol s) {
        if (s.length() == 1) {
            return (short) (4096 + s.first());
        }
        short ret = hashFind(s);
        return ret != 0 ? ret : shortCodes[s.first2()];
    }

}
