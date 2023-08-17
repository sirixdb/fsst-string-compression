package toolbox;

import java.util.Collections;

import java.util.ArrayList;
import java.util.List;

public class ToolboxUtils {

    public static List<Integer> computeLCP(List<Byte> data, List<Integer> suffixArray) {
        // Compute the longest common prefix array
        // Equivalent to ```inverseSuffixArray.resize(suffixArray.size());```
        List<Integer> inverseSuffixArray = new ArrayList<>(Collections.nCopies(suffixArray.size(), 0));
        for (int index = 0, limit = suffixArray.size(); index != limit; ++index)
            inverseSuffixArray.set(suffixArray.get(index), index);

        List<Integer> lcp = new ArrayList<>(Collections.nCopies(suffixArray.size(), 0));
        int height = 0;
        for (int index = 0, limit = suffixArray.size(); index != limit; ++index) {
            int pos = inverseSuffixArray.get(index);
            if (pos != 0) {
                int index2 = suffixArray.get(pos - 1);
                while ((data.get(index + height) == data.get(index2 + height)) && (data.get(index + height) != 0))
                    ++height;
                lcp.set(pos, height);
                if (height > 0)
                    --height;
            }
        }
        return lcp;
    }

    public static char getSymbolLength(long symbol) {
        // Get the length of a symbol
        return (char) ((char) symbol != 0 ? (8 - (Long.numberOfLeadingZeros(symbol) >> 3)) : 0);
    }

    static int computeGain(int len, int count) {
        int saved = (len - 1) * count;
        return (len != 0 && saved > len) ? (saved - len) : 0;
    }

    static long limitTo(long v, int len) {
        int garbageBits = (8 - len) * 8;
        // NOTE: The "">>>"" unsigned right shift operator is needed
        // to mimic the behavior of the c++ implementation which shifts
        // on an unsigned variable
        return (v << garbageBits) >>> garbageBits;
    }

    static boolean contains0(long v, int len) {
        // Checks if v contains a 0 within a given length
        final long highMask = 0x8080808080808080L;
        final long lowMask = 0x7F7F7F7F7F7F7F7FL;
        long high = v & highMask;
        long couldBe0 = (~(v & lowMask) + lowMask) & highMask;
        return limitTo(couldBe0 & (~high), len) != 0;
    }

    public static void recomputeGain(Candidate c, List<Integer> suffixArray, BitMask modified) {
        // Recompute the gain of an entry
        int len = (int) getSymbolLength(c.symbol); // Assuming getSymbolLen is defined elsewhere
        int invalid = 0;

        for (int i = c.from; i < c.to; i++) {
            invalid += modified.isAnyMarked(suffixArray.get(i), len) ? 1 : 0;
        }

        c.count = (c.to - c.from) - invalid;
        c.gain = computeGain(len, c.count); // Assuming computeGain is defined elsewhere
    }

    public static void invalidatePositions(Candidate c, List<Integer> suffixArray, BitMask modified) {
        // Invalidate all positions
        int len = (int) getSymbolLength(c.symbol); // Assuming getSymbolLen is defined elsewhere

        for (int i = c.from; i < c.to; i++) {
            modified.mark(suffixArray.get(i), len);
        }
    }

}
