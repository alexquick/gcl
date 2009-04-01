package gcl;

import java.util.Hashtable;
import java.util.Enumeration;

// --------------------- SymbolTable ---------------------------------
public class SymbolTable {
	private SymbolTable(SymbolTable oldScope) {
		next = oldScope;
	}

	/** return a new symbol table not chained to any other. */
	public static SymbolTable unchained() {
		SymbolTable result = new SymbolTable(null);
		return result;
	}

	/** Retrieve the current maximal (deepest) scope */
	public static SymbolTable currentScope() {
		return globalScope;
	}

	/** Create a new scope chained to the current scope
	 *  @param isPublic tells if this is a public or privat scope. For documentation only.
	 *  @return a new scope chained to the original current. The new one is now current.
	 */
	public SymbolTable openScope(boolean isPublic) {
		publicScope = isPublic;
		SymbolTable result = new SymbolTable(this);
		globalScope = result;
		return result;
	}

	/** Abandon the current scope for the previous one. Used to make the @s+ pragma
	 *  behave properly.     */
	public void closeScope() {
		globalScope = next;
	}

	/** Restore a previously saved procedure scope. Note that this has no effect
	 * on the computation other than to enable @s+ to work correctly.
	 *  @param scope the scope to be restored. It must have been previously saved.
	 */
	public void restoreProcedureScope(SymbolTable scope) {
		globalScope = scope;
	}

	/** Factory for new symbol table entries.  Creates a new entry and 
	 * puts it into the symbol table
	 *  @param entryKind the kind of entry, variable, type,...
	    @param name the identifier to assign to this entry
	    @param item the semantic item to return when this entry is used
	    @return an entry according to the entryKind
	 */
	public Entry newEntry(String entryKind, Identifier name, SemanticItem item) {
		Entry result = new Entry(entryKind, name, item);
		enterIdentifier(name, result); // save it in this hashtable
		return result;
	}

	/** Lookup an identifier in this SymbolTable and the ones chained to it
		@param name some identifer to be looked up
	    @return the associated symbol table entry or null
	 */
	public Entry lookupIdentifier(Identifier name) { // Checks initializations also
		Entry result = null;
		Hashtable<Identifier, Entry> here = storage;
		SymbolTable current = this;
		while (here != null) {
			if (here.containsKey(name)) {
				result = here.get(name);
				return result;
			} else {
				CompilerOptions.message("Not yet found: " + name); // info only, not necessarily an error
			}
			if (current.next == null){
				here = null;
			}
			else {
				current = current.next;
				here = current.storage;
			}
		}
		return result;
	}
	
	/** Lookup an identifier in this SymbolTable and the ones chained to it
		@param name some identifer to be looked up
    	@return the associated symbol table entry or null
	*/
	public Entry lookupIdentifier(Identifier name, SemanticItem module){
		if(!(module instanceof Module)){
			return null;
		}
		return ((Module)module).resolve(name);
	}

	/** Insert an identifier and its associated data into the SymbolTable
		@param name the identifier to insert (entryKind)
	    @param value the associated data
	 */
	private void enterIdentifier(Identifier name, Entry value) {
		if (name != null){
			storage.put(name, value);
		}
	}

	/** Show the entire symbol table */
	public void dump() {
		boolean old = CompilerOptions.listCode;
		CompilerOptions.listCode = true;
		CompilerOptions.listCode("");
		CompilerOptions.listCode("------ Symbol Table ------");
		CompilerOptions.listCode("");
		Hashtable<Identifier, Entry> here = storage;
		SymbolTable current = this;
		while (here != null) {
			Enumeration<Entry> e = here.elements();
			while (e.hasMoreElements()) {
				CompilerOptions.listCode(e.nextElement().toString());
			}
			if (current.next == null){
				here = null;
			}
			else {
				CompilerOptions.listCode("Scope change");
				current = current.next;
				here = current.storage;
			}
		}
		CompilerOptions.listCode("");
		CompilerOptions.listCode("------ Symbol Table End ------");
		CompilerOptions.listCode("");
		CompilerOptions.listCode = old;
	}

	private static final int hashsize = 8;
	private Hashtable<Identifier, Entry> storage = new Hashtable<Identifier, Entry>(hashsize);
	private SymbolTable next = null;
	private static SymbolTable globalScope = null;
	//Note that the only purpose of globalScope is to permit the $s+ pragma to work
	//It has no real function in the compiler otherwise.
	private static boolean publicScope = true; // Are new defs public or private? Documentation only.

	public static void dumpAll() {
		globalScope.dump();
	}

	/**Register the prototypes with the factory. Extend for each new subclass of Entry
	 */
	public static void initializeSymbolTable() {
		globalScope = new SymbolTable(null);
		publicScope = true;
	}

	public int size() {
		int result = storage.size();
		while (next != null) {
			SymbolTable another = next;
			result += another.storage.size();
		}
		return result;
	}

	static {
		initializeSymbolTable();
	}

	// -------------- Symbol Table Entries ----- inner -------

	static class Entry {
		public Entry(String entryKind, Identifier itsName, SemanticItem item) {
			identifierValue = itsName;
			isPublic = publicScope;
			this.entryKind = entryKind;
			if (item != null) {
				semanticItem = item;
			}
		}

		public String toString() {
			return (isPublic() ? "public " : "private ") + entryKind
					+ " entry: ID = " + identifierValue + " semantic: "
					+ semanticItem.toString();
		}

		public SemanticItem semanticRecord() {
			return semanticItem;
		}

		private boolean isPublic() {
			return isPublic;
		}

		public Identifier identifier() {
			return identifierValue;
		}

		private Identifier identifierValue;
		private String entryKind; // Documentation only
		private boolean isPublic = true; // Documentation only
		private SemanticItem semanticItem = DEFAULT_ITEM;
		private static final SemanticItem DEFAULT_ITEM;
		static {
			boolean messages = CompilerOptions.showMessages;
			CompilerOptions.showMessages = false;
			DEFAULT_ITEM = new SemanticError("Error entry in symbol table.");
			CompilerOptions.showMessages = messages;
		}
	}
}