/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #79: 라벨 칩 배치(겹침 0), 원조 라벨만 빼는 그리기, 터널 색, 버스 이름, 마우스 오버 정보. */
class LabelsTest {
    @TempDir
    Path tmp;

    private static boolean overlaps(Rectangle a, Rectangle b) {
        return a.intersects(b);
    }

    @Test
    void chipsStayHomeWhenFreeAndNeverOverlap() {
        // 빈 자리면 원래 자리 그대로
        List<LabelLayout.Req> one = Collections.singletonList(
                new LabelLayout.Req("a", new Rectangle(100, 100, 20, 10), 30, 14, 0));
        LabelLayout.Placed p = LabelLayout.layout(one, new ArrayList<>(), 4, 10).get(0);
        assertEquals(new Rectangle(95, 98, 30, 14), p.rect);
        assertFalse(p.leader);

        // 빽빽한 라벨 40개와 부품 12개: 칩끼리, 칩과 부품이 겹치지 않는다
        Random rnd = new Random(7);
        List<LabelLayout.Req> reqs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            int x = 200 + 10 * (i % 8);
            int y = 200 + 10 * (i / 8);
            reqs.add(new LabelLayout.Req("L" + i, new Rectangle(x, y, 20, 10), 24 + rnd.nextInt(30), 14, 0));
        }
        List<Rectangle> obstacles = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            obstacles.add(new Rectangle(150 + 40 * (i % 4), 150 + 40 * (i / 4), 30, 30));
        }
        List<LabelLayout.Placed> placed = LabelLayout.layout(reqs, obstacles, 4, 40);
        assertEquals(40, placed.size());
        for (int i = 0; i < placed.size(); i++) {
            LabelLayout.Placed a = placed.get(i);
            assertFalse(a.overlapped, "no free spot for " + a.key);
            for (Rectangle o : obstacles) {
                assertFalse(overlaps(a.rect, o), a.key + " on a component");
            }
            for (int j = i + 1; j < placed.size(); j++) {
                assertFalse(overlaps(a.rect, placed.get(j).rect), a.key + " / " + placed.get(j).key);
            }
        }
        assertTrue(placed.stream().anyMatch(x -> x.leader), "far moves get a leader line");

        // 입력 순서와 무관하게 같은 결과
        List<LabelLayout.Req> shuffled = new ArrayList<>(reqs);
        Collections.shuffle(shuffled, new Random(3));
        List<LabelLayout.Placed> again = LabelLayout.layout(shuffled, obstacles, 4, 40);
        for (int i = 0; i < placed.size(); i++) {
            assertEquals(placed.get(i).key, again.get(i).key);
            assertEquals(placed.get(i).rect, again.get(i).rect);
        }
    }

    @Test
    void tunnelColorsAreDeterministic() {
        Set<Integer> used = new HashSet<>();
        for (String n : new String[] {"clk", "PC", "ALUResult", "RegWrite", "MemRead", "rs", "rt", "rd", "imm",
            "funct", "zero", "branch", "jump", "op"}) {
            int i = TunnelColors.index(n);
            assertTrue(i >= 0 && i < TunnelColors.PALETTE.length);
            assertEquals(i, TunnelColors.index(n));
            assertEquals(TunnelColors.PALETTE[i], TunnelColors.of(n));
            used.add(i);
        }
        assertTrue(used.size() >= 5, "names spread over the palette: " + used);
        // FNV-1a 결과를 고정해 실행·JVM과 무관함을 확인한다
        assertEquals(Math.floorMod(0xE40C292C, TunnelColors.PALETTE.length), TunnelColors.index("a"));
    }

    private static BufferedImage render(Circuit c, CircuitState state, boolean filtered) {
        BufferedImage img = new BufferedImage(700, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 700, 400);
        g.setColor(Color.BLACK);
        Graphics draw = filtered ? wrapFor(c, g) : g;
        ComponentDrawContext ctx = new ComponentDrawContext(new javax.swing.JPanel(), c, state, g, draw);
        c.draw(ctx, Collections.<Component>emptySet());
        g.dispose();
        return img;
    }

    private static Graphics wrapFor(Circuit c, Graphics2D g) {
        return LabelOverlay.filter(g, c, Collections.<Component>emptySet(), LabelOverlay.labelFields(c, g));
    }

    /** 걸러 낸 그리기는 원조 라벨 글자만 빠지고 나머지 픽셀(부품, 값 글자)은 원조와 같다. */
    @Test
    void filterRemovesOnlyTheOriginalLabels() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        b.add("Wiring", "Pin", 100, 100, "width", "8", "label", "Operand");
        b.add("Memory", "Register", 400, 200, "width", "8", "label", "PC");
        b.add("Gates", "AND Gate", 300, 320);
        b.add("Base", "Text", 550, 350, "text", "memo");
        b.commit();
        Circuit c = file.getMainCircuit();
        CircuitState state = new Project(file).getCircuitState();
        state.getPropagator().propagate();

        BufferedImage g0 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        List<LabelOverlay.LabelField> fields = LabelOverlay.labelFields(c, g0.createGraphics());
        assertEquals(2, fields.size(), "labels only, not the body of a Text component");
        List<Rectangle> labels = new ArrayList<>();
        for (LabelOverlay.LabelField f : fields) {
            Rectangle r = new Rectangle(f.bounds);
            r.grow(2, 2);
            labels.add(r);
        }

        BufferedImage orig = render(c, state, false);
        BufferedImage filt = render(c, state, true);
        int inLabelInk = 0;
        int differOutside = 0;
        int inkOutside = 0;
        for (int y = 0; y < 400; y++) {
            for (int x = 0; x < 700; x++) {
                boolean inLabel = false;
                for (Rectangle r : labels) {
                    inLabel |= r.contains(x, y);
                }
                int a = orig.getRGB(x, y) & 0xFFFFFF;
                int f = filt.getRGB(x, y) & 0xFFFFFF;
                if (inLabel) {
                    if (a != 0xFFFFFF && f == 0xFFFFFF) {
                        inLabelInk++;
                    }
                } else {
                    if (a != f) {
                        differOutside++;
                    }
                    if (a != 0xFFFFFF) {
                        inkOutside++;
                    }
                }
            }
        }
        assertTrue(inLabelInk > 20, "the original labels were drawn and are now gone: " + inLabelInk);
        assertEquals(0, differOutside, "nothing else changed");
        assertTrue(inkOutside > 200, "components are still drawn");
    }

    @Test
    void busNamesOnLongNamedBuses() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component res = b.add("Wiring", "Pin", 100, 100, "width", "32", "label", "ALUResult");
        b.wire(Location.create(100, 100), Location.create(240, 100));
        Component bit = b.add("Wiring", "Pin", 100, 200, "label", "zero");
        b.wire(Location.create(100, 200), Location.create(240, 200));
        Component s = b.add("Wiring", "Pin", 100, 300, "width", "8", "label", "imm");
        b.wire(Location.create(100, 300), Location.create(130, 300));
        b.commit();
        Circuit c = file.getMainCircuit();
        Map<Wire, String> names = LabelOverlay.busNames(c);
        assertEquals(1, names.size(), names.toString());
        assertEquals("ALUResult[31:0]", names.values().iterator().next());
        assertNotNull(res);
        assertNotNull(bit);
        assertNotNull(s);
    }

    @Test
    void hoverShowsPathLabelInputsWidthAndNets() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component and = b.add("Gates", "AND Gate", 300, 200, "inputs", "3", "width", "4");
        b.tunnel(and, 0, "sum");
        Component reg = b.add("Memory", "Register", 500, 300, "width", "8", "label", "PC");
        b.wire(Location.create(600, 100), Location.create(700, 100));
        b.commit();
        CircuitState state = new Project(file).getCircuitState();

        List<String> gate = HoverInfo.lines(state, Location.create(290, 200));
        assertEquals("main › AND #1", gate.get(0));
        assertTrue(gate.get(1).contains("3") && gate.get(1).contains("4"), gate.toString());
        assertTrue(gate.get(2).contains("out = sum"), gate.toString());

        List<String> r = HoverInfo.lines(state, Location.create(480, 300));
        assertEquals("main › PC", r.get(0));
        assertTrue(r.get(1).contains("PC") && r.get(1).contains("8"), r.toString());

        List<String> w = HoverInfo.lines(state, Location.create(650, 100));
        assertEquals(2, w.get(0).split(" › ").length, w.toString());
        assertNull(HoverInfo.lines(state, Location.create(50, 50)));
        assertTrue(HoverInfo.html(gate).startsWith("<html><b>main › AND #1</b>"));
        assertTrue(HoverInfo.tip(state, Location.create(290, 200), null).contains("main › AND #1"));
        assertNull(HoverInfo.tip(state, Location.create(50, 50), null));
        assertNotNull(reg);
    }

    /** 스플리터 팔 라벨: 범위와(있으면) 팔 이름, 팔 순서대로 팔 끝에. */
    @Test
    void splitterArmLabelsShowRangesAndNames() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);
        Component sp = b.add("Wiring", "Splitter", 300, 200, "fanout", "2", "incoming", "32");
        b.commit();
        List<LabelOverlay.ArmLabel> plain = LabelOverlay.armLabels(file, c, sp);
        assertEquals(2, plain.size());
        assertEquals("[15:0]", plain.get(0).text);
        assertEquals("[31:16]", plain.get(1).text);
        assertEquals(sp.getEnds().get(1).getLocation(), plain.get(0).end);

        kr.ac.hallym.hcs.app.splitter.SplitterSpec spec = kr.ac.hallym.hcs.app.splitter.SplitterSpec
                .parse("15:0, 31:16", 32, false).withNames(java.util.Arrays.asList("imm", "upper"));
        kr.ac.hallym.hcs.app.splitter.SplitterEdits.setNames(file, c, sp.getLocation(), spec);
        List<LabelOverlay.ArmLabel> named = LabelOverlay.armLabels(file, c, sp);
        assertEquals("[15:0] imm", named.get(0).text);
        assertEquals("[31:16] upper", named.get(1).text);
    }

    /** 팔레트는 10색 이상이고, 흰 바탕에서 보이며 서로 떨어져 있다(검토 반영 1). */
    @Test
    void paletteIsLargeAndDistinct() {
        Color[] p = TunnelColors.PALETTE;
        assertTrue(p.length >= 10, "at least 10 colors");
        for (int i = 0; i < p.length; i++) {
            double lum = (0.2126 * p[i].getRed() + 0.7152 * p[i].getGreen() + 0.0722 * p[i].getBlue()) / 255;
            assertTrue(lum < 0.75, "visible on white: " + Integer.toHexString(p[i].getRGB()));
            for (int j = i + 1; j < p.length; j++) {
                double d = Math.sqrt(Math.pow(p[i].getRed() - p[j].getRed(), 2)
                        + Math.pow(p[i].getGreen() - p[j].getGreen(), 2) + Math.pow(p[i].getBlue() - p[j].getBlue(), 2));
                assertTrue(d > 60, i + " vs " + j + " too close: " + d);
            }
        }
    }

    private static java.util.List<java.awt.Point> at(int... xy) {
        java.util.List<java.awt.Point> ret = new ArrayList<>();
        for (int i = 0; i < xy.length; i += 2) {
            ret.add(new java.awt.Point(xy[i], xy[i + 1]));
        }
        return ret;
    }

    /** 가까운 다른 이름(03b의 pc·four·pc4)은 다른 색, 같은 이름은 같은 색, 결과는 입력 순서와 무관(검토 반영 1). */
    @Test
    void nearbyNamesGetDifferentColors() {
        Map<String, java.util.List<java.awt.Point>> t = new java.util.LinkedHashMap<>();
        t.put("pc", at(100, 100, 3000, 3000));
        t.put("four", at(160, 100));
        t.put("pc4", at(220, 100));
        t.put("far", at(5000, 100));
        Map<String, Integer> a = TunnelColors.assign(t, new java.util.HashMap<>());
        assertEquals(3, new HashSet<>(java.util.Arrays.asList(a.get("pc"), a.get("four"), a.get("pc4"))).size());

        // 12개가 한 화면에 모여도 모두 다른 색
        Map<String, java.util.List<java.awt.Point>> crowd = new java.util.HashMap<>();
        for (int i = 0; i < TunnelColors.PALETTE.length; i++) {
            crowd.put("s" + i, at(100 + 20 * i, 100));
        }
        assertEquals(TunnelColors.PALETTE.length,
                new HashSet<>(TunnelColors.assign(crowd, new java.util.HashMap<>()).values()).size());

        // 입력 순서가 달라도 같은 결과
        Map<String, java.util.List<java.awt.Point>> rev = new java.util.LinkedHashMap<>();
        java.util.List<String> keys = new ArrayList<>(t.keySet());
        Collections.reverse(keys);
        for (String k : keys) {
            rev.put(k, t.get(k));
        }
        assertEquals(a, TunnelColors.assign(rev, new java.util.HashMap<>()));

        // 직접 지정한 색은 그대로이고 이웃은 그 색을 피한다
        Map<String, Integer> fixed = new java.util.HashMap<>();
        fixed.put("four", a.get("pc"));
        Map<String, Integer> b = TunnelColors.assign(t, fixed);
        assertEquals(a.get("pc"), b.get("four"));
        assertTrue(!b.get("pc").equals(b.get("four")));
    }

    /** 서브회로 상자 포트 이름: 넓은 상자는 9px, 좁은 기본 상자는 맞는 만큼 줄이고 5px 아래로는 가지 않는다. */
    @Test
    void subcircuitPortNamesShrinkToFitTheBox() {
        assertEquals(9f, LabelOverlay.portPx(60, 20, 9f), "fits at full size");
        assertEquals(4.5f * 2, LabelOverlay.portPx(20, 20, 9f));
        assertEquals(6.75f, LabelOverlay.portPx(15, 20, 9f), 1e-6, "30px default box, 20px name at 9px");
        assertEquals(LabelOverlay.PORT_MIN_PX, LabelOverlay.portPx(11, 60, 9f), "long names stop at the minimum");
    }

    /** 팔 라벨은 팔 간격(10px) 안에서 가장 크고, 흰 바탕 대비 7:1 이상이다(검토 2차 E). */
    @Test
    void splitterArmLabelsAreLargeAndDarkEnough() {
        assertTrue(LabelOverlay.ARM_PX > 7f && LabelOverlay.ARM_PX <= 9f, "fits 10px arm spacing: " + LabelOverlay.ARM_PX);
        assertTrue(LabelOverlay.ARM_PX * 2 >= 16f, "at 200% at least 16 screen px");
        assertTrue(contrast(LabelOverlay.ARM_COLOR, java.awt.Color.WHITE) >= 7.0,
                "contrast " + contrast(LabelOverlay.ARM_COLOR, java.awt.Color.WHITE));
        assertTrue(LabelOverlay.ARM_BACKGROUND.getAlpha() >= 220);
    }

    /** WCAG 2 대비. */
    static double contrast(java.awt.Color a, java.awt.Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(java.awt.Color c) {
        double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) {
            v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }
}
