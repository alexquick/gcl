package gcl;

import java.io.FileWriter;
import java.io.Writer;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;

class Token {
	int kind;    // token kind
	int pos;     // token position in the source text (starting at 0)
	int col;     // token column (starting at 1)
	int line;    // token line (starting at 1)
	String val;  // token value
	Token next;  // ML 2005-03-11 Peek tokens are kept in linked list
	public String spelling(){ return val;}
	public int line() { return line;}
	public int column() { return col;}
}

//-----------------------------------------------------------------------------------
// Buffer
//-----------------------------------------------------------------------------------
class Buffer {
	// This Buffer supports the following cases:
	// 1) seekable stream (file)
	//    a) whole stream in buffer
	//    b) part of stream in buffer
	// 2) non seekable stream (network, console)

	public static final int EOF = Character.MAX_VALUE + 1;
	private static final int MIN_BUFFER_LENGTH = 1024; // 1KB
	private static final int MAX_BUFFER_LENGTH = MIN_BUFFER_LENGTH * 64; // 64KB
	private byte[] buf;   // input buffer
	private int bufStart; // position of first byte in buffer relative to input stream
	private int bufLen;   // length of buffer
	private int fileLen;  // length of input stream (may change if stream is no file)
	private int bufPos;      // current position in buffer
	private RandomAccessFile file; // input stream (seekable)
	private InputStream stream; // growing input stream (e.g.: console, network)
	Scanner scanner;

	public Buffer(InputStream s, Scanner scanner) {
		this.scanner = scanner;
		stream = s;
		fileLen = bufLen = bufStart = bufPos = 0;
		buf = new byte[MIN_BUFFER_LENGTH];
	}

	public Buffer(String fileName, Scanner scanner) {
		this.scanner = scanner;
		try {
			file = new RandomAccessFile(fileName, "r");
			fileLen = (int) file.length();
			bufLen = Math.min(fileLen, MAX_BUFFER_LENGTH);
			buf = new byte[bufLen];
			bufStart = Integer.MAX_VALUE; // nothing in buffer so far
			if (fileLen > 0) setPos(0); // setup buffer to position 0 (start)
			else bufPos = 0; // index 0 is already after the file, thus setPos(0) is invalid
			if (bufLen == fileLen) Close();
		} catch (IOException e) {
			throw new FatalError("Could not open file " + fileName);
		}
	}

	// don't use b after this call anymore
	// called in UTF8Buffer constructor
	protected Buffer(Buffer b, Scanner scanner) {
		this.scanner = scanner;
		buf = b.buf;
		bufStart = b.bufStart;
		bufLen = b.bufLen;
		fileLen = b.fileLen;
		bufPos = b.bufPos;
		file = b.file;
		stream = b.stream;
		// keep finalize from closing the file
		b.file = null;
	}

	protected void finalize() throws Throwable {
		super.finalize();
		Close();
	}

	protected void Close() {
		if (file != null) {
			try {
				file.close();
				file = null;
			} catch (IOException e) {
				throw new FatalError(e.getMessage());
			}
		}
	}

	static boolean showLine = false;

	public int Read() {
		if (bufPos < bufLen) {
			final char eol = '\n'; // jbergin 10/05
			if (showLine) // Assumes dos end lines. 
			{	int i = bufPos + 1, j;
				int size = 0;
				for(;i < bufLen && buf[i] != eol; ++i) size++;
				char s [] = new char[size + 1];
				for(i = bufPos,j = 0; i < bufLen && buf[i] != eol; i++, j++) 	
					s[j] = (char)buf[i];
				scanner.outFile().println(s);
				showLine = false;
			}
			if(buf[bufPos] == eol) showLine = true;
			return buf[bufPos++] & 0xff;  // mask out sign bits
		} else if (getPos() < fileLen) {
			setPos(getPos());         // shift buffer start to pos
			return buf[bufPos++] & 0xff; // mask out sign bits
		} else if (stream != null && ReadNextStreamChunk() > 0) {
			return buf[bufPos++] & 0xff;  // mask out sign bits
		} else {
			return EOF;
		}
	}

	public int Peek() {
		int curPos = getPos();
		int ch = Read();
		setPos(curPos);
		return ch;
	}

	public String GetString(int beg, int end) {
	    int len = end - beg;
	    char[] buf = new char[len];
	    int oldPos = getPos();
	    setPos(beg);
	    for (int i = 0; i < len; ++i) buf[i] = (char) Read();
	    setPos(oldPos);
	    return new String(buf);
	}

	public int getPos() {
		return bufPos + bufStart;
	}

	public void setPos(int value) {
		if (value >= fileLen && stream != null) {
			// Wanted position is after buffer and the stream
			// is not seek-able e.g. network or console,
			// thus we have to read the stream manually till
			// the wanted position is in sight.
			while (value >= fileLen && ReadNextStreamChunk() > 0);
		}

		if (value < 0 || value > fileLen) {
			throw new FatalError("buffer out of bounds access, position: " + value);
		}

		if (value >= bufStart && value < bufStart + bufLen) { // already in buffer
			bufPos = value - bufStart;
		} else if (file != null) { // must be swapped in
			try {
				file.seek(value);
				bufLen = file.read(buf);
				bufStart = value; bufPos = 0;
			} catch(IOException e) {
				throw new FatalError(e.getMessage());
			}
		} else {
			// set the position to the end of the file, Pos will return fileLen.
			bufPos = fileLen - bufStart;
		}
	}
	
	// Read the next chunk of bytes from the stream, increases the buffer
	// if needed and updates the fields fileLen and bufLen.
	// Returns the number of bytes read.
	private int ReadNextStreamChunk() {
		int free = buf.length - bufLen;
		if (free == 0) {
			// in the case of a growing input stream
			// we can neither seek in the stream, nor can we
			// foresee the maximum length, thus we must adapt
			// the buffer size on demand.
			byte[] newBuf = new byte[bufLen * 2];
			System.arraycopy(buf, 0, newBuf, 0, bufLen);
			buf = newBuf;
			free = bufLen;
		}
		
		int read;
		try { read = stream.read(buf, bufLen, free); }
		catch (IOException ioex) { throw new FatalError(ioex.getMessage()); }
		
		if (read > 0) {
			fileLen = bufLen = (bufLen + read);
			return read;
		}
		// end of stream reached
		return 0;
	}
}

//-----------------------------------------------------------------------------------
// UTF8Buffer
//-----------------------------------------------------------------------------------
class UTF8Buffer extends Buffer {
	UTF8Buffer(Buffer b, Scanner scanner) { super(b, scanner); }

	public int Read() {
		int ch;
		do {
			ch = super.Read();
			// until we find a utf8 start (0xxxxxxx or 11xxxxxx)
		} while ((ch >= 128) && ((ch & 0xC0) != 0xC0) && (ch != EOF));
		if (ch < 128 || ch == EOF) {
			// nothing to do, first 127 chars are the same in ascii and utf8
			// 0xxxxxxx or end of file character
		} else if ((ch & 0xF0) == 0xF0) {
			// 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
			int c1 = ch & 0x07; ch = super.Read();
			int c2 = ch & 0x3F; ch = super.Read();
			int c3 = ch & 0x3F; ch = super.Read();
			int c4 = ch & 0x3F;
			ch = (((((c1 << 6) | c2) << 6) | c3) << 6) | c4;
		} else if ((ch & 0xE0) == 0xE0) {
			// 1110xxxx 10xxxxxx 10xxxxxx
			int c1 = ch & 0x0F; ch = super.Read();
			int c2 = ch & 0x3F; ch = super.Read();
			int c3 = ch & 0x3F;
			ch = (((c1 << 6) | c2) << 6) | c3;
		} else if ((ch & 0xC0) == 0xC0) {
			// 110xxxxx 10xxxxxx
			int c1 = ch & 0x1F; ch = super.Read();
			int c2 = ch & 0x3F;
			ch = (c1 << 6) | c2;
		}
		return ch;
	}
}

//-----------------------------------------------------------------------------------
// StartStates  -- maps characters to start states of tokens
//-----------------------------------------------------------------------------------
class StartStates {
	private static class Elem {
		public int key, val;
		public Elem next;
		public Elem(int key, int val) { this.key = key; this.val = val; }
	}

	private Elem[] tab = new Elem[128];

	public void set(int key, int val) {
		Elem e = new Elem(key, val);
		int k = key % 128;
		e.next = tab[k]; tab[k] = e;
	}

	public int state(int key) {
		Elem e = tab[key % 128];
		while (e != null && e.key != key) e = e.next;
		return e == null ? 0: e.val;
	}
}

//-----------------------------------------------------------------------------------
// Scanner
//-----------------------------------------------------------------------------------
public class Scanner {
	static final char EOL = '\n';
	static final int  eofSym = 0;
	static final int maxT = 35;
	static final int noSym = 35;


	private PrintWriter out;
	public Buffer buffer; // scanner buffer

	Token t;           // current token
	int ch;            // current input character
	int pos;           // byte position of current character
	int col;           // column number of current character
	int line;          // line number of current character
	int oldEols;       // EOLs that appeared in a comment;
	static final StartStates start; // maps initial token character to start state
	static final Map<String, Integer>  literals; // maps literal strings to literal kinds

	Token tokens;      // list of tokens already peeked (first token is a dummy)
	Token pt;          // current peek token
	
	char[] tokenText = new char[16]; // token text used in NextToken(), dynamically enlarged
	
	static {
		start = new StartStates();
		literals = new HashMap<String, Integer> ();
		for (int i = 65; i <= 90; ++i) start.set(i, 1);
		for (int i = 97; i <= 122; ++i) start.set(i, 1);
		for (int i = 48; i <= 57; ++i) start.set(i, 2);
		start.set(36, 13); 
		start.set(46, 16); 
		start.set(59, 17); 
		start.set(44, 18); 
		start.set(91, 33); 
		start.set(93, 19); 
		start.set(58, 20); 
		start.set(45, 34); 
		start.set(43, 24); 
		start.set(40, 25); 
		start.set(41, 26); 
		start.set(61, 27); 
		start.set(35, 28); 
		start.set(60, 35); 
		start.set(62, 36); 
		start.set(42, 31); 
		start.set(47, 32); 
		start.set(Buffer.EOF, -1);
		literals.put("module", new Integer(3));
		literals.put("private", new Integer(4));
		literals.put("begin", new Integer(6));
		literals.put("end", new Integer(7));
		literals.put("integer", new Integer(10));
		literals.put("Boolean", new Integer(11));
		literals.put("tuple", new Integer(12));
		literals.put("skip", new Integer(15));
		literals.put("read", new Integer(16));
		literals.put("write", new Integer(17));
		literals.put("if", new Integer(19));
		literals.put("fi", new Integer(20));

	}
	
	public Scanner (String fileName, String outFile) {
		buffer = new Buffer(fileName, this);
		try{out = new PrintWriter(new FileWriter(outFile));}catch(Exception xx){System.exit(0);}
		Init();
	}
	
	public Scanner(InputStream s, Writer outFile) {
		buffer = new Buffer(s, this);
		try{out = new PrintWriter(outFile);}catch(Exception xx){System.exit(0);}
		Init();
	}
	
	public PrintWriter outFile(){
		return out;
	}
	
	public Token currentToken(){
		return t;
	}

	void Init () {
		out.println("Compiled on: " + new Date());
		pos = -1; line = 1; col = 0;
		oldEols = 0;
		NextCh();
		if (ch == 0xEF) { // check optional byte order mark for UTF-8
			NextCh(); int ch1 = ch;
			NextCh(); int ch2 = ch;
			if (ch1 != 0xBB || ch2 != 0xBF) {
				throw new FatalError("Illegal byte order mark at start of file");
			}
			buffer = new UTF8Buffer(buffer, this); col = 0;
			NextCh();
		}
		pt = tokens = new Token();  // first token is a dummy
	}
	
	void NextCh() {
		if (oldEols > 0) { ch = EOL; oldEols--; }
		else {
			pos = buffer.getPos();
			ch = buffer.Read(); col++;
			// replace isolated '\r' by '\n' in order to make
			// eol handling uniform across Windows, Unix and Mac
			if (ch == '\r' && buffer.Peek() != '\n') ch = EOL;
			if (ch == EOL) { line++; col = 0; }
		}

	}
	

	boolean Comment0() {
		int level = 1, pos0 = pos, line0 = line, col0 = col;
		NextCh();
		if (ch == '-') {
			NextCh();
			for(;;) {
				if (ch == 13) {
					level--;
					if (level == 0) { oldEols = line - line0; NextCh(); return true; }
					NextCh();
				} else if (ch == Buffer.EOF) return false;
				else NextCh();
			}
		} else {
			buffer.setPos(pos0); NextCh(); line = line0; col = col0;
		}
		return false;
	}

	boolean Comment1() {
		int level = 1, pos0 = pos, line0 = line, col0 = col;
		NextCh();
			for(;;) {
				if (ch == '}') {
					level--;
					if (level == 0) { oldEols = line - line0; NextCh(); return true; }
					NextCh();
				} else if (ch == '{') {
					level++; NextCh();
				} else if (ch == Buffer.EOF) return false;
				else NextCh();
			}
	}


	void CheckLiteral() {
		String val = t.val;

		Object kind = literals.get(val);
		if (kind != null) {
			t.kind = ((Integer) kind).intValue();
		}
	}

	Token NextToken() {
		while(ch == ' ' ||
			ch >= 9 && ch <= 10 || ch == 13
		) NextCh();
		if (ch == '-' && Comment0() ||ch == '{' && Comment1()) return NextToken();
		t = new Token();
		t.pos = pos; t.col = col; t.line = line; 
		int state = start.state(ch);
		char[] tval = tokenText; // local variables are more efficient
		int tlen = 0;
		tval[tlen++] = (char)ch; NextCh();
		
		boolean done = false;
		while (!done) {
			if (tlen >= tval.length) {
				char[] newBuf = new char[2 * tval.length];
				System.arraycopy(tval, 0, newBuf, 0, tval.length);
				tokenText = tval = newBuf;
			}
			switch (state) {
				case -1: { t.kind = eofSym; done = true; break; } // NextCh already done 
				case 0: { t.kind = noSym; done = true; break; }   // NextCh already done
				case 1:
					if (ch >= '0' && ch <= '9' || ch >= 'A' && ch <= 'Z' || ch == '_' || ch >= 'a' && ch <= 'z') {tval[tlen++] = (char)ch; NextCh(); state = 1; break;}
					else {t.kind = 1; t.val = new String(tval, 0, tlen); CheckLiteral(); return t;}
				case 2:
					if (ch >= '0' && ch <= '9') {tval[tlen++] = (char)ch; NextCh(); state = 2; break;}
					else {t.kind = 2; done = true; break;}
				case 3:
					if (ch == '+' || ch == '-') {tval[tlen++] = (char)ch; NextCh(); state = 4; break;}
					else {t.kind = noSym; done = true; break;}
				case 4:
					{t.kind = 36; done = true; break;}
				case 5:
					if (ch == '+' || ch == '-') {tval[tlen++] = (char)ch; NextCh(); state = 6; break;}
					else {t.kind = noSym; done = true; break;}
				case 6:
					{t.kind = 37; done = true; break;}
				case 7:
					{t.kind = 38; done = true; break;}
				case 8:
					{t.kind = 39; done = true; break;}
				case 9:
					if (ch == '+' || ch == '-') {tval[tlen++] = (char)ch; NextCh(); state = 10; break;}
					else {t.kind = noSym; done = true; break;}
				case 10:
					{t.kind = 40; done = true; break;}
				case 11:
					{t.kind = 41; done = true; break;}
				case 12:
					{t.kind = 42; done = true; break;}
				case 13:
					if (ch == 'C' || ch == 'c') {tval[tlen++] = (char)ch; NextCh(); state = 3; break;}
					else if (ch == 'O' || ch == 'o') {tval[tlen++] = (char)ch; NextCh(); state = 5; break;}
					else if (ch == 'S' || ch == 's') {tval[tlen++] = (char)ch; NextCh(); state = 14; break;}
					else if (ch == 'M' || ch == 'm') {tval[tlen++] = (char)ch; NextCh(); state = 9; break;}
					else if (ch == 'R' || ch == 'r') {tval[tlen++] = (char)ch; NextCh(); state = 15; break;}
					else {t.kind = noSym; done = true; break;}
				case 14:
					if (ch == '+') {tval[tlen++] = (char)ch; NextCh(); state = 7; break;}
					else if (ch == '-') {tval[tlen++] = (char)ch; NextCh(); state = 8; break;}
					else {t.kind = noSym; done = true; break;}
				case 15:
					if (ch == '+') {tval[tlen++] = (char)ch; NextCh(); state = 11; break;}
					else if (ch == '-') {tval[tlen++] = (char)ch; NextCh(); state = 12; break;}
					else {t.kind = noSym; done = true; break;}
				case 16:
					{t.kind = 5; done = true; break;}
				case 17:
					{t.kind = 8; done = true; break;}
				case 18:
					{t.kind = 9; done = true; break;}
				case 19:
					{t.kind = 14; done = true; break;}
				case 20:
					if (ch == '=') {tval[tlen++] = (char)ch; NextCh(); state = 21; break;}
					else {t.kind = noSym; done = true; break;}
				case 21:
					{t.kind = 18; done = true; break;}
				case 22:
					{t.kind = 21; done = true; break;}
				case 23:
					{t.kind = 22; done = true; break;}
				case 24:
					{t.kind = 23; done = true; break;}
				case 25:
					{t.kind = 25; done = true; break;}
				case 26:
					{t.kind = 26; done = true; break;}
				case 27:
					{t.kind = 27; done = true; break;}
				case 28:
					{t.kind = 28; done = true; break;}
				case 29:
					{t.kind = 31; done = true; break;}
				case 30:
					{t.kind = 32; done = true; break;}
				case 31:
					{t.kind = 33; done = true; break;}
				case 32:
					{t.kind = 34; done = true; break;}
				case 33:
					if (ch == ']') {tval[tlen++] = (char)ch; NextCh(); state = 22; break;}
					else {t.kind = 13; done = true; break;}
				case 34:
					if (ch == '>') {tval[tlen++] = (char)ch; NextCh(); state = 23; break;}
					else {t.kind = 24; done = true; break;}
				case 35:
					if (ch == '=') {tval[tlen++] = (char)ch; NextCh(); state = 29; break;}
					else {t.kind = 29; done = true; break;}
				case 36:
					if (ch == '=') {tval[tlen++] = (char)ch; NextCh(); state = 30; break;}
					else {t.kind = 30; done = true; break;}

			}
		}
		t.val = new String(tval, 0, tlen);
		return t;
	}
	
	// get the next token (possibly a token already seen during peeking)
	public Token Scan () {
		if (tokens.next == null) {
			return NextToken();
		} else {
			pt = tokens = tokens.next;
			return tokens;
		}
	}

	// get the next token, ignore pragmas
	public Token Peek () {
		do {
			if (pt.next == null) {
				pt.next = NextToken();
			}
			pt = pt.next;
		} while (pt.kind > maxT); // skip pragmas

		return pt;
	}

	// make sure that peeking starts at current scan position
	public void ResetPeek () { pt = tokens; }

} // end Scanner

