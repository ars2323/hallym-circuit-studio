/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.demo;

import java.util.LinkedHashMap;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.splitter.SplitterSpec;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 논리설계 및 실험 튜토리얼의 예제 tests/tutorial/tutorial-logic.circ 생성기(N-18, D-161). MIPS 부품은 없다(원조
 * 2.7.1 그대로 열린다). 학생이 그린 것처럼 선으로 잇고, 세 자리로 나눈다.
 *
 * <ul>
 * <li>왼쪽 위: 입력 핀 A·B, 출력 핀 Y, 그 사이의 빈 자리(영역 메모 "AND Gate"). L3에서 AND 게이트를 놓고 L4에서 잇는다.
 * <li>왼쪽 아래: 입력 핀 P·Q에서 나온 선이 빈 자리(영역 메모 "half_adder")에서 끝난다. L8에서 half_adder를
 * {@link #HALF_ADDER_AT}에 놓으면 두 입력이 선 끝에 닿는다(출력 s·c는 비워 둔다: 받는 쪽 없이 선만 있으면 "값을 내는 것이
 * 없음" 메시지가 된다).
 * <li>오른쪽: 4비트 Register count와 가산기·상수 1의 카운터 고리. 클럭은 빠져 있고(Clock 부품만 옆에 있음, L11·L12),
 * 출력 버스에 여러 진법 Radix Probe(16·10·2진수를 함께 보인다: Hallym MIPS 라이브러리의 부품이지만 MIPS 전용이 아니라
 * 논리설계 및 실험 모드에서도 보인다, v2 추가 3 A-08 정정)와 팔 이름(q0~q3)을 붙인 Splitter, 팔마다 LED.
 * Instruction Memory·Data Memory·Console 같은 MIPS 전용 부품은 없다.
 * </ul>
 */
public final class TutorialLogic {
    /** L8: half_adder 인스턴스를 놓을 자리(부품 위치). 화면의 [건너뛰기]가 같은 자리에 놓는다(electron/src/renderer/app/tutorial/actions.json). */
    public static final Location HALF_ADDER_AT = Location.create(290, 360);

    public Circuit halfAdder;
    public Component a;
    public Component b;
    public Component y;
    public Component register;
    public Component clock;
    public Component splitter;
    public Component probe;
    public Component adder;
    /** half_adder의 포트 이름 → 인스턴스를 {@link #HALF_ADDER_AT}에 놓았을 때 포트 자리(a·b에 선 끝이 있다). */
    public final Map<String, Location> halfAdderEnds = new LinkedHashMap<>();

    private final LogisimFile file;
    private final Library mips;

    private TutorialLogic(LogisimFile file, Library mips) {
        this.file = file;
        this.mips = mips;
    }

    /** mips: 파일에 넣은 Hallym MIPS 라이브러리(Radix Probe 하나만 쓴다). */
    public static TutorialLogic build(LogisimFile file, Library mips) throws Exception {
        TutorialLogic t = new TutorialLogic(file, mips);
        t.halfAdder = t.halfAdder();
        kr.ac.hallym.hcs.app.appear.AutoAppearance
                .action(t.halfAdder, kr.ac.hallym.hcs.app.appear.AutoAppearance.build(t.halfAdder)).doIt(null);
        t.main();
        return t;
    }

    private static Location at(int x, int y) {
        return Location.create(x, y);
    }

    /** 꺾은선: 점들을 차례로 잇는다(모두 가로 또는 세로). */
    static void path(CircuitBuilder b, Location... pts) {
        for (int i = 0; i + 1 < pts.length; i++) {
            if (!pts[i].equals(pts[i + 1])) {
                b.wire(pts[i], pts[i + 1]);
            }
        }
    }

    // ---- 서브회로 half_adder: s = a XOR b, c = a AND b ----

    private Circuit halfAdder() {
        Circuit c = new Circuit("half_adder");
        file.addCircuit(c);
        CircuitBuilder b = new CircuitBuilder(file, c);
        Component pa = b.add("Wiring", "Pin", 100, 100, "tristate", "false", "label", "a");
        Component pb = b.add("Wiring", "Pin", 100, 200, "tristate", "false", "label", "b");
        Component xor = b.add("Gates", "XOR Gate", 300, 120, "inputs", "2");
        Component and = b.add("Gates", "AND Gate", 300, 240, "inputs", "2");
        Component s = b.add("Wiring", "Pin", 400, 120, "facing", "west", "output", "true", "label", "s",
                "labelloc", "east");
        Component co = b.add("Wiring", "Pin", 400, 240, "facing", "west", "output", "true", "label", "c",
                "labelloc", "east");
        Location xa = CircuitBuilder.port(xor, 1);
        Location xb = CircuitBuilder.port(xor, 2);
        Location aa = CircuitBuilder.port(and, 1);
        Location ab = CircuitBuilder.port(and, 2);
        // a: 핀 → x=150에서 갈라 XOR·AND의 윗 입력; b: 핀 → x=180에서 갈라 아랫 입력(가로·세로가 끝점 없이만 교차)
        path(b, pa.getLocation(), at(150, 100), at(150, xa.getY()), xa);
        path(b, at(150, 100), at(150, aa.getY()), aa);
        path(b, pb.getLocation(), at(180, 200), at(180, xb.getY()), xb);
        path(b, at(180, 200), at(180, ab.getY()), ab);
        path(b, xor.getLocation(), s.getLocation());
        path(b, and.getLocation(), co.getLocation());
        b.commit();
        return c;
    }

    // ---- main ----

    private void main() throws Exception {
        Circuit c = file.getMainCircuit();
        CircuitBuilder b = new CircuitBuilder(file, c);

        // 왼쪽 위: A·B → (AND 자리) → Y. 입력 핀은 떠 있지 않게(원조 GUI로 놓은 핀처럼 0에서 시작)
        a = b.add("Wiring", "Pin", 120, 120, "tristate", "false", "label", "A");
        this.b = b.add("Wiring", "Pin", 120, 200, "tristate", "false", "label", "B");
        y = b.add("Wiring", "Pin", 440, 160, "facing", "west", "output", "true", "label", "Y", "labelloc", "east");
        memo(c, 200, 90, 180, 140, 0, "AND Gate");

        // 왼쪽 아래: half_adder 자리. 인스턴스를 HALF_ADDER_AT에 놓았을 때의 포트 자리까지 선을 긋는다.
        Component probe = halfAdder.getSubcircuitFactory().createComponent(HALF_ADDER_AT,
                halfAdder.getSubcircuitFactory().createAttributeSet());
        Map<String, Location> ports = DemoDatapath.ports(probe);
        halfAdderEnds.putAll(ports);
        Location pa = ports.get("a");
        Location pb = ports.get("b");
        Component p = b.add("Wiring", "Pin", 120, pa.getY(), "tristate", "false", "label", "P");
        Component q = b.add("Wiring", "Pin", 120, pb.getY(), "tristate", "false", "label", "Q");
        path(b, p.getLocation(), pa);
        path(b, q.getLocation(), pb);
        Bounds hb = probe.getBounds();
        int x0 = (hb.getX() - 20) / 10 * 10;
        int y0 = (hb.getY() - 20) / 10 * 10;
        int x1 = (hb.getX() + hb.getWidth() + 29) / 10 * 10;
        int y1 = (hb.getY() + hb.getHeight() + 29) / 10 * 10;
        memo(c, x0, y0, x1 - x0, y1 - y0, 3, "half_adder");

        // 오른쪽: 4비트 카운터(Register + 가산기 + 상수 1), 클럭 빠짐
        register = b.add("Memory", "Register", 760, 300, "width", "4", "label", "count");
        adder = b.add("Arithmetic", "Adder", 700, 300, "width", "4");
        b.add("Wiring", "Constant", 640, 310, "width", "4", "value", "0x1");
        path(b, at(640, 310), CircuitBuilder.port(adder, 1)); // 1 → 가산기 b
        path(b, adder.getLocation(), CircuitBuilder.port(register, 1)); // 합 → D
        // Q → 버스: 770에서 가산기 a로 돌아가고, 800에서 아래로 Radix Probe, 840에 Splitter
        Location qOut = register.getLocation();
        path(b, qOut, at(770, 300), at(800, 300), at(840, 300));
        path(b, at(770, 300), at(770, 250), at(640, 250), at(640, 290), CircuitBuilder.port(adder, 0));
        path(b, at(800, 300), at(800, 360));
        probe = b.add(mips, "Radix Probe", 800, 360, "width", "4");
        clock = b.add("Wiring", "Clock", 700, 380);

        // 버스를 비트로: 팔 q0~q3 → LED
        SplitterSpec spec = SplitterSpec.parse("0 q0, 1 q1, 2 q2, 3 q3", 4, false);
        java.util.List<String> attrs = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : spec.toStandardAttrs().entrySet()) {
            attrs.add(e.getKey());
            attrs.add(e.getValue());
        }
        splitter = b.add("Wiring", "Splitter", 840, 300, attrs.toArray(new String[0]));
        for (int k = 0; k < 4; k++) {
            Location arm = CircuitBuilder.port(splitter, 1 + k);
            int x = 890 + 30 * k;
            path(b, arm, at(x, arm.getY()), at(x, 210));
            b.add("I/O", "LED", x, 210, "facing", "south");
        }
        b.commit();
        SplitterEdits.setNames(file, c, splitter.getLocation(), spec);
    }

    /** 영역 메모(hcs:ext, v1 AreaMemos와 같은 모양): 자리를 가리키는 상자와 이름. */
    private void memo(Circuit c, int x, int y, int w, int h, int color, String text) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("x", Integer.toString(x));
        m.put("y", Integer.toString(y));
        m.put("w", Integer.toString(w));
        m.put("h", Integer.toString(h));
        m.put("color", Integer.toString(color));
        m.put("text", text);
        CircExtensions.of(file).add(c.getName(), new CircExtension.Item("memo", m));
    }
}
