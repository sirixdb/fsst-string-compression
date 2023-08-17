package toolbox;

public class BitMask {

    private long[] words;

    // Construct ones
    private static long getOnes(int len) {
        return (~0L) >>> (64 - len);
    }

    // Resize
    public void resize(int size) {
        words = new long[(size + 63) / 64];
    }

    // Mark
    public void mark(int pos, int len) {
        int word = (pos >> 6), ofs = (pos & 63);
        if (ofs + len > 64) {
            words[word] |= getOnes(64 - ofs) << ofs;
            words[word + 1] |= getOnes(len - (64 - ofs));
        } else {
            words[word] |= getOnes(len) << ofs;
        }
    }

    // Check if anything in the range is marked
    public boolean isAnyMarked(int pos, int len) {
        int word = (pos >> 6), ofs = (pos & 63);
        if (ofs + len > 64) {
            return (words[word] & (getOnes(64 - ofs) << ofs)) != 0 ||
                    (words[word + 1] & getOnes(len - (64 - ofs))) != 0;
        } else {
            return (words[word] & (getOnes(len) << ofs)) != 0;
        }
    }

    // Return the unmarked entries
    public int getUnmarked(int pos, int len) {
        int word = (pos >> 6), ofs = (pos & 63);
        if (ofs + len > 64) {
            return len - (Long.bitCount(words[word] & (getOnes(64 - ofs) << ofs))
                    + Long.bitCount(words[word + 1] & getOnes(len - (64 - ofs))));
        } else {
            return len - Long.bitCount(words[word] & (getOnes(len) << ofs));
        }
    }
}
