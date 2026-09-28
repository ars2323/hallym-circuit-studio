/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringGetter;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.labels.TunnelColorStore;
import kr.ac.hallym.hcs.app.labels.TunnelColors;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.app.splitter.SplitterEdits;
import kr.ac.hallym.hcs.app.splitter.SplitterSpec;
import kr.ac.hallym.hcs.app.wiring.WireGuard;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 학생이 직접 정하는 확장 정보를 바꾸는 의도(N-12, D-150): 터널 색({@code edit.tunnelColor}), Splitter 편집기
 * ({@code edit.splitterEdit}), 선 위 새 스플리터({@code edit.splitterSplit}). v1의 동작 객체를 그대로 쓴다
 * ({@link TunnelColorStore#action}, {@link SplitterEdits#change}·{@link SplitterEdits#create}·{@link
 * SplitterEdits#withNames}, 검사기 {@link WireGuard}). 모두 {@code Project.doAction} 한 번 = 되돌리기 한 단계이고, 원조
 * Splitter 속성(fanout·incoming·bitN)과 hcs:ext만 바꾼다(D-024, D-032).
 */
public final class ExtEdits {
    private ExtEdits() {
    }

    // ---- edit.tunnelColor: 터널 우클릭 Tunnel Color ›(v1 EditMenus.tunnel) ----

    /**
     * 터널 색. color는 v1 팔레트 12색 가운데 하나({@code #rrggbb}, 대소문자 무관)이고 null이면 Automatic(항목을 지운다:
     * 이름으로 정하는 자동 색). 같은 회로의 같은 이름 터널은 모두 같은 색이다. 이미 그 색이면 바꾸지 않는다.
     */
    public static Intents.Result tunnelColor(Doc d, Circuit c, Component tunnel, String color) throws RpcError {
        Intents.editable(d, c);
        String label = TunnelColorStore.name(tunnel);
        if (label == null) {
            throw RpcError.params("component " + d.ids().of(tunnel) + " is not a tunnel with a label");
        }
        Color col = null;
        if (color != null) {
            col = paletteColor(color);
            if (col == null) {
                throw RpcError.params("color must be one of the tunnel palette colors (#rrggbb): " + color);
            }
        }
        Color now = TunnelColorStore.get(d.file(), c, label);
        if (now == null ? col == null : col != null && now.getRGB() == col.getRGB()) {
            return Intents.Result.unchanged("same");
        }
        d.show(c);
        d.project().doAction(keepingOrder(TunnelColorStore.action(d.file(), c, label, col), d, c));
        return new Intents.Result(true, null, null);
    }

    /** 팔레트의 색(v1 메뉴가 주는 색만). 아니면 null. */
    static Color paletteColor(String text) {
        if (!text.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        int rgb = Integer.parseInt(text.substring(1), 16);
        for (Color p : TunnelColors.PALETTE) {
            if ((p.getRGB() & 0xFFFFFF) == rgb) {
                return p;
            }
        }
        return null;
    }

    // ---- edit.splitterEdit: 스플리터 우클릭 Edit Splitter…(v1 SplitterEditor.editExisting) ----

    /**
     * Splitter 편집기의 Apply. ranges는 편집기의 범위 글({@code 31:26, 25:21, 20:16, 15:0}, 이름을 붙여도 된다:
     * {@code 31:26 op}; {@code 4x8}), names는 팔 이름 칸(위 팔부터, 없으면 글의 이름·전의 이름), lsbTop은 "LSB on top".
     * 팔 자리가 바뀌면 검사기(W-05)를 거쳐 원조 속성을 바꾸고, 팔 이름은 hcs:ext에 둔다. 바뀐 것이 없으면
     * {@code changed:false}, 검사기가 막으면 {@code outcome:"refused"}.
     */
    public static Intents.Result splitterEdit(Doc d, Circuit c, Component splitter, String ranges, List<String> names,
            boolean lsbTop) throws RpcError {
        Intents.editable(d, c);
        if (!splitter.getFactory().getName().equals("Splitter")) {
            throw RpcError.params("component " + d.ids().of(splitter) + " is not a splitter");
        }
        SplitterSpec cur = SplitterEdits.specOf(d.file(), c, splitter);
        SplitterSpec s = dialog(cur, ranges, names, lsbTop);
        boolean attrs = !s.toStandardAttrs().equals(cur.toStandardAttrs());
        boolean named = !s.names().equals(cur.names());
        if (!attrs && !named) {
            return Intents.Result.unchanged("same");
        }
        StringGetter name = () -> Messages.get("splitter.editAction");
        Action base = null;
        if (attrs) {
            // 팔 자리가 바뀌면 새 팔 끝은 옛 팔 끝·묶인 끝 자리에서만 옛 선에 닿아도 된다(v1 W-05)
            CircuitMutation m = SplitterEdits.change(c, splitter, s);
            if (!WireGuard.problems(d.project(), c, m, WireGuard.ends(splitter)).isEmpty()) {
                return Intents.Result.unchanged("refused");
            }
            base = m.toAction(name);
        }
        d.show(c);
        d.project().doAction(keepingOrder(SplitterEdits.withNames(base, name.get(), d.file(), c,
                splitter.getLocation(), s), d, c));
        return new Intents.Result(true, null, null);
    }

    // ---- edit.splitterSplit: 여러 비트 선 우클릭 Split Bits…(v1 SplitterEditor.createNew) ----

    /**
     * 여러 비트 선 위 at(가까운 선 위 격자점)에 동쪽을 보는 새 스플리터. 편집기는 32비트면 MIPS R 형식, 아니면 반씩으로
     * 열리고, ranges·names·lsbTop은 {@link #splitterEdit}과 같다. 비트 하나만 뽑기(Take One Bit)는 ranges가 그
     * 비트 하나({@code "5"})다. result의 id는 새 스플리터.
     */
    public static Intents.Result splitterSplit(Doc d, Circuit c, Wire wire, Location at, String ranges,
            List<String> names, boolean lsbTop) throws RpcError {
        Intents.editable(d, c);
        int width = wireWidth(c, wire);
        if (width <= 1) {
            throw RpcError.params("wire " + d.ids().of(wire) + " carries " + Math.max(width, 0)
                    + " bit(s): Split Bits needs a multi-bit wire");
        }
        Location p = onWire(wire, at);
        SplitterSpec init;
        try {
            init = width == 32 ? SplitterSpec.Preset.MIPS_R.spec(true)
                    : SplitterSpec.parse((width - 1) + ":" + (width / 2) + ", " + (width / 2 - 1) + ":0", width, true);
        } catch (SplitterSpec.ParseException e) {
            throw RpcError.params("cannot split a " + width + "-bit wire: " + e.getMessage());
        }
        SplitterSpec s = dialog(init, ranges, names, lsbTop);
        StringGetter name = () -> Messages.get("splitter.createAction");
        CircuitMutation m = SplitterEdits.create(d.file(), c, p, Direction.EAST, s);
        if (!WireGuard.problems(d.project(), c, m, Collections.singletonList(p)).isEmpty()) {
            return Intents.Result.unchanged("refused");
        }
        List<Component> before = splittersAt(c, p);
        d.show(c);
        d.project().doAction(keepingOrder(SplitterEdits.withNames(m.toAction(name), name.get(), d.file(), c, p, s), d, c));
        Component added = null;
        for (Component x : splittersAt(c, p)) {
            if (!before.contains(x)) {
                added = x;
            }
        }
        return new Intents.Result(true, null, added);
    }

    /**
     * v1 동작을 감싸, 되돌리면 그 회로의 확장 항목이 차례까지 전과 같게 한다. v1은 항목을 지우고 끝에 다시 넣어 되돌리므로
     * 편집하고 되돌린 파일의 {@code <hcs:ext>} 줄 차례가 바뀌었다(열고 저장만 한 파일은 그대로라는 D-149의 뜻을 편집
     * 뒤 되돌리기까지 넓힌다). 하는 일과 이름은 v1 그대로다.
     */
    static Action keepingOrder(Action inner, Doc d, Circuit c) {
        return new Action() {
            private String circuit;
            private List<CircExtension.Item> before;

            @Override
            public String getName() {
                return inner.getName();
            }

            @Override
            public void doIt(Project proj) {
                circuit = c.getName();
                before = CircExtensions.of(d.file()).items(circuit);
                inner.doIt(proj);
            }

            @Override
            public void undo(Project proj) {
                inner.undo(proj);
                CircExtension ext = CircExtensions.of(d.file());
                for (CircExtension.Item i : ext.items(circuit)) {
                    ext.remove(circuit, i);
                }
                for (CircExtension.Item i : before) {
                    ext.add(circuit, i);
                }
            }

            @Override
            public boolean isModification() {
                return inner.isModification();
            }
        };
    }

    private static List<Component> splittersAt(Circuit c, Location p) {
        List<Component> ret = new ArrayList<>();
        for (Component x : c.getNonWires(p)) {
            if (x.getFactory().getName().equals("Splitter") && x.getLocation().equals(p)) {
                ret.add(x);
            }
        }
        return ret;
    }

    /**
     * 편집기 창에서 한 일을 그대로(v1 {@code SplitterEditor}): 창은 지금 모양(initial)과 그 팔 이름 칸으로 열린다.
     * "LSB on top"을 누르면 칸의 글을 LSB 위로 다시 읽고, 범위 글을 넣으면 다시 읽는다. 다시 읽을 때 이름이 없는 팔은
     * 팔 수가 그대로면 전의 칸 이름을 지킨다. 끝으로 names가 앞 칸부터 채운다.
     */
    static SplitterSpec dialog(SplitterSpec initial, String ranges, List<String> names, boolean lsbTop)
            throws RpcError {
        List<String> fields = initial.names();
        if (lsbTop) {
            fields = shown(fields, parse(initial.toText(), initial.width(), false));
        }
        SplitterSpec s = parse(ranges, initial.width(), !lsbTop);
        fields = shown(fields, s);
        if (names != null) {
            if (names.size() > fields.size()) {
                throw RpcError.params("names has " + names.size() + " entries but the splitter has " + fields.size()
                        + " arms");
            }
            for (int k = 0; k < names.size(); k++) {
                fields.set(k, names.get(k));
            }
        }
        return s.withNames(fields);
    }

    /** v1 {@code SplitterEditor.show}: 새 모양의 이름 칸들. */
    static List<String> shown(List<String> oldFields, SplitterSpec s) {
        List<String> ret = new ArrayList<>();
        for (int i = 0; i < s.arms().size(); i++) {
            String n = s.arms().get(i).name();
            ret.add(n.isEmpty() && i < oldFields.size() && s.arms().size() == oldFields.size() ? oldFields.get(i) : n);
        }
        return ret;
    }

    private static SplitterSpec parse(String text, int width, boolean msbOnTop) throws RpcError {
        try {
            return SplitterSpec.parse(text, width, msbOnTop);
        } catch (SplitterSpec.ParseException e) {
            throw RpcError.params("cannot read the ranges '" + text + "': " + e.getMessage());
        }
    }

    /** 선의 폭(v1 {@code SplitterMenu.width}): 원조가 계산한 그 점의 폭, 모르면 넷의 포트 폭. */
    static int wireWidth(Circuit circuit, Wire w) {
        BitWidth bw = circuit.getWidth(w.getEnd0());
        if (bw != null && bw.getWidth() > 0) {
            return bw.getWidth();
        }
        Netlist.Net net = Netlist.of(circuit).netOf(w);
        return net == null ? 0 : net.width();
    }

    /** 누른 점을 선 위 격자점으로(v1 {@code SplitterMenu.onWire}). */
    static Location onWire(Wire w, Location p) {
        int x = Math.round(p.getX() / 10f) * 10;
        int y = Math.round(p.getY() / 10f) * 10;
        Location a = w.getEnd0();
        Location b = w.getEnd1();
        if (a.getY() == b.getY()) {
            x = Math.max(Math.min(a.getX(), b.getX()), Math.min(Math.max(a.getX(), b.getX()), x));
            return Location.create(x, a.getY());
        }
        y = Math.max(Math.min(a.getY(), b.getY()), Math.min(Math.max(a.getY(), b.getY()), y));
        return Location.create(a.getX(), y);
    }
}
