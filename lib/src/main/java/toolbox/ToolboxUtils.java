package toolbox;

public class ToolboxUtils {

    public static char getSymolLength(long symbol) {
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

}
