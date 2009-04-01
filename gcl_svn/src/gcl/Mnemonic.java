package gcl;

/** --------------------- Assembly Mnemonics and opcodes ----------------------------
 * THIS FILE SHOULD NEVER CHANGE. IT IS KEYED TO THE SAM2 Assembler.
 */
public interface Mnemonic {
	class SamOp {
		private SamOp(int op, String samCode) {
			this.op = op;
			this.samCode = samCode;
		}

		public String samCode() {
			return samCode;
		}

		private int op; // unused. Just for documentation.
		private String samCode;
	}

	public static final SamOp INEG = new SamOp(0, "     IN      ");
	public static final SamOp IA = new SamOp(1, "     IA      ");
	public static final SamOp IS = new SamOp(2, "     IS      ");
	public static final SamOp IM = new SamOp(3, "     IM      ");
	public static final SamOp ID = new SamOp(4, "     ID      ");
	public static final SamOp FNEG = new SamOp(5, "     FN      ");
	public static final SamOp FA = new SamOp(6, "     FA      ");
	public static final SamOp FS = new SamOp(7, "     FS      ");
	public static final SamOp FM = new SamOp(8, "     FM      ");
	public static final SamOp FD = new SamOp(9, "     FD      ");
	public static final SamOp BI = new SamOp(10, "     BI      ");
	public static final SamOp BO = new SamOp(11, "     BO      ");
	public static final SamOp BA = new SamOp(12, "     BA      ");
	public static final SamOp IC = new SamOp(13, "     IC      ");
	public static final SamOp FC = new SamOp(14, "     FC      ");
	public static final SamOp JSR = new SamOp(15, "     JSR     ");
	public static final SamOp BKT = new SamOp(16, "     BKT     ");
	public static final SamOp LD = new SamOp(17, "     LD      ");
	public static final SamOp STO = new SamOp(18, "     STO     ");
	public static final SamOp LDA = new SamOp(19, "     LDA     ");
	public static final SamOp FLT = new SamOp(20, "     FLT     ");
	public static final SamOp FIX = new SamOp(21, "     FIX     ");
	public static final SamOp JMP = new SamOp(22, "     JMP     ");
	public static final SamOp JLT = new SamOp(23, "     JLT     ");
	public static final SamOp JLE = new SamOp(24, "     JLE     ");
	public static final SamOp JEQ = new SamOp(25, "     JEQ     ");
	public static final SamOp JNE = new SamOp(26, "     JNE     ");
	public static final SamOp JGE = new SamOp(27, "     JGE     ");
	public static final SamOp JGT = new SamOp(28, "     JGT     ");
	public static final SamOp NOP = new SamOp(29, "     NOP     ");
	public static final SamOp SRZ = new SamOp(30, "     SRZ     ");
	public static final SamOp SRO = new SamOp(31, "     SRO     ");
	public static final SamOp SRE = new SamOp(32, "     SRE     ");
	public static final SamOp SRC = new SamOp(33, "     SRC     ");
	public static final SamOp SRCZ = new SamOp(34, "     SRCZ    ");
	public static final SamOp SRCO = new SamOp(35, "     SRCO    ");
	public static final SamOp SRCE = new SamOp(36, "     SRCE    ");
	public static final SamOp SRCC = new SamOp(37, "     SRCC    ");
	public static final SamOp SLZ = new SamOp(38, "     SLZ     ");
	public static final SamOp SLO = new SamOp(39, "     SLO     ");
	public static final SamOp SLE = new SamOp(40, "     SLE     ");
	public static final SamOp SLC = new SamOp(41, "     SLC     ");
	public static final SamOp SLCZ = new SamOp(42, "     SLCZ    ");
	public static final SamOp SLCO = new SamOp(43, "     SLCO    ");
	public static final SamOp SLCE = new SamOp(44, "     SLCE    ");
	public static final SamOp SLCC = new SamOp(45, "     SLCC    ");
	public static final SamOp RDI = new SamOp(46, "     RDI     ");
	public static final SamOp RDF = new SamOp(47, "     RDF     ");
	public static final SamOp RDBD = new SamOp(48, "     RDBD    ");
	public static final SamOp RDBW = new SamOp(49, "     RDBW    ");
	public static final SamOp RDOD = new SamOp(50, "     RDOD    ");
	public static final SamOp RDOW = new SamOp(51, "     RDOW    ");
	public static final SamOp RDHD = new SamOp(52, "     RDHD    ");
	public static final SamOp RDHW = new SamOp(53, "     RDHW    ");
	public static final SamOp RDCH = new SamOp(54, "     RDCH    ");
	public static final SamOp RDST = new SamOp(55, "     RDST    ");
	public static final SamOp RDIN = new SamOp(56, "     RDIN    ");
	public static final SamOp WRI = new SamOp(57, "     WRI     ");
	public static final SamOp WRF = new SamOp(58, "     WRF     ");
	public static final SamOp WRBD = new SamOp(59, "     WRBD    ");
	public static final SamOp WRBW = new SamOp(60, "     WRBW    ");
	public static final SamOp WROD = new SamOp(61, "     WROD    ");
	public static final SamOp WROW = new SamOp(62, "     WROW    ");
	public static final SamOp WRHD = new SamOp(63, "     WRHD    ");
	public static final SamOp WRHW = new SamOp(64, "     WRHW    ");
	public static final SamOp WRCH = new SamOp(65, "     WRCH    ");
	public static final SamOp WRST = new SamOp(66, "     WRST    ");
	public static final SamOp WRNL = new SamOp(67, "     WRNL    ");
	public static final SamOp TRNG = new SamOp(68, "     TRNG    ");
	public static final SamOp HALT = new SamOp(69, "     HALT    ");
	public static final SamOp SKIP_DIRECTIVE = new SamOp(70, "     SKIP    ");
	public static final SamOp LABEL_DIRECTIVE = new SamOp(71, " LABEL ");
	public static final SamOp REAL_DIRECTIVE = new SamOp(72, "     REAL    ");
	public static final SamOp INT_DIRECTIVE = new SamOp(73, "     INT     ");
	public static final SamOp STRING_DIRECTIVE = new SamOp(74, "     STRING  ");

}
