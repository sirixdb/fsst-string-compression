package toolbox;

import java.util.ArrayList;
import java.util.List;

public class SubsetSelect {
    private List<Byte> data;
    private boolean[] used;

    // Constructor
    public SubsetSelect() {
        data = new ArrayList<>();
        used = new boolean[256];
    }

    // Add a string for statistics computations
    public void add(String s) {
        // Compute used
        for (char c : s.toCharArray()) {
            used[(byte) c & 0xFF] = true;
        }
        if (s.length() < 2) {
            return;
        }

        // Remember the text
        for (char c : s.toCharArray()) {
            data.add((byte) c);
        }
        data.add((byte) 0);
    }

}
