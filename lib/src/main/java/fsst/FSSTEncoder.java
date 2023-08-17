package fsst;

import java.util.ArrayList;
import java.util.Arrays;

public class FSSTEncoder {
    static final long FSST_ENDIAN_MARKER = 1L;
    static final long FSST_VERSION_20190218 = 20190218L;
    // static final long FSST_VERSION;

    SymbolTable symbolTable;
    Counters counters;
    int[] simdBuffer = new int[3 << 19];

/** */
    FSSTEncoder() {
    }

// TODO: Ask about string arrays instead of this char arrays.
   /** Calibrate a FSST symbol table from a batch of strings (it is best to provide at least 16KB of data). */
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

    /** Create another FSSTEncoder instance, necessary to do multi-threaded encoding using the same symbol table.
     * 
     * @param symbolTable table to duplicate.
    */
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
            int rand = Symbol.FSST_HASH(4637947).intValueExact();
            int sampleLimit = (int) (sampleBuffer.length + Symbol.FSST_SAMPLETARGET);
            int[] sampleLen = new int[(int) (lineCount + Symbol.FSST_SAMPLEMAXSZ / Symbol.FSST_SAMPLELINE)];
            lengthArray = sampleLen;
            while (sampleBuffer.length < sampleLimit) {
                // choose a non-empty line
                rand = Symbol.FSST_HASH(rand).intValueExact();
                int linenr = rand % lineCount;
                while (lengthArray[linenr] == 0) {
                    if (++linenr == lineCount) {
                        linenr = 0;
                    }
                }

                // choose a chunk
                int chunks = (int) (1 + ((lengthArray[linenr] - 1) / Symbol.FSST_SAMPLELINE));
                rand = Symbol.FSST_HASH(rand).intValueExact();
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

}
