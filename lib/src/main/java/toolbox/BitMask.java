package toolbox;

public class BitMask {

<<<<<<< HEAD
    private static final int bytes = 64;
=======
>>>>>>> faf420f4cca784c25adc1bb26203e4cfcc3a6d13
    private long[] words;

    // Construct ones
    private static long getOnes(int len) {
<<<<<<< HEAD
        return (~0L) >>> (bytes - len);
=======
        return (~0L) >>> (64 - len);
>>>>>>> faf420f4cca784c25adc1bb26203e4cfcc3a6d13
    }

    // Resize
    public void resize(int size) {
<<<<<<< HEAD
        words = new long[(size + 63) / bytes];
=======
        words = new long[(size + 63) / 64];
>>>>>>> faf420f4cca784c25adc1bb26203e4cfcc3a6d13
    }

    // Mark
    public void mark(int pos, int len) {
        int word = (pos >> 6), ofs = (pos & 63);
<<<<<<< HEAD
        if (ofs + len > bytes) {
            words[word] |= getOnes(bytes - ofs) << ofs;
            words[word + 1] |= getOnes(len - (bytes - ofs));
=======
        if (ofs + len > 64) {
            words[word] |= getOnes(64 - ofs) << ofs;
            words[word + 1] |= getOnes(len - (64 - ofs));
>>>>>>> faf420f4cca784c25adc1bb26203e4cfcc3a6d13
        } else {
            words[word] |= getOnes(len) << ofs;
        }
    }

    // Check if anything in the range is marked
    public boolean isAnyMarked(int pos, int len) {
        int word = (pos >> 6), ofs = (pos & 63);
<<<<<<< HEAD
        if (ofs + len > bytes) {
            return (words[word] & (getOnes(bytes - ofs) << ofs)) != 0 ||
                    (words[word + 1] & getOnes(len - (bytes - ofs))) != 0;
=======
        if (ofs + len > 64) {
            return (words[word] & (getOnes(64 - ofs) << ofs)) != 0 ||
                    (words[word + 1] & getOnes(len - (64 - ofs))) != 0;
>>>>>>> faf420f4cca784c25adc1bb26203e4cfcc3a6d13
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
