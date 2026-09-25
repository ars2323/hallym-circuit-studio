/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-23 회귀: MIPS 부품(Instruction Memory, Data Memory, Stack, Console)의 포트 이름은 포트가 놓인 테두리에서 14px
 * 안쪽에 적혀, 포트마다 붙인 터널 글자와 겹치지 않는다. Console의 출력 칸은 포트 이름과 떨어져 있다. 편집 캔버스의
 * 그리기 문맥(PortLabels)은 우리 부품을 원조 그대로 그리므로 두 문맥에서 같다.
 */
class MipsPortInsetTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    /** lib-mips MemoryFactory.PORT_INSET. */
    static final int PORT_INSET = 14;
    static final Color OUTPUT_FILL = new Color(0xF4, 0xF6, 0xF8);

    @TempDir
    Path tmp;

    /** 그린 글자의 자리(화면 사각형)와 출력 칸 채움을 적어 두는 Graphics. */
    static final class Recorder extends DelegatingGraphics {
        final List<Object[]> texts;
        final List<Rectangle> fills;

        Recorder(Graphics2D g, List<Object[]> texts, List<Rectangle> fills) {
            super(g);
            this.texts = texts;
            this.fills = fills;
        }

        @Override
        public void drawString(String s, int x, int y) {
            java.awt.FontMetrics fm = g.getFontMetrics();
            texts.add(new Object[] {s, new Rectangle(x, y - fm.getAscent(), fm.stringWidth(s), fm.getAscent()
                    + fm.getDescent())});
            g.drawString(s, x, y);
        }

        @Override
        public void fillRect(int x, int y, int w, int h) {
            if (OUTPUT_FILL.equals(g.getColor())) {
                fills.add(new Rectangle(x, y, w, h));
            }
            g.fillRect(x, y, w, h);
        }

        @Override
        public Graphics create() {
            return new Recorder((Graphics2D) g.create(), texts, fills);
        }
    }

    Circuit circuit;
    com.cburch.logisim.circuit.CircuitState state;
    List<Component> tunnels = new ArrayList<>();

    Component place(String kind, String... portNames) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "m");
        Path jar = dir.resolve("hcs-mips.jar");
        Files.copy(MIPS_JAR.toPath(), jar, StandardCopyOption.REPLACE_EXISTING);
        Loader loader = new Loader(null);
        LogisimFile f = CircuitBuilder.newFile(loader, dir.toFile());
        Library mips = loader.loadJarLibrary(jar.toFile(), "kr.ac.hallym.hcs.mips.MipsLibrary");
        f.addLibrary(mips);
        circuit = f.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(f, circuit);
        Component m = b.add(mips, kind, 600, 400);
        // 포트마다 포트 이름과 같은 긴 이름의 터널(데모 회로처럼)
        for (int i = 0; i < m.getEnds().size(); i++) {
            b.tunnel(m, i, "t" + i + "_" + (i < portNames.length ? portNames[i] : "port"));
        }
        b.commit();
        tunnels.clear();
        for (Component c : circuit.getNonWires()) {
            if ("Tunnel".equals(c.getFactory().getName())) {
                tunnels.add(c);
            }
        }
        com.cburch.logisim.proj.Project p = new com.cburch.logisim.proj.Project(f);
        p.getSimulator().setIsRunning(false);
        state = p.getCircuitState();
        return m;
    }

    List<Object[]> texts = new ArrayList<>();
    List<Rectangle> fills = new ArrayList<>();

    void draw(Component c, boolean canvasContext) {
        texts.clear();
        fills.clear();
        Recorder g = new Recorder(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics(), texts,
                fills);
        ComponentDrawContext ctx = canvasContext ? PortLabels.forTest(circuit, state, g, 1.0, null)
                : new ComponentDrawContext(null, circuit, state, g, g);
        c.draw(ctx);
    }

    Rectangle text(String s) {
        for (Object[] t : texts) {
            if (s.equals(t[0])) {
                return (Rectangle) t[1];
            }
        }
        return null;
    }

    /** 포트 이름이 그 포트의 변에서 14px 안쪽이고, 부품 안이다. */
    void assertInset(Component m, String[] portNames) {
        Bounds b = m.getBounds();
        Rectangle box = new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight());
        for (int i = 0; i < portNames.length; i++) {
            if (portNames[i] == null) {
                continue;
            }
            Rectangle r = text(portNames[i]);
            assertTrue(r != null, portNames[i] + " is drawn");
            assertTrue(box.contains(r), portNames[i] + " inside the part: " + r + " " + box);
            Location p = m.getEnd(i).getLocation();
            if (p.getX() <= b.getX()) {
                assertEquals(b.getX() + PORT_INSET, r.x, portNames[i] + " starts 14px in from the left edge");
            } else if (p.getX() >= b.getX() + b.getWidth()) {
                assertEquals(b.getX() + b.getWidth() - PORT_INSET, r.x + r.width, 1,
                        portNames[i] + " ends 14px in from the right edge");
            } else if (p.getY() <= b.getY()) {
                assertTrue(r.y >= b.getY() + PORT_INSET - 1, portNames[i] + " 14px below the top edge: " + r);
            } else {
                // 아랫변: 기준선이 테두리에서 10px 위(PORT_INSET - 4), 글자 아래 끝도 테두리 안쪽
                assertTrue(r.y + r.height <= b.getY() + b.getHeight() - 5, portNames[i] + " above the bottom: " + r);
            }
        }
    }

    /** 포트 이름이 부품 밖 터널 글자와 겹치지 않는다. */
    void assertClearOfTunnels(String[] portNames) {
        List<Rectangle> tunnelText = new ArrayList<>();
        for (Component t : tunnels) {
            List<Object[]> own = new ArrayList<>(texts);
            texts.clear();
            Recorder g = new Recorder(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics(), texts,
                    fills);
            t.draw(new ComponentDrawContext(null, circuit, state, g, g));
            for (Object[] x : texts) {
                tunnelText.add((Rectangle) x[1]);
            }
            texts.clear();
            texts.addAll(own);
        }
        assertEquals(tunnels.size(), tunnelText.size(), "every tunnel drew its name");
        for (String n : portNames) {
            if (n == null) {
                continue;
            }
            Rectangle r = text(n);
            for (Rectangle t : tunnelText) {
                assertFalse(r.intersects(t), n + " overlaps a tunnel name: " + r + " " + t);
            }
        }
    }

    void check(String kind, String... portNames) throws Exception {
        Component m = place(kind, portNames);
        for (boolean canvas : new boolean[] {false, true}) {
            draw(m, canvas);
            assertInset(m, portNames);
            assertClearOfTunnels(portNames);
        }
    }

    @Test
    void instructionMemoryPortNamesSitInside() throws Exception {
        check("Instruction Memory", "Addr", "Instr");
    }

    @Test
    void dataMemoryPortNamesSitInside() throws Exception {
        check("Data Memory", "Addr", "WriteData", "MemWrite", "MemRead", null, "ReadData");
    }

    @Test
    void stackPortNamesSitInside() throws Exception {
        check("Stack", "Addr", "WriteData", "MemWrite", "MemRead", null, "ReadData");
    }

    @Test
    void consoleOutputAreaIsClearOfThePortNames() throws Exception {
        Component m = place("Console", "Syscall", "V0", "A0", null, "Exit");
        draw(m, true);
        String[] names = {"Syscall", "V0", "A0", "Exit"};
        assertEquals(1, fills.size(), "one output area");
        Rectangle out = fills.get(0);
        Bounds b = m.getBounds();
        assertTrue(new Rectangle(b.getX(), b.getY(), b.getWidth(), b.getHeight()).contains(out), "inside the part");
        for (String n : names) {
            Rectangle r = text(n);
            assertTrue(r != null, n + " is drawn");
            assertFalse(r.intersects(out), n + " overlaps the output area: " + r + " " + out);
        }
        // 포트 이름 자체도 14px 안쪽이고 터널 글자와 떨어져 있다(포트 순서: Syscall, V0, A0, clk, Exit)
        String[] byPort = {"Syscall", "V0", "A0", null, "Exit"};
        assertInset(m, byPort);
        assertClearOfTunnels(names);
    }
}
