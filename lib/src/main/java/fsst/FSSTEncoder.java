package fsst;

import java.util.ArrayList;
import java.util.Arrays;

public class FSSTEncoder {
    static final long FSST_ENDIAN_MARKER = (long) 1;
    static final long FSST_VERSION_20190218 = 20190218;
    static final long FSST_VERSION = ((long) FSST_VERSION_20190218);

    SymbolTable symbolTable;
    Counters counters;
    int[] simdBuffer = new int[3 << 19];

    FSSTEncoder() {
    }

    FSSTEncoder(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
    }

    static ArrayList<Integer> makeSample(int[] sampleBuffer, int[] inputString, int[] lengthArray, int lineCount) {
        int totalSize = 0;
        ArrayList<Integer> samples = new ArrayList<Integer>();
        for (int i = 0; i < lineCount; i++) {
            totalSize += lengthArray[i];
        }
        if (totalSize < Symbol.FSST_SAMPLETARGET) {
            for (int i = 0; i < lineCount; i++) {
                samples.add(inputString[i]);
            }
        } else {
            // TODO Check that the long conversions are safe and correct and that the cast
            // to int is safe
            long rand = Symbol.FSST_HASH(4637947);
            int sampleLimit = (int) (sampleBuffer.length + Symbol.FSST_SAMPLETARGET);
            int[] sampleLen = new int[(int) (lineCount + Symbol.FSST_SAMPLEMAXSZ / Symbol.FSST_SAMPLELINE)];
            lengthArray = sampleLen;
            while (sampleBuffer.length < sampleLimit) {
                // choose a non-empty line
                rand = Symbol.FSST_HASH(rand);
                int linenr = (int) (rand % lineCount);
                while (lengthArray[linenr] == 0) {
                    if (++linenr == lineCount) {
                        linenr = 0;
                    }
                }

                // choose a chunk
                int chunks = (int) (1 + ((lengthArray[linenr] - 1) / Symbol.FSST_SAMPLELINE));
                rand = Symbol.FSST_HASH(rand);
                int chunk = (int) (Symbol.FSST_SAMPLELINE * (rand % chunks));

                // add the chunk to the sample
                int len = Math.min(lengthArray[linenr] - chunk, (int) Symbol.FSST_SAMPLELINE);
                // Memcpy approximation I think
                Arrays.fill(sampleBuffer, inputString[linenr] + chunk);
                for (int i = 0; i < sampleBuffer.length; i++) {
                    Integer value = Integer.valueOf(sampleBuffer[i]);
                    samples.add(value);
                }
                for (int i = 0; i < sampleLen.length; i++) {
                    sampleLen[i]++;
                }
                assert (sampleLen.length == sampleBuffer.length);
                for (int i = 0; i < sampleLen.length; i++) {
                    sampleBuffer[i] += sampleLen[i];
                }
            }
        }
        return samples;
    }

    FSSTEncoder(int n, int[] inputLength, char[] inputString, int zeroTerminated) {
        int[] sampleBuffer = new int[(int) Symbol.FSST_SAMPLEMAXSZ];
        int[] sampleLen = inputLength;
        Object[] sample = makeSample(simdBuffer, sampleBuffer, sampleLen, n == 0 ? n : 1).toArray();
        FSSTEncoder encoder = new FSSTEncoder();
        SymbolTable symbolTable = new SymbolTable();
        encoder.symbolTable = symbolTable.buildSymbolTable(encoder.counters, (Integer[]) sample, sampleLen,
                zeroTerminated);
        if (sampleLen != inputLength) {
            // TODO: There might be a better way of doing the delete operator as the c++
            // code is doing but this is my current closest guess
            Arrays.fill(sampleLen, 0);
        }
        // TODO: There might be a better way of doing the delete operator as the c++
        // code is doing but this is my current closest guess
        Arrays.fill(sampleBuffer, 0);
    }

    FSSTEncoder duplicate() {
        FSSTEncoder duplicate = new FSSTEncoder(this.symbolTable);
        return duplicate;
    }

    int export(long buffer) {
        // TODO: Incomplete
        // In ->version there is a versionnr, but we hide also
        // suffixLim/terminator/nSymbols there.
        // This is sufficient in principle to *reconstruct* a fsst_encoder_t from a
        // fsst_decoder_t
        // (such functionality could be useful to append compressed data to an existing
        // block).
        //
        // However, the hash function in the encoder hash table is endian-sensitive, and
        // given its
        // 'lossy perfect' hashing scheme is *unable* to contain other-endian-produced
        // symbol tables.
        // Doing a endian-conversion during hashing will be slow and self-defeating.
        //
        // Overall, we could support reconstructing an encoder for incremental
        // compression, but
        // should enforce equal-endianness. Bit of a bummer. Not going there now.
        //
        // The version field is now there just for future-proofness, but not used yet

        // version allows keeping track of fsst versions, track endianness, and encoder
        // reconstruction
        Encoder e = new Encoder();
        long version = (FSST_VERSION << 32) | // version is 24 bits, most significant byte is 0
                (((long) e.symbolTable.suffixLim) << 24) |
                (((long) e.symbolTable.terminator) << 16) |
                (((long) e.symbolTable.nSymbols) << 8) |
                FSST_ENDIAN_MARKER; // least significant byte is nonzero
        buffer = version;
        return 0;
    }

    // Inline methods in C++ can be made private in Java if they're only used within
    // the class
    private long _compressAuto(long nlines, long[] lenIn, byte[][] strIn, long size, byte[] output,
            long[] lenOut, byte[][] strOut, int simd) {
        boolean avoidBranch = false, noSuffixOpt = false;

        if (100 * this.symbolTable.lenHisto[1] > 65 * this.symbolTable.nSymbols
                && 100 * this.symbolTable.suffixLim > 95 * this.symbolTable.lenHisto[1]) {
            noSuffixOpt = true;
        } else if ((this.symbolTable.lenHisto[0] > 24 && this.symbolTable.lenHisto[0] < 92) &&
                (this.symbolTable.lenHisto[0] < 43 || this.symbolTable.lenHisto[6] + this.symbolTable.lenHisto[7] < 29)
                &&
                (this.symbolTable.lenHisto[0] < 72 || this.symbolTable.lenHisto[2] < 72)) {
            avoidBranch = true;
        }

        return _compressImpl(nlines, lenIn, strIn, size, output, lenOut, strOut, noSuffixOpt, avoidBranch, simd);
    }

    private long _compressImpl(long nlines, long[] lenIn, byte[][] strIn, long size, byte[] output, long[] lenOut,
            byte[][] strOut, boolean noSuffixOpt, boolean avoidBranch, int simd) {
        return compressBulk(this.symbolTable, nlines, lenIn, strIn, size, output, lenOut, strOut, noSuffixOpt,
                avoidBranch);

    }

    public long compressBulk(SymbolTable symbolTable2, long nlines, long[] lenIn, byte[][] strIn, long size,
            byte[] output, long[] lenOut, byte[][] strOut, boolean noSuffixOpt, boolean avoidBranch) {
        return 0;
    }

    public long fsst_compress(
            int nlines,
            long[] lenIn,
            byte[][] strIn,
            long size,
            byte[] output,
            long[] lenOut,
            byte[][] strOut) {

        long totLen = 0;
        for (int i = 0; i < nlines; i++) {
            totLen += lenIn[i];
        }

        boolean simd = totLen > nlines * 12 && (nlines > 64 || totLen > (long) 1 << 15);
        return this._compressAuto(nlines, lenIn, strIn, size, output, lenOut, strOut, 3 * (simd ? 1 : 0));
    }

    public static int compressBulk(SymbolTable symbolTable, int nlines, int[] lenIn, byte[][] strIn, int size,
            byte[] out, int[] lenOut, byte[][] strOut, boolean noSuffixOpt, boolean avoidBranch) {
        byte[] cur = null;
        byte[] end = null;
        byte[] lim = new byte[out.length + size];

        int curLine;
        byte[] buf = new byte[512 + 8];

        int outPointer = 0;

        for (curLine = 0; curLine < nlines; curLine++) {
            int chunk, curOff = 0;
            strOut[curLine] = out;

            do {
                cur = Arrays.copyOfRange(strIn[curLine], curOff, strIn[curLine].length);
                chunk = lenIn[curLine] - curOff;

                if (chunk > 511) {
                    chunk = 511;
                }

                if (2 * chunk + 7 > lim.length - outPointer) {
                    return curLine;
                }

                System.arraycopy(cur, 0, buf, 0, chunk);
                buf[chunk] = (byte) symbolTable.terminator;
                end = Arrays.copyOfRange(buf, 0, chunk);

                // Java doesn't have lambdas like C++ does (not in the same way), so we'll call
                // a function instead.
                if (noSuffixOpt) {
                    outPointer = compressVariant(cur, end, out, outPointer, symbolTable, true, false);
                } else if (avoidBranch) {
                    outPointer = compressVariant(cur, end, out, outPointer, symbolTable, false, true);
                } else {
                    outPointer = compressVariant(cur, end, out, outPointer, symbolTable, false, false);
                }

            } while ((curOff += chunk) < lenIn[curLine]);
            lenOut[curLine] = outPointer;
        }

        return curLine;
    }

    private static int compressVariant(byte[] cur, byte[] end, byte[] out, int outPointer, SymbolTable symbolTable,
            boolean noSuffixOpt, boolean avoidBranch) {
        byte byteLim = (byte) (symbolTable.nSymbols + (symbolTable.zeroTerminated ? 1 : 0) - symbolTable.lenHisto[0]);
        while (cur.length < end.length) {
            long word = Utils.fsst_unaligned_load(cur, 0);
            int code = symbolTable.shortCodes[(int) (word & 0xFFFF)];
            if (noSuffixOpt && (byte) code < symbolTable.suffixLim) {
                out[outPointer++] = (byte) code;
                cur = Arrays.copyOfRange(cur, 2, cur.length);
            } else {
                int pos = (int) (word & 0xFFFFFF);
                int idx = (int) (Symbol.FSST_HASH(pos) & (symbolTable.hashTabSize - 1));
                Symbol s = symbolTable.hashTab[idx];
                out[outPointer + 1] = (byte) word;
                word &= (0xFFFFFFFFFFFFFFFFL >> (byte) s.icl);
                if (s.icl < QSymbol.FSST_ICL_FREE && s.value == word) {
                    out[outPointer++] = (byte) s.code();
                    cur = Arrays.copyOfRange(cur, s.length(), cur.length);
                } else if (avoidBranch) {
                    out[outPointer] = (byte) code;
                    outPointer += 1 + ((code & Symbol.FSST_CODE_BASE) >> 8);
                    cur = Arrays.copyOfRange(cur, code >> Symbol.FSST_LEN_BITS, cur.length);
                } else if ((byte) code < byteLim) {
                    out[outPointer++] = (byte) code;
                    cur = Arrays.copyOfRange(cur, 2, cur.length);
                } else {
                    out[outPointer] = (byte) code;
                    outPointer += 1 + ((code & Symbol.FSST_CODE_BASE) >> 8);
                    cur = Arrays.copyOfRange(cur, 1, cur.length);
                }
            }
        }
        return outPointer;
    }

}
