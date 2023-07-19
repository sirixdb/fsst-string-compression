package fsst;
// two phases of compression, before and after optimize():

//
// (1) to encode values we probe (and maintain) three datastructures:
// - u16 byteCodes[65536] array at the position of the next byte (s.length==1)
// - u16 shortCodes[65536] array at the position of the next twobyte pattern
// (s.length==2)
// - Symbol hashtable[1024] (keyed by the next three bytes, ie for s.length>2),
// this search will yield a u16 code, it points into Symbol symbols[]. You
// always find a hit, because the first 256 codes are
// pseudo codes representing a single byte these will become escapes)
//
// (2) when we finished looking for the best symbol table we call optimize() to
// reshape it:
// - it renumbers the codes by length (first symbols of length 2,3,4,5,6,7,8;
// then 1 (starting from byteLim are symbols of length 1)
// length 2 codes for which no longer suffix symbol exists (< suffixLim) come
// first among the 2-byte codes
// (allows shortcut during compression)
// - for each two-byte combination, in all unused slots of shortCodes[], it
// enters the byteCode[] of the symbol corresponding
// to the first byte (if such a single-byte symbol exists). This allows us to
// just probe the next two bytes (if there is only one
// byte left in the string, there is still a terminator-byte added during
// compression) in shortCodes[]. That is, byteCodes[]
// and its codepath is no longer required. This makes compression faster. The
// reason we use byteCodes[] during symbolTable construction
// is that adding a new code/symbol is expensive (you have to touch shortCodes[]
// in 256 places). This optimization was
// hence added to make symbolTable construction faster.
//
// this final layout allows for the fastest compression code, only currently
// present in compressBulk
// in the hash table, the icl field contains (low-to-high)
// ignoredBits:16,code:12,length:4

class QSymbol {
    static final int FSST_SAMPLETARGET = (1 << 14);
    static final int FSST_SAMPLEMAXSZ = (int) ((long) 2 * FSST_SAMPLETARGET);
    // high bits of icl (len=8,code=FSST_CODE_MASK) indicates free bucket
    static final int FSST_ICL_FREE = ((15 << 28) | (((int) Symbol.FSST_CODE_MASK) << 16));

    // ignoredBits is (8-length)*8, which is the amount of high bits to zero in the
    // input word before comparing with the hashtable key
    // ..it could of course be computed from len during lookup, but storing it
    // precomputed in some loose bits is faster
    //
    // the gain field is only used in the symbol queue that sorts symbols on gain

    Symbol symbol;
    int gain;

    @Override
    public boolean equals(Object o) {
        // If the object is compared with itself then return true
        if (o == this) {
            return true;
        }

        /*
         * Check if o is an instance of Complex or not
         * "null instanceof [type]" also returns false
         */
        if (!(o instanceof QSymbol)) {
            return false;
        }
        QSymbol other = (QSymbol) o;
        return this.symbol.value == other.symbol.value && this.symbol.length() == other.symbol.length();
    }

}
