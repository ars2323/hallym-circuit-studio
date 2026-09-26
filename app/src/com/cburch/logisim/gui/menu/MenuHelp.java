/* Copyright (c) 2010, Carl Burch. License information is located in the
 * com.cburch.logisim.Main source code and at www.cburch.com/logisim/. */

package com.cburch.logisim.gui.menu;

import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.start.About;
import com.cburch.logisim.util.MacCompatibility;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.Locale;

import javax.help.HelpSet;
import javax.help.JHelp;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

class MenuHelp extends JMenu implements ActionListener {
	private LogisimMenuBar menubar;
	private JMenuItem tutorial = new JMenuItem();
	private JMenuItem quickStart = new JMenuItem(); // HCS: #23
	private JMenuItem shortcuts = new JMenuItem(); // HCS: E-09 keyboard shortcuts
	private JMenu examples = new JMenu(); // HCS: V-07 bundled examples
	private JMenuItem guide = new JMenuItem();
	private JMenuItem library = new JMenuItem();
	private JMenuItem about = new JMenuItem();
	private HelpSet helpSet;
	private String helpSetUrl = "";
	private JHelp helpComponent;
	private LFrame helpFrame;

	public MenuHelp(LogisimMenuBar menubar) {
		this.menubar = menubar;

		tutorial.addActionListener(this);
		quickStart.addActionListener(this);
		shortcuts.addActionListener(this); // HCS: E-09
		guide.addActionListener(this);
		library.addActionListener(this);
		about.addActionListener(this);

		add(quickStart);
		for (String name : kr.ac.hallym.hcs.app.tutorial.Examples.NAMES) { // HCS: V-07
			JMenuItem it = new JMenuItem(name);
			it.addActionListener(e -> kr.ac.hallym.hcs.app.tutorial.Examples.open(menubar.getParentWindow(),
					menubar.getProject(), name));
			examples.add(it);
		}
		add(examples); // HCS: V-07
		add(shortcuts); // HCS: E-09
		add(tutorial);
		add(guide);
		add(library);
		if (!MacCompatibility.isAboutAutomaticallyPresent()) {
			addSeparator();
			add(about);
		}
	}

	public void localeChanged() {
		this.setText(Strings.get("helpMenu"));
		if (helpFrame != null) {
			helpFrame.setTitle(Strings.get("helpWindowTitle"));
		}
		tutorial.setText(kr.ac.hallym.hcs.app.Messages.get("tour.menu")); // HCS: E-10 window tour
		quickStart.setText(kr.ac.hallym.hcs.app.Messages.get("quickstart.menu")); // HCS: #23
		shortcuts.setText(kr.ac.hallym.hcs.app.Messages.get("keys.menu")); // HCS: E-09
		examples.setText(kr.ac.hallym.hcs.app.Messages.get("examples.menu")); // HCS: V-07
		guide.setText(Strings.get("helpGuideItem"));
		library.setText(Strings.get("helpLibraryItem"));
		about.setText(Strings.get("helpAboutItem"));
		if (helpFrame != null) {
			helpFrame.setLocale(Locale.getDefault());
			loadBroker();
		}
	}

	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == guide) {
			showHelp("guide");
		} else if (src == shortcuts) { // HCS: E-09
			kr.ac.hallym.hcs.app.keys.Shortcuts.showTable(menubar.getParentWindow());
		} else if (src == quickStart) { // HCS: #23
			kr.ac.hallym.hcs.app.tutorial.QuickStart.show(menubar.getParentWindow());
		} else if (src == tutorial) { // HCS: E-10 window tour instead of the JavaHelp tutorial (still in the guide)
			java.awt.Window w = menubar.getParentWindow();
			if (w instanceof com.cburch.logisim.gui.main.Frame) {
				kr.ac.hallym.hcs.app.tutorial.Tour.show((com.cburch.logisim.gui.main.Frame) w);
			} else {
				showHelp("tutorial");
			}
		} else if (src == library) {
			showHelp("libs");
		} else if (src == about) {
			kr.ac.hallym.hcs.app.about.AboutDialog.show(menubar.getParentWindow()); // HCS: E-11 About
		}
	}
	
	private void loadBroker() {
		String helpUrl = Strings.get("helpsetUrl");
		if (helpUrl == null) helpUrl = "doc/doc_en.hs";
		if (helpSet == null || helpFrame == null || !helpUrl.equals(helpSetUrl)) {
			ClassLoader loader = MenuHelp.class.getClassLoader();
			try {
				URL hsURL = HelpSet.findHelpSet(loader, helpUrl);
				if (hsURL == null) {
					disableHelp();
					JOptionPane.showMessageDialog(menubar.getParentWindow(),
							Strings.get("helpNotFoundError"));
					return;
				}
				helpSetUrl = helpUrl;
				helpSet = new HelpSet(null, hsURL);
				helpComponent = new JHelp(helpSet);
				if (helpFrame == null) {
					helpFrame = new LFrame();
					helpFrame.setTitle(Strings.get("helpWindowTitle"));
					helpFrame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
					helpFrame.getContentPane().add(helpComponent);
					helpFrame.pack();
				} else {
					helpFrame.getContentPane().removeAll();
					helpFrame.getContentPane().add(helpComponent);
					helpComponent.revalidate();
				}
			} catch (Exception e) {
				disableHelp();
				e.printStackTrace();
				JOptionPane.showMessageDialog(menubar.getParentWindow(),
						Strings.get("helpUnavailableError"));
				return;
			}
		}
	}

	private void showHelp(String target) {
		loadBroker();
		try {
			helpComponent.setCurrentID(target);
			helpFrame.toFront();
			helpFrame.setVisible(true);
		} catch (Exception e) {
			disableHelp();
			e.printStackTrace();
			JOptionPane.showMessageDialog(menubar.getParentWindow(),
					Strings.get("helpDisplayError"));
		}
	}

	private void disableHelp() {
		guide.setEnabled(false);
		tutorial.setEnabled(false);
		library.setEnabled(false);
	}
}
