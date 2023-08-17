package toolbox.Map;

import java.util.Collections;
import java.util.Vector;

import toolbox.ToolboxUtils;

public class SymbolMap {
    Vector<Entry> entries;
    char[] table = new char[257];

    SymbolMap() {
    }

    void add(long symbol, char c) {
        char len = ToolboxUtils.getSymolLength(symbol);
        if (len > 1) {
            Entry entry = new Entry(symbol, c, len);
            entries.add(entry);
        }
    }

    void buildTable() {
        Collections.sort(entries, (a, b) -> Long.compare(a.symbol & 0xFF, b.symbol & 0xFF));
        int current = 0;
        table[0] = 0;
        for (int index = 0, limit = entries.size(); index != limit; ++index) {
            int v = (int) (entries.get(index).symbol & 0xFF);
            if (v != current) {
                for (int index2 = current + 1; index2 < v; ++index2)
                    table[index2] = (char) index;
                table[v] = (char) index;
                current = v;
            }
        }
        for (int index2 = current + 1; index2 <= 256; ++index2)
            table[index2] = (char) entries.size();
        for (int i = 0; i < 256; i++) {
            Collections.sort(entries.subList(table[i], table[i + 1]), (a, b) -> Character.compare(b.len, a.len));
        }
    }

}
