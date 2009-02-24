package gcl;

/**
 Compiler for gcl
 Copyright 1998-2006, Joseph Bergin.  All rights reserved.

 This is a GUI front end to the compiler.   If you wish to use this front end then
 this file replaces the file GCLCompiler.java in your project.  All of the other files
 are the same.

 */
import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class GUICompiler extends Frame {
	private String currentFile = "test0"; // Default test file.
	private Button source = new Button("Source File");
	private Button listing = new Button("Listing File");
	private Button compile = new Button("Compile");
	private Button quit = new Button("Quit");
	private TextField sourceFilename = new TextField(currentFile, 60);
	private TextField listingFilename = new TextField(currentFile + ".lst", 60);
	private Label done = new Label("                ");
	private TextField compilerOptions = new TextField("c", 10);
	private String inFileName = currentFile, listFileName = currentFile
			+ ".lst";
	private static final long serialVersionUID = 1L;

	GUICompiler() {
		super("gcl Compiler");
		init();
	}

	public Dimension getPreferredSize() {
		return new Dimension(500, 200);
	}

	private void addItem(Component c, GridBagConstraints gbc, int x, int y,
			int w, int h) {
		gbc.gridx = x;
		gbc.gridy = y;
		gbc.gridwidth = w;
		gbc.gridheight = h;
		add(c, gbc);
	}

	void init() {
		setBackground(new Color(255, 255, 0xfa));
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		setLayout(gbl);
		gbc.insets = new Insets(0, 5, 0, 5);
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		addItem(source, gbc, 0, 0, 1, 1);
		ActionListener sl = new SourceListener();
		source.addActionListener(sl);
		gbc.weightx = 4.0;
		addItem(sourceFilename, gbc, 1, 0, 1, 1);
		ActionListener cl = new ClearListener();
		sourceFilename.addActionListener(cl);
		gbc.weightx = 1.0;
		addItem(listing, gbc, 0, 1, 1, 1);
		ActionListener ll = new ListListener();
		listing.addActionListener(ll);
		gbc.weightx = 4.0;
		addItem(listingFilename, gbc, 1, 1, 1, 1);
		listingFilename.addActionListener(cl);
		gbc.weightx = 1.0;
		addItem(compile, gbc, 0, 2, 1, 1);
		compile.addActionListener(new CompileListener());
		gbc.weightx = 4.0;
		addItem(compilerOptions, gbc, 1, 2, 1, 1);
		gbc.weightx = 1.0;
		addItem(quit, gbc, 0, 3, 1, 1);
		quit.addActionListener(new QuitListener());
		gbc.weightx = 4.0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		addItem(done, gbc, 1, 3, 1, 1);
		source.requestFocus();
		addWindowListener // Anonymous inner class
		(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				dispose();
				System.exit(0);
			}
		});
	}

	private class SourceListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			FileDialog d = new FileDialog(GUICompiler.this, "Source File",
					FileDialog.LOAD);
			d.setDirectory(".");
			d.setFile("");
			d.setVisible(true);
			inFileName = d.getFile();
			sourceFilename.setText(inFileName);
			done.setText("");
			d.dispose();
		}
	}

	private class ListListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			FileDialog d = new FileDialog(GUICompiler.this, "Listing File",
					FileDialog.SAVE);
			d.setDirectory(".");
			if (inFileName == null){
				inFileName = sourceFilename.getText();
			}
			d.setFile(inFileName + ".lst");
			d.setVisible(true);
			listFileName = d.getFile();
			listingFilename.setText(listFileName);
			done.setText("");
			d.dispose();
		}
	}

	private class ClearListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			done.setText("");
		}
	}

	private class CompileListener implements ActionListener {
		public void actionPerformed(ActionEvent e) // Compiler driver.
		{
			Cursor oldCursor = GUICompiler.this.getCursor();
			GUICompiler.this.setCursor(Cursor
					.getPredefinedCursor(Cursor.WAIT_CURSOR));
			done.setText("");
			inFileName = sourceFilename.getText();
			listFileName = listingFilename.getText();
			if (listFileName == null || listFileName.equals("")){
				listFileName = "list.txt";
			}
			FileInputStream temp = null;
			try {
				temp = new FileInputStream(inFileName);
				temp = null;
			} catch (FileNotFoundException exc) {
				done.setText("Source file not found.");
				GUICompiler.this.setCursor(oldCursor);
				temp = null;
				return;
			}
			String options = compilerOptions.getText().trim();
			for (int j = 0; j < options.length(); ++j){
				switch (Character.toUpperCase(options.charAt(j))) {
				case 'C':
					CompilerOptions.listCode = true;
					break;
				case 'O':
					CompilerOptions.optimize = true;
					break;
				case 'M':
					CompilerOptions.showMessages = true;
					break;
				default:
					System.out.println("Invalid option " + options.charAt(j)
							+ ":");
					System.out.println();
					System.out.println("Invalid option " + options.charAt(j));
					done.setText("Invalid option " + options.charAt(j)
							+ ". Use 'cmo' only.");
					GUICompiler.this.setCursor(oldCursor);
					return;
				}
			}
			FileInputStream aFile;
			try {
				aFile = new FileInputStream(inFileName);
				// Scanner.Init(aFile, new FileWriter(listFileName), new
				// SemanticActions.GCLErrorStream());
				Scanner scanner = new Scanner(aFile, new FileWriter(
						listFileName));
				out = scanner.outFile();
				// CompilerOptions.out = out;
				err = new SemanticActions.GCLErrorStream(scanner);
				codegen = new Codegen(err);
				// CompilerOptions.codegen = codegen;
				CompilerOptions.init(out, codegen);
				actions = new SemanticActions(codegen, err); // a
																// codegenerator
				// actions.init(err);
				parser = new Parser(scanner, actions, err);
				// SemanticActions.parser = parser;
				// SemanticActions.err = err;
			} catch (IOException e1) {
				System.out.println("File not found.");
				return;
			}
			try {
				SymbolTable.initializeSymbolTable();
				codegen.init();
				actions.init();
				parser.Parse();
			} catch (Throwable any) {
				err.count++;
				String message = any.getMessage();
				if (message != null){
					System.out.println(message);
				}
				any.printStackTrace();
				any.printStackTrace(out);
			} finally {
				System.out.println("  <end of compilation> 	There were "
						+ err.count + " errors detected");
				out.println("  <end of compilation> 	There were " + err.count
						+ " errors detected");
				CompilerOptions.printAllocatedRegisters();

				if (err.count > 0){
					codegen.gen0Address(Mnemonic.HALT);
				}
				codegen.closeCodefile();
				out.close();
				GUICompiler.this.setCursor(oldCursor);
			}
		}
	}

	static Parser parser;
	static SemanticActions.GCLErrorStream err;
	static PrintWriter out;
	static Codegen codegen;
	static SemanticActions actions;

	private class QuitListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			GUICompiler.this.dispose();
			System.exit(0);
		}
	}

	public static void main(String args[]) {
		GUICompiler thisCompiler = new GUICompiler();
		thisCompiler.pack();
		thisCompiler.setVisible(true);

	}

}

/*
 * Note that there was an error here. When compiling several files with this it
 * was not correctly reinitializing the complier between runs. I've tried to FIX
 * this but am not positive I have it. If you see anomalies when using this let
 * me know and I'll try to address it. If it happens and you want to continue
 * using this, it may be necessary to quit and restart it between runs.
 */