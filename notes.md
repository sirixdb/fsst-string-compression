# BACKGROUND:
Dictionary compression : uniquely matches strings to fixed-size integers.
	- Effective only if repeating strings, i.e. similiar words lose benefit
	- Also if applied to fraction of a whole relation, ineffective
	- Most srings stored are generally less than 200 bytes and often less than 30 bytes per string

LZ4 (dictionary compression example)
	- Not efficient for compressing individual strings - requires kB input size for efficient compression
	- So it's used to compress columnar blocks (many string values together)
	- Therefore prevents random access;
	- Example: decompressing large blocks for these values, some of which goes unused.

Potential:
	- Use in conjunction with dictionary compression - i.e. after data is compressed, FSST can compress the strings in the dictionary
	

# SUMMARY
Terminology:
	- Symbol: 1-8 bytes for a substring
	- Code: 1-byte, represents a compressed symbol.

FSST - Fast Static Symbol Table
	- Compression: replace frequently-occuring substrings of 1-8 bytes with 1-byte codes. Non-frequently-occuring will remain the same.
	- Decompression: translate each 1-byte code into its symbolic substring, using an immutable array table (256 entries)

Decompression Algorithm:
	- Decompress into symbols and store as 8-byte word in array. 

	/** */
	void decodeBasic (int[] in, int[] out, symbolTable, actualLengthOfSymbols){
		int code = *in++; //Dereference to get (*in) before the in pointer is moved forwards.
		*out = sym[code]; //Translate the symbol, cast to 8 byte word and put it into outtput buffer
		out+= len[ccode]; //Moves the pointer head forwards to the new out[0]/next place to write.
	}

	void decodeWithEscape (...) {
		if (code == 255)
			*out++ = *in++; //Copy the escape character.
	}
	/***/ 

Escape Code:
	- Code 255 (Array table is [0,255] ) is used to indicate the following byte in the input is not translated/does not get lookup in the symbol table
	Benefits:
	? Can compress unseen text using an existing symbol table?

Questions ; 256 symbol table...seems like it would only be useful on small pieces of text.
- Does it also do a one sweep first to get symbols??
? "otherwise the escaped input byte would have been included in the symbol table
? Abscense of escapes

Compression:
	findLongestSymbol() finds the longest matching symbol at the current input position. If no matching symbol is found. The input byte is escaped 

Benefits:
- Can apply on existing database systems
- Compressed Query Processing - Can complete equality comparisons on the compressed, without needing decompression
? String Matching - future work


Summary:
	- Symbol table is limited size (256 bytes). So if we can't compress it to a code, the input byte is escaped to just keep the symbol as it is.
	- 


Symbol Table Construction
- Choosing the 2555 symbols
- Naive greedy single-pass: count and pick the most frequent occured. Con: does not consider overlapping symbols ex. (http://w, ttp://www) and if sequential read-in, shorter symbols will be consumed long before the better/longer symbols (h before ttp://w)
- Actual iterative algorithm - Linear time, multiple (ex. 5) iterations, and on-the-fly compression, bottom-up
- Concatenate short symbols to longer symbols
- Multiple iterations update the table, add new symbols, remove bad symbols
- Base case: empty symbol table
- Each iteration:
	1. Iterate over the uncompressed input and compress with existing symbol table, count frequency
	2. Select the highest-gain symbols to construct a new symbol table. Choose from:
		* Old table
		* New symbols generated by concatenating pairs of symbols
		* Reconsider all symbols that consist of a single byte
		* Each existing symbol concatenated with the next occuring byte (even if that single byte is not currently a symbol)
- Ties for gain are resolved randomly for symbols

ITeration 3 -> 4, cwit becomes cwi??


 

