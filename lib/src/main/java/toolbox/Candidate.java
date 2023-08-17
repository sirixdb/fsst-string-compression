package toolbox;

/// A symbol candidate
public class Candidate implements Comparable<Candidate> {
    // The symbol
    long symbol;
    // The count
    int count;
    // The gain
    int gain;
    // The position range
    int from, to;
    // The modification step
    int modificationStep;

    // Comparison
    @Override
    public int compareTo(Candidate o) {
        return Integer.compare(gain, o.gain);
    }
}
