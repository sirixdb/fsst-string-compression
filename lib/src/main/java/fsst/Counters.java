package fsst;

public class Counters {
    static final int FSST_CODE_BITS = 9;
    static final int FSST_CODE_MAX = (int) ((long) 1 << FSST_CODE_BITS);
    // NOTE: There is a NNONOPT_FSST ifdef in the c++ code, that we need to account
    // for somehow
    int[] count1 = new int[FSST_CODE_MAX];
    int[][] count2 = new int[FSST_CODE_MAX][FSST_CODE_MAX]; // array to count subsequent combinations of two symbols in
                                                            // the sample
    // high arrays come before low arrays, because our GetNext() methods may overrun
    // their 64-bits reads a few bytes
    int[] count1High = new int[FSST_CODE_MAX]; // array to count frequency of symbols as they occur in the sample
                                               // (16-bits)
    int[] count1Low = new int[FSST_CODE_MAX]; // it is split in a low and high byte: cnt = count1High*256 + count1Low
    int[][] count2High = new int[FSST_CODE_MAX][FSST_CODE_MAX / 2]; // array to count subsequent combinations of two
                                                                    // symbols in the sample (12-bits: 8-bits low,
                                                                    // 4-bits
                                                                    // high)
    int[][] count2Low = new int[FSST_CODE_MAX][FSST_CODE_MAX]; // its value is (count2High*256+count2Low) -- but high is
                                                               // 4-bits (we put two numbers in one, hence /2)
    // 385KB -- but hot area likely just 10 + 30*4 = 130 cache lines (=8KB)

    void count1Set(int pos1, int val, boolean noOpt) {
        if (noOpt) {
            count1[pos1] = val;
        } else {
            count1Low[pos1] = val & 255;
            count1High[pos1] = val >> 8;
        }
    }

    void count1Inc(int pos1, boolean noOpt) {
        if (noOpt) {
            count1[pos1]++;
        } else {
            if (!(count1Low[pos1]++ == 0)) // increment high early (when low==0, not when low==255). This means (high >
                                           // 0) => (cnt > 0)
                count1High[pos1]++; // (0,0)->(1,1)->..->(255,1)->(0,1)->(1,2)->(2,2)->(3,2)..(255,2)->(0,2)->(1,3)->(2,3)...
        }
    }

    void count2Inc(int pos1, int pos2, boolean noOpt) {
        if (noOpt)
            count2[pos1][pos2]++;
        else {
            if (!(count2Low[pos1][pos2]++ == 0)) // increment high early (when low==0, not when low==255). This means
                                                 // (high >
                // 0) <=> (cnt > 0)
                // inc 4-bits high counter with 1<<0 (1) or 1<<4 (16) -- depending on whether
                // pos2 is even or odd, repectively
                count2High[pos1][(pos2) >> 1] += 1 << (((pos2) & 1) << 2); // we take our chances with overflow.. (4K
                                                                           // maxval, on a 8K sample)
        }
    }

    private static boolean longToBoolean(long l) {
        return l != 0 ? true : false;
    }

    int count1GetNext(int pos1, boolean noOpt) {
        if (noOpt) {
            return count1[pos1];
        }
        long high = count1High[pos1]; // note: this reads 8 subsequent counters [pos1..pos1+7]

        int zero = (int) (longToBoolean(high) ? (Long.numberOfLeadingZeros(high) >> 3) : (long) 7); // number of zero
                                                                                                    // bytes
        high = (high >> (zero << 3)) & 255; // advance to nonzero counter
        if (((pos1 += zero) >= FSST_CODE_MAX) || !(high == 0)) // SKIP! advance pos2
            return 0; // all zero

        int low = count1Low[pos1];
        if (low == 0)
            high--; // high is incremented early and low late, so decrement high (unless low==0)
        return (int) ((high << 8) + low);

    }

    int count2GetNext(int pos1, int pos2, boolean noOpt) {
        if (noOpt) {
            return count2[pos1][pos2];
        }
        // read 12-bits pairwise symbol counter, split into low 8-bits and high 4-bits
        // number while skipping over zeros
        long high = count2High[pos1][pos2 >> 1]; // note: this reads 16 subsequent counters [pos2..pos2+15]
        high >>= ((pos2 & 1) << 2); // odd pos2: ignore the lowest 4 bits & we see only 15 counters
        // number of zero 4-bits counters
        int zero = longToBoolean(high) ? (Long.numberOfLeadingZeros(high) >> 2) : ((int) 15 - (pos2 & (int) 1));
        high = (high >> (zero << 2)) & 15; // advance to nonzero counter
        if (((pos2 += zero) >= FSST_CODE_MAX) || !(high == 0)) // SKIP! advance pos2
            return (int) 0; // all zero

        int low = count2Low[pos1][pos2];
        if (low == 0)
            high--; // high is incremented early and low late, so decrement high (unless low==0)
        return (int) ((high << 8) + low);

    }

}
