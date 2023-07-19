package fsst;

import java.math.BigInteger;
import java.util.Random;

public class SymbolTable {
    static final int hashTabSize = 1 << Symbol.FSST_HASH_LOG2SIZE;
    int[] shortCodes = new int[65536];
    int[] byteCodes = new int[256];
    Symbol[] symbols = new Symbol[Symbol.FSST_CODE_MAX];
    Symbol[] hashTab = new Symbol[hashTabSize];
    int nSymbols; // amount of symbols in the map (max 255)
    int suffixLim; // codes higher than this do not have a longer suffix
    int terminator; // code of 1-byte symbol, that can be used as a terminator during compression
    boolean zeroTerminated; // whether we are expecting zero-terminated strings (we then also produce
                            // zero-terminated compressed strings)
    int lenHisto[] = new int[Symbol.FSST_CODE_BITS]; // lenHisto[x] is the amount of symbols of byte-length (x+1) in
                                                     // this symbolTable

    SymbolTable() {
        this.nSymbols = 0;
        this.suffixLim = Symbol.FSST_CODE_MAX;
        this.terminator = 0;
        this.zeroTerminated = false;
        for (int i = 0; i < 256; i++) {
            symbols[i] = new Symbol(i, i | (1 << Symbol.FSST_LEN_BITS)); // pseudo symbols
        }
        Symbol unused = new Symbol((int) 0, Symbol.FSST_CODE_MASK); // single-char symbol, exception code
        for (int i = 256; i < Symbol.FSST_CODE_MAX; i++) {
            symbols[i] = unused; // we start with all symbols unused
        }
        // empty hash table
        Symbol s = new Symbol();
        s.value = 0;
        s.icl = QSymbol.FSST_ICL_FREE; // marks empty in hashtab
        for (int i = 0; i < hashTabSize; i++)
            hashTab[i] = s;

        // fill byteCodes[] with the pseudo code all bytes (escaped bytes)
        for (int i = 0; i < 256; i++)
            byteCodes[i] = (1 << Symbol.FSST_LEN_BITS) | i;

        // fill shortCodes[] with the pseudo code for the first byte of each two-byte
        // pattern
        for (int i = 0; i < 65536; i++) {
            shortCodes[i] = (1 << Symbol.FSST_LEN_BITS) | (i & 255);
        }
    }

    public void clear() {
        for (int i = Symbol.FSST_CODE_BASE; i < Symbol.FSST_CODE_BASE + nSymbols; i++) {
            if (symbols[i].length() == 1) {
                int val = symbols[i].first();
                byteCodes[val] = (1 << Symbol.FSST_LEN_BITS) | val;
            } else if (symbols[i].length() == 2) {
                int val = symbols[i].first2();
                shortCodes[val] = (1 << Symbol.FSST_LEN_BITS) | (val & 255);
            } else {
                BigInteger tableSize = new BigInteger(String.valueOf(hashTabSize - 1));
                BigInteger idx = symbols[i].hash().and(tableSize);
                hashTab[idx.intValue()].value = 0;
                hashTab[idx.intValue()].icl = QSymbol.FSST_ICL_FREE; // marks empty in hashtab
            }
        }
        nSymbols = 0; // no need to clean symbols[] as no symbols are used
    }

    boolean hashInsert(Symbol s) {
        int idx = s.hash().intValue() & (hashTabSize - 1);
        boolean taken = (hashTab[idx].icl < QSymbol.FSST_ICL_FREE);
        if (taken)
            return false; // collision in hash table
        hashTab[idx].icl = s.icl;
        BigInteger offset = new BigInteger(String.valueOf("0xFFFFFFFFFFFFFFFF"));
        hashTab[idx].value = s.value & (offset.shiftRight((int) s.icl)).intValue();
        return true;
    }

    boolean add(Symbol s) {
        assert (Symbol.FSST_CODE_BASE + nSymbols < Symbol.FSST_CODE_MAX);
        int len = s.length();
        s.setCodeLength(Symbol.FSST_CODE_BASE + nSymbols, len);
        if (len == 1) {
            byteCodes[s.first()] = Symbol.FSST_CODE_BASE + nSymbols + (1 << Symbol.FSST_LEN_BITS); // len=1
                                                                                                   // (<<FSST_LEN_BITS)
        } else if (len == 2) {
            shortCodes[s.first2()] = Symbol.FSST_CODE_BASE + nSymbols + (2 << Symbol.FSST_LEN_BITS); // len=2
                                                                                                     // (<<FSST_LEN_BITS)
        } else if (!hashInsert(s)) {
            return false;
        }
        symbols[Symbol.FSST_CODE_BASE + nSymbols++] = s;
        lenHisto[len - 1]++;
        return true;
    }

    /// Find longest expansion, return code (= position in symbol table)
    int findLongestSymbol(Symbol s) {
        int idx = s.hash().intValue() & (hashTabSize - 1);
        BigInteger offset = new BigInteger("0xFFFFFFFFFFFFFFFF");
        if (hashTab[idx].icl <= s.icl
                && hashTab[idx].value == (s.value & (offset.intValue() >> ((int) hashTab[idx].icl)))) {
            return (int) ((hashTab[idx].icl >> 16) & Symbol.FSST_CODE_MASK); // matched a long symbol
        }
        if (s.length() >= 2) {
            int code = shortCodes[s.first2()] & Symbol.FSST_CODE_MASK;
            if (code >= Symbol.FSST_CODE_BASE)
                return code;
        }
        return byteCodes[s.first()] & Symbol.FSST_CODE_MASK;
    }

    int findLongestSymbol(int cur, int end) {
        Symbol symbol = new Symbol(cur, end);
        return findLongestSymbol(symbol); // represent the string as a temporary symbol
    }

    // rationale for finalize:
    // - during symbol table construction, we may create more than 256 codes, but
    // bring it down to max 255 in the last makeTable()
    // consequently we needed more than 8 bits during symbol table contruction, but
    // can simplify the codes to single bytes in finalize()
    // (this feature is in fact lo longer used, but could still be exploited: symbol
    // construction creates no more than 255 symbols in each pass)
    // - we not only reduce the amount of codes to <255, but also *reorder* the
    // symbols and renumber their codes, for higher compression perf.
    // we renumber codes so they are grouped by length, to allow optimized scalar
    // string compression (byteLim and suffixLim optimizations).
    // - we make the use of byteCode[] no longer necessary by inserting single-byte
    // codes in the free spots of shortCodes[]
    // Using shortCodes[] only makes compression faster. When creating the
    // symbolTable, however, using shortCodes[] for the single-byte
    // symbols is slow, as each insert touches 256 positions in it. This
    // optimization was added when optimizing symbolTable construction time.
    //
    // In all, we change the layout and coding, as follows..
    //
    // before finalize():
    // - The real symbols are symbols[256..256+nSymbols>. As we may have nSymbols >
    // 255
    // - The first 256 codes are pseudo symbols (all escaped bytes)
    //
    // after finalize():
    // - table layout is symbols[0..nSymbols>, with nSymbols < 256.
    // - Real codes are [0,nSymbols>. 8-th bit not set.
    // - Escapes in shortCodes have the 8th bit set (value: 256+255=511). 255
    // because the code to be emitted is the escape byte 255
    // - symbols are grouped by length: 2,3,4,5,6,7,8, then 1 (single-byte codes
    // last)
    // the two-byte codes are split in two sections:
    // - first section contains codes for symbols for which there is no longer
    // symbol (no suffix). It allows an early-out during compression
    //
    // finally, shortCodes[] is modified to also encode all single-byte symbols
    // (hence byteCodes[] is not required on a critical path anymore).
    //
    void finalize(int zeroTerminated) {
        assert (nSymbols <= 255);
        int[] newCode = new int[256];
        int[] rsum = new int[8];
        int byteLim = nSymbols - (lenHisto[0] - zeroTerminated);

        // compute running sum of code lengths (starting offsets for each length)
        rsum[0] = byteLim; // 1-byte codes are highest
        rsum[1] = zeroTerminated;
        for (int i = 1; i < 7; i++)
            rsum[i + 1] = rsum[i] + lenHisto[i];

        // determine the new code for each symbol, ordered by length (and splitting
        // 2byte symbols into two classes around suffixLim)
        suffixLim = rsum[1];
        symbols[newCode[0] = 0] = symbols[256]; // keep symbol 0 in place (for zeroTerminated cases only)

        for (int i = zeroTerminated, j = rsum[2]; i < nSymbols; i++) {
            Symbol s1 = symbols[Symbol.FSST_CODE_BASE + i];
            int len = s1.length();
            int opt = (len == 2) ? 1 : 0 * nSymbols;
            if (opt == 0) {
                int first2 = s1.first2();
                for (int k = 0; k < opt; k++) {
                    Symbol s2 = symbols[Symbol.FSST_CODE_BASE + k];
                    if (k != i && s2.length() > 1 && first2 == s2.first2()) // test if symbol k is a suffix of s
                        opt = 0;
                }
                // By putting 'opt' by itself I think the code was implicitly checking if it was
                // true in the ternary operator
                // so by doing 'opt == 0' I believe we achieve the same effect
                newCode[i] = opt == 0 ? suffixLim++ : --j; // symbols without a larger suffix have a code < suffixLim
            } else
                newCode[i] = rsum[len - 1]++;
            s1.setCodeLength(newCode[i], len);
            symbols[newCode[i]] = s1;
        }
        // renumber the codes in byteCodes[]
        for (int i = 0; i < 256; i++) {
            if ((byteCodes[i] & Symbol.FSST_CODE_MASK) >= Symbol.FSST_CODE_BASE)
                byteCodes[i] = newCode[(int) byteCodes[i]] + (1 << Symbol.FSST_LEN_BITS);
            else
                byteCodes[i] = 511 + (1 << Symbol.FSST_LEN_BITS);
        }
        // renumber the codes in shortCodes[]
        for (int i = 0; i < 65536; i++) {
            if ((shortCodes[i] & Symbol.FSST_CODE_MASK) >= Symbol.FSST_CODE_BASE)
                shortCodes[i] = newCode[(int) shortCodes[i]] + (shortCodes[i] & (15 << Symbol.FSST_LEN_BITS));
            else
                shortCodes[i] = byteCodes[i & 0xFF];
        }

        // replace the symbols in the hash table
        for (int i = 0; i < hashTabSize; i++)
            if (hashTab[i].icl < QSymbol.FSST_ICL_FREE)
                hashTab[i] = symbols[newCode[(int) hashTab[i].code()]];
    }

    boolean isEscapeCode(int pos) {
        return pos < Symbol.FSST_CODE_BASE;
    }

    // TODO: This functionn is technically written as a lambda function inside the
    // buildSymbolTable function
    // Unclear whether it is okay to leave it as a standalone function, but for
    // simplicity's sake leaving it for now.
    int compressCount(SymbolTable symbolTable, Counters counters, Integer[] line, int[] len, int sampleFrac) {
        int gain = 0;
        Random rand = new Random();
        for (int i = 0; i < len.length; i++) {
            int cur = line[i];
            int end = cur + len[i];
            if (sampleFrac < 128) {
                if (rand.nextInt(i) > sampleFrac) {
                    continue;
                }
            }
            if (cur < end) {
                int start = cur;
                int code2 = 255, code1 = symbolTable.findLongestSymbol(cur, end);
                cur += symbolTable.symbols[code1].length();
                gain += (int) (symbolTable.symbols[code1].length() - (1 + Utils.boolToInt(isEscapeCode(code1))));
                while (true) {
                    // count single symbol (i.e. an option is not extending it)
                    // TODO: Double check which boolean flag we need for our specific implementation
                    counters.count1Inc(code1, true);

                    // as an alternative, consider just using the next byte..
                    if (symbolTable.symbols[code1].length() != 1) // .. but do not count single byte symbols doubly
                        // TODO: Double check which boolean flag we need for our specific implementation
                        counters.count1Inc(start, true);

                    if (cur == end) {
                        break;
                    }

                    // now match a new symbol
                    start = cur;
                    if (cur < end - 7) {
                        // NOTE: Technically the c++ code does an unaligned load here, but I'm not sure
                        // how to replicate that or if we even need to
                        double word = cur;
                        int code = (int) word & 0xFFFFFF;
                        int idx = Symbol.FSST_HASH(code).intValueExact() & (hashTabSize - 1);
                        Symbol s = symbolTable.hashTab[idx];
                        long word_bits = Double.doubleToRawLongBits(word);
                        long offset_bits = Double.doubleToRawLongBits(0xFFFF);
                        long res = word_bits & offset_bits;
                        code2 = symbolTable.shortCodes[(int) res] & Symbol.FSST_CODE_MASK;
                        double literal = 0xFFFFFFFFFFFFFFFFL;
                        // Probably unsafe integer cast here
                        int t = ((int) literal >> (int) s.icl);
                        // FIXME: Find a way to integrate this back in
                        // word &= t;
                        if ((s.icl < QSymbol.FSST_ICL_FREE) & (s.value == word)) {
                            code2 = s.code();
                            cur += s.length();
                        } else if (code2 >= Symbol.FSST_CODE_BASE) {
                            cur += 2;
                        } else {
                            // Probably unsafe integer cast here
                            code2 = symbolTable.byteCodes[(int) word & 0xFF] & Symbol.FSST_CODE_MASK;
                            cur += 1;
                        }
                    } else {
                        code2 = symbolTable.findLongestSymbol(cur, end);
                        cur += symbolTable.symbols[code2].length();
                    }

                    // compute compressed output size
                    gain += ((int) (cur - start)) - (1 + Utils.boolToInt(isEscapeCode(code2)));

                    // now count the subsequent two symbols we encode as an extension codesibility
                    if (sampleFrac < 128) { // no need to count pairs in final round
                        // consider the symbol that is the concatenation of the two last symbols
                        counters.count2Inc(code1, code2, true);

                        // as an alternative, consider just extending with the next byte..
                        if ((cur - start) > 1) // ..but do not count single byte extensions doubly
                            counters.count2Inc(code1, start, true);
                    }
                    code1 = code2;
                }
            }
        }
        return gain;
    }

    SymbolTable buildSymbolTable(Counters counters, Integer[] line, int[] len, int zeroTerminated) {
        SymbolTable symbolTable = new SymbolTable();
        SymbolTable best = new SymbolTable();
        int bestGain = (int) -Symbol.FSST_SAMPLEMAXSZ;
        int sampleFrac = -128;
        // XXX: HACK
        symbolTable.zeroTerminated = zeroTerminated == 0;
        if (zeroTerminated == 0) {
            symbolTable.terminator = 0;
        } else {
            int byteHisto[] = new int[256];
            for (int i = 0; i < len.length; i++) {
                int cur = line[i];
                int end = cur + len[i];
                while (cur < end) {
                    byteHisto[cur++]++;
                }
            }
            int minSize = (int) Symbol.FSST_SAMPLEMAXSZ, i = symbolTable.terminator = 256;
            while (i-- > 0) {
                if (byteHisto[i] > minSize)
                    continue;
                symbolTable.terminator = i;
                minSize = byteHisto[i];
            }
        }
        assert (symbolTable.terminator != 256);
        Random rand = new Random();
        int rand128 = rand.nextInt(129);
        int compressCountRet = this.compressCount(this, counters, line, len, sampleFrac);
        // TODO: Implement this method
        // SymbolTable table = this.makeTable(SymbolTable st, Counters counters);
        // https://github.com/cwida/fsst/blob/42850e13ba220dbba5fd721a4c54f969e2a45ac5/libfsst.cpp#L160
        return best;
    }
}
