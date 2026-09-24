/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * D-040 라벨 빼기의 경계 사례: 같은 글자의 라벨·핀 라벨·글자 부품·터널이 한 화면에 여럿 있고, 터널 글자가 다른
 * 라벨과 좌표까지 똑같이 겹치고, 확대 비율이 바뀌고, 끌고 있는 부품이 있어도 빠지는 것은 라벨 부품 자신의
 * 라벨뿐이다. 원조 그리기가 부르는 drawString을 모두 기록해 호출 단위로 비교한다.
 */
class LabelFilterTest {
    @TempDir
    Path tmp;

    /** drawString을 기록하는 Graphics(자식도 같은 기록에 쓴다). */
    static final class Recorder extends DelegatingGraphics {
        final List<String> log;

        Recorder(Graphics2D g, List<String> log) {
            super(g);
            this.log = log;
        }

        @Override
        public void drawString(String str, int x, int y) {
            log.add(FilterGraphics.key(str, x, y));
            g.drawString(str, x, y);
        }

        @Override
        public Graphics create() {
            return new Recorder((Graphics2D) g.create(), log);
        }
    }

    private static Recorder recorder(double zoom) {
        BufferedImage img = new BufferedImage(1600, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(zoom, zoom); // 원조 캔버스처럼 확대를 Graphics 변환으로
        return new Recorder(g, new ArrayList<>());
    }

    /** 원조 그리기 그대로의 drawString 기록. */
    private static List<String> original(Circuit c, CircuitState s, Collection<Component> hidden, double zoom) {
        Recorder r = recorder(zoom);
        c.draw(new ComponentDrawContext(new javax.swing.JPanel(), c, s, r, r), hidden);
        return r.log;
    }

    /** 거른 그리기의 drawString 기록과 뺀 라벨의 열쇠. */
    private static List<String> filtered(Circuit c, CircuitState s, Collection<Component> hidden, double zoom,
            List<String> expectedDropped) {
        Recorder r = recorder(zoom);
        List<LabelOverlay.LabelField> fields = LabelOverlay.labelFields(c, r);
        for (LabelOverlay.LabelField f : fields) {
            if (!hidden.contains(f.comp)) {
                expectedDropped.add(FilterGraphics.key(f.text, f.drawX, f.drawY));
            }
        }
        FilterGraphics fg = LabelOverlay.filter(r, c, hidden, fields);
        c.draw(new ComponentDrawContext(new javax.swing.JPanel(), c, s, r, fg), hidden);
        return r.log;
    }

    private static void assertOnlyLabelsDropped(List<String> orig, List<String> filt, List<String> dropped) {
        List<String> expect = new ArrayList<>(orig);
        for (String k : dropped) {
            assertTrue(expect.remove(k), "label was drawn originally: " + k);
        }
        List<String> a = new ArrayList<>(expect);
        List<String> b = new ArrayList<>(filt);
        Collections.sort(a);
        Collections.sort(b);
        assertEquals(a, b, "everything except the labels themselves is still drawn");
    }

    private static int count(List<String> log, String key) {
        int n = 0;
        for (String k : log) {
            if (k.equals(key)) {
                n++;
            }
        }
        return n;
    }

    @Test
    void sameTextEverywhereOnlyOwnLabelsDisappear() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);
        b.add("Wiring", "Pin", 200, 100, "label", "A");
        b.add("Wiring", "Pin", 600, 100, "facing", "west", "output", "true", "label", "A");
        Component reg = b.add("Memory", "Register", 400, 300, "width", "8", "label", "A");
        b.add("Gates", "AND Gate", 400, 500, "label", "A");
        b.add("Wiring", "Tunnel", 300, 600, "label", "A");
        b.add("Base", "Text", 500, 650, "text", "A");
        // 같은 자리에 겹친 두 핀(같은 라벨)
        b.add("Wiring", "Pin", 200, 400, "label", "A");
        b.add("Wiring", "Pin", 200, 400, "label", "A");
        b.commit();

        // 터널·글자 부품은 Graphics를 옮긴 뒤 작은 상대 좌표로 글자를 그린다. 그 좌표에 딱 맞게 라벨 달린 레지스터를
        // 하나 더 놓아, 글자와 좌표만으로 거르면 터널 글자까지 사라지는 경우를 만든다.
        CircuitState s0 = new Project(file).getCircuitState();
        List<String> base = original(c, s0, Collections.<Component>emptySet(), 1.0);
        List<String> labelKeys = new ArrayList<>();
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D mg = img.createGraphics();
        for (LabelOverlay.LabelField f : LabelOverlay.labelFields(c, mg)) {
            labelKeys.add(FilterGraphics.key(f.text, f.drawX, f.drawY));
        }
        String collide = null;
        for (String k : base) {
            if (k.startsWith("A\u0000") && !labelKeys.contains(k)) {
                collide = k; // 터널이나 글자 부품의 "A"
                break;
            }
        }
        assertTrue(collide != null, base.toString());
        String[] xy = collide.substring(2).split(",");
        Component probe = new CircuitBuilder(file, c).add("Memory", "Register", 1000, 1000, "label", "A");
        int[] have = LabelOverlay.drawPoint(LabelOverlay.textField(probe), mg);
        CircuitBuilder b2 = new CircuitBuilder(file, c);
        b2.add("Memory", "Register", 1000 + Integer.parseInt(xy[0]) - have[0],
                1000 + Integer.parseInt(xy[1]) - have[1], "label", "A");
        b2.commit();

        CircuitState state = new Project(file).getCircuitState();
        state.getPropagator().propagate();
        for (double zoom : new double[] {1.0, 0.5, 2.0, 1.5}) {
            List<String> orig = original(c, state, Collections.<Component>emptySet(), zoom);
            List<String> dropped = new ArrayList<>();
            List<String> filt = filtered(c, state, Collections.<Component>emptySet(), zoom, dropped);
            assertEquals(7, dropped.size(), "labels of 2+2 pins, 2 registers and a gate");
            assertOnlyLabelsDropped(orig, filt, dropped);
            if (zoom == 1.0) {
                assertEquals(2, count(orig, collide), "a label sits exactly on the tunnel/text coordinates");
                assertEquals(1, count(filt, collide), "the tunnel/text glyphs stay");
            }
        }

        // 끌고 있는 부품(원조가 그리지 않음)이 있어도 순서가 맞다
        List<Component> hidden = Collections.singletonList(reg);
        List<String> orig = original(c, state, hidden, 1.0);
        List<String> dropped = new ArrayList<>();
        List<String> filt = filtered(c, state, hidden, 1.0, dropped);
        assertEquals(6, dropped.size());
        assertOnlyLabelsDropped(orig, filt, dropped);
    }
}
