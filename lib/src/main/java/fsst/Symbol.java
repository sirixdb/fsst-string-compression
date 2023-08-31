package fsst;

import java.math.BigInteger;

public class Symbol {
    static final short FSST_CODE_BITS = 9;
    static final short FSST_HASH_LOG2SIZE = 10;
    static final short FSST_CODE_MAX = (short) (long) 1 << FSST_CODE_BITS;
    static final short FSST_CODE_MASK = (short) (FSST_CODE_MAX - (long) 1);
    static final short FSST_LEN_BITS = 12;
    /*
     * first 256 codes [0,255] are pseudo codes: escaped bytes
     */
    static final short FSST_CODE_BASE = (short) (long) 256;
    static final long FSST_HASH_PRIME = 2971215073L;
    static final short FSST_SHIFT = 15;
    static final long FSST_SAMPLETARGET = (long) (1 << 14);
    static final long FSST_SAMPLEMAXSZ = ((long) 2 * FSST_SAMPLETARGET);
    static final long FSST_SAMPLELINE = 512;

    static final int maxLength = 8;
    long value = 0;
    long icl; //ignoredBits:code:length
    int gcl;
    int gain;
    byte[] symbol = new byte[maxLength];

    Symbol() {
        this.icl = 0;
        this.value = 0;

    }

    Symbol(byte c, int code) {
        // TODO: This needs to be checked
        this.icl = (1 << 28) | (code << 16) | 56;
        this.value = c;
    }

    Symbol(char input, int len) {
        this.value = 0;
        if (len >= 8) {
            len = 8;
        }
        this.setCodeLength(FSST_CODE_MAX, len);
    }

    Symbol(char begin, char end) {
        this(begin, (int) end - begin);
    }

    void setCodeLength(int code, int len) {
        this.icl = (len << 28) | (code << 16) | ((8 - len) * 8);
    }

    int length() {
        return (int) (icl >> 28);
    }

    int code() {
        return (int) ((icl >> 16) & FSST_CODE_MASK);
    }

    int ignoredBits() {
        return (int) icl;
    }

    int first() {
        assert (length() >= 1);
        return (int) (0xFF & this.value);
    }

    int first2() {
        assert (length() >= 2);
        return (int) (0xFFFF & this.value);
    }

    Symbol concat(Symbol a, Symbol b) {
        Symbol s;
         length = a.length()+b.length();
   if (length > Symbol::maxLength) length = Symbol::maxLength; 
   s.set_code_len(FSST_CODE_MASK, length);
   s.val.num = (b.val.num << (8*a.length())) | a.val.num;
   return s;
}

    static long FSST_HASH(long w) {
        return ((w * FSST_HASH_PRIME) ^ ((w * FSST_HASH_PRIME) >>> 13));
    }

    public long hash() {
        long v = 0xFFFFFF & this.value;
        return FSST_HASH(v);
    }
}
