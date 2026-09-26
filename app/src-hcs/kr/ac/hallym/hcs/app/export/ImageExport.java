/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.export;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.sim.SimControls;

/**
 * 그림 내보내기(E-07, PLAN.md 11.13): 회로 전체 또는 고른 부분을 SVG, PDF(둘 다 벡터), 고해상도 PNG(1~4배)로. 우리 덧그림
 * (라벨 칩, 연결점·점프)을 넣을지 고른다. 넣지 않으면 원조 그림 그대로다. 원조 File › Export Image를 대신한다.
 */
public final class ImageExport {
    /** 형식. */
    public enum Format {
        PNG("png"), SVG("svg"), PDF("pdf");

        public final String ext;

        Format(String ext) {
            this.ext = ext;
        }
    }

    static final int BORDER = 10;
    /** 덧그림(라벨 칩)을 넣을 때의 여백: 칩은 선 위·옆으로 칩 높이만큼 나간다. */
    static final int OVERLAY_BORDER = 30;

    private ImageExport() {
    }

    /** 그릴 범위(회로 좌표): 전체 또는 고른 부품. 비었으면 null. */
    static Bounds area(Circuit circuit, Collection<Component> only, Graphics g) {
        return area(circuit, only, g, false);
    }

    static Bounds area(Circuit circuit, Collection<Component> only, Graphics g, boolean overlays) {
        Bounds b;
        if (only == null) {
            b = circuit.getBounds(g);
        } else {
            b = Bounds.EMPTY_BOUNDS;
            for (Component c : only) {
                b = b.add(c.getBounds(g));
            }
        }
        return b.getWidth() <= 0 || b.getHeight() <= 0 ? null : b.expand(overlays ? OVERLAY_BORDER : BORDER);
    }

    /**
     * 회로를 그린다(g는 회로 좌표로 맞춘 것). only가 있으면 그것만. overlays면 우리 라벨 칩과 연결점·점프를 함께.
     */
    static void draw(Graphics2D g, Canvas canvas, Circuit circuit, CircuitState state, Collection<Component> only,
            boolean overlays) {
        Set<Component> hidden = new HashSet<>();
        if (only != null) {
            for (Component c : circuit.getNonWires()) {
                if (!only.contains(c)) {
                    hidden.add(c);
                }
            }
            for (Component w : circuit.getWires()) {
                if (!only.contains(w)) {
                    hidden.add(w);
                }
            }
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Graphics drawG = overlays && canvas != null
                ? kr.ac.hallym.hcs.app.labels.LabelOverlay.wrap(canvas, g, circuit, hidden) : g;
        ComponentDrawContext context = new ComponentDrawContext(canvas, circuit, state, g, drawG, false);
        circuit.draw(context, hidden);
        if (overlays && canvas != null) {
            kr.ac.hallym.hcs.app.wiring.WireMarks.paint(canvas, g, circuit, state, hidden);
            kr.ac.hallym.hcs.app.labels.LabelOverlay.paint(canvas, g, circuit, state, hidden);
        }
    }

    /** 파일로 쓴다. 쓸 것이 없으면 false. */
    public static boolean write(File dest, Format f, Canvas canvas, Circuit circuit, CircuitState state,
            Collection<Component> only, boolean overlays, int scale) throws IOException {
        // 한 번 그려 두어야 글자 크기에 따른 부품 범위가 정해진다(원조는 그릴 때 범위를 고친다)
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D probe = scratch.createGraphics();
        draw(probe, canvas, circuit, state, only, overlays);
        Bounds b = area(circuit, only, probe, overlays);
        probe.dispose();
        if (b == null) {
            return false;
        }
        if (f == Format.PNG) {
            int s = Math.max(1, Math.min(4, scale));
            BufferedImage img = new BufferedImage(b.getWidth() * s, b.getHeight() * s, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.setColor(Color.BLACK);
            g.scale(s, s);
            g.translate(-b.getX(), -b.getY());
            draw(g, canvas, circuit, state, only, overlays);
            g.dispose();
            ImageIO.write(img, "PNG", dest);
            return true;
        }
        VectorGraphics.Sink sink = new VectorGraphics.Sink();
        VectorGraphics g = new VectorGraphics(sink);
        g.setColor(Color.BLACK);
        g.translate(-b.getX(), -b.getY());
        draw(g, canvas, circuit, state, only, overlays);
        try (OutputStream out = Files.newOutputStream(dest.toPath())) {
            if (f == Format.SVG) {
                VectorWriters.svg(sink.items, b.getWidth(), b.getHeight(), out);
            } else {
                VectorWriters.pdf(sink.items, b.getWidth(), b.getHeight(), out);
            }
        }
        return true;
    }

    /** File › Export Image…: 형식·범위·배율·덧그림을 고르고 파일로. */
    public static void show(Project proj) {
        Canvas canvas = proj.getFrame() == null ? null : proj.getFrame().getCanvas();
        Circuit circuit = proj.getCurrentCircuit();
        Collection<Component> selected = proj.getSelection() == null ? Collections.<Component>emptyList()
                : proj.getSelection().getComponents();
        JComboBox<String> format = new JComboBox<>(new String[] {"PNG", "SVG", "PDF"});
        JComboBox<String> scale = new JComboBox<>(new String[] {"1×", "2×", "3×", "4×"});
        scale.setSelectedIndex(1);
        format.addActionListener(e -> scale.setEnabled(format.getSelectedIndex() == 0));
        JRadioButton all = new JRadioButton(Messages.get("export.all"), true);
        JRadioButton sel = new JRadioButton(Messages.get("export.selection"));
        sel.setEnabled(!selected.isEmpty());
        ButtonGroup scope = new ButtonGroup();
        scope.add(all);
        scope.add(sel);
        JCheckBox overlays = new JCheckBox(Messages.get("export.overlays"), true);
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        p.add(new JLabel(Messages.get("export.format")), c);
        c.gridx = 1;
        p.add(format, c);
        c.gridx = 0;
        c.gridy = 1;
        p.add(new JLabel(Messages.get("export.scale")), c);
        c.gridx = 1;
        p.add(scale, c);
        c.gridx = 0;
        c.gridy = 2;
        p.add(new JLabel(Messages.get("export.scope")), c);
        c.gridx = 1;
        JPanel sc = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        sc.add(all);
        sc.add(sel);
        p.add(sc, c);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        p.add(overlays, c);
        int r = JOptionPane.showConfirmDialog(proj.getFrame(), p, Messages.get("export.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        Format f = Format.values()[format.getSelectedIndex()];
        JFileChooser fc = new JFileChooser();
        File circ = proj.getLogisimFile().getLoader().getMainFile();
        String base = (circ == null ? proj.getLogisimFile().getName() : circ.getName().replaceFirst("\\.circ$", ""))
                + "-" + circuit.getName() + "." + f.ext;
        fc.setSelectedFile(new File(circ == null ? new File(".") : circ.getParentFile(), base));
        if (fc.showSaveDialog(proj.getFrame()) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File dest = fc.getSelectedFile();
        if (!dest.getName().toLowerCase().endsWith("." + f.ext)) {
            dest = new File(dest.getParentFile(), dest.getName() + "." + f.ext);
        }
        try {
            boolean ok = write(dest, f, canvas, circuit, proj.getCircuitState(), sel.isSelected() ? selected : null,
                    overlays.isSelected(), scale.getSelectedIndex() + 1);
            SimControls.notice(proj, Messages.get(ok ? "export.done" : "export.empty", dest.getName()));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(proj.getFrame(), Messages.get("export.failed", ex.getMessage()),
                    Messages.get("export.title"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
