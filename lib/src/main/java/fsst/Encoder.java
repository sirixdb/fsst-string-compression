package fsst;
// an encoder is a symbol-map plus some buffer-space, needed during map construction as well as compression 

public class Encoder {
    static final int FSST_BUFSZ = (3 << 19);
    SymbolTable symbolTable;
    Counters counters;
    int simdbuf[] = new int[FSST_BUFSZ];

}
