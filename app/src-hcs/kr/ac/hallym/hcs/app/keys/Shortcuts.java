/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.keys;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.PokeTool;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.move.MoveGesture;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.edit.CircuitEdits;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;

/**
 * 조작과 단축키(#78, PLAN.md 11.7). 선택·편집 도구에서 방향키는 한 칸 이동(원조의 연결 유지 이동과 같은 방법),
 * R은 시계 방향 회전(Shift+R 반대), ?는 단축키 표. Ctrl+클릭은 입력 핀·버튼을 찌르기 도구처럼 누르고, 입력 핀
 * 더블클릭은 값 넣기. 포트 위에 마우스를 올리면 포트 이름과 폭. 단축키 사용자 설정은 4b.
 */
public final class Shortcuts {
    /** 단축키 표(? 창과 테스트가 같은 표를 쓴다): 키 표기 → 설명 문구 키. */
    public static final Map<String, String> TABLE = new LinkedHashMap<>();

    static {
        TABLE.put("← ↑ → ↓", "keys.nudge");
        TABLE.put("R / Shift+R", "keys.rotate");
        TABLE.put("Ctrl+D", "keys.duplicate");
        TABLE.put("Delete", "keys.delete");
        TABLE.put("Ctrl+Click", "keys.poke");
        TABLE.put("Double-click", "keys.value");
        TABLE.put("P", "keys.probe");
        TABLE.put("Ctrl+Wheel", "keys.zoomWheel");
        TABLE.put("Ctrl+= / Ctrl+-", "keys.zoomStep");
        TABLE.put("Ctrl+0 / Ctrl+1", "keys.zoomFit");
        TABLE.put("F", "keys.zoomSel");
        TABLE.put("Space+Drag", "keys.pan");
        TABLE.put("Ctrl+2 … Ctrl+9", "keys.tools");
        TABLE.put("Ctrl+F", "keys.find");
        TABLE.put("?", "keys.help");
    }

    private final Canvas canvas;
    private final PokeTool poke = new PokeTool();
    private boolean poking;

    public Shortcuts(Canvas canvas) {
        this.canvas = canvas;
    }

    private boolean editing() {
        Tool t = canvas.getProject().getTool();
        return t instanceof EditTool || t instanceof SelectTool;
    }

    /** 캔버스의 키 처리 앞에서 부른다. 우리가 처리했으면 true(원조 도구에 넘기지 않음). */
    public boolean keyPressed(KeyEvent e) {
        if (e.getKeyChar() == '?' && !(canvas.getProject().getTool() instanceof com.cburch.logisim.tools.TextTool)) {
            showTable();
            return true;
        }
        if (!editing()) {
            return false;
        }
        Selection sel = canvas.getSelection();
        int mods = e.getModifiersEx();
        switch (e.getKeyCode()) {
        case KeyEvent.VK_LEFT:
            return mods == 0 && nudge(sel, -10, 0);
        case KeyEvent.VK_RIGHT:
            return mods == 0 && nudge(sel, 10, 0);
        case KeyEvent.VK_UP:
            return mods == 0 && nudge(sel, 0, -10);
        case KeyEvent.VK_DOWN:
            return mods == 0 && nudge(sel, 0, 10);
        case KeyEvent.VK_R:
            if ((mods & ~KeyEvent.SHIFT_DOWN_MASK) == 0 && !sel.isEmpty()) {
                rotate(sel.getComponents(), (mods & KeyEvent.SHIFT_DOWN_MASK) == 0);
                return true;
            }
            return false;
        default:
            return false;
        }
    }

    /** 원조 선택 도구가 끌어 옮길 때와 같이 연결을 유지하며 한 칸 옮긴다. */
    boolean nudge(Selection sel, int dx, int dy) {
        if (sel.isEmpty()) {
            return false;
        }
        Project proj = canvas.getProject();
        if (!proj.getLogisimFile().contains(canvas.getCircuit()) || sel.hasConflictWhenMoved(dx, dy)) {
            return true;
        }
        MoveGesture g = new MoveGesture((gesture, x, y) -> { }, canvas.getCircuit(),
                sel.getAnchoredComponents());
        ReplacementMap repl = g.forceRequest(dx, dy).getReplacementMap();
        proj.doAction(SelectionActions.translate(sel, dx, dy, repl));
        return true;
    }

    /** 방향 있는 부품을 90도 돌린다(시계 방향: 오른쪽→아래→왼쪽→위). */
    void rotate(Collection<Component> comps, boolean clockwise) {
        List<Component> faced = new ArrayList<>();
        for (Component c : comps) {
            if (c.getAttributeSet().getAttribute("facing") != null) {
                faced.add(c);
            }
        }
        if (faced.isEmpty()) {
            return;
        }
        com.cburch.logisim.circuit.CircuitMutation m = rotation(canvas.getCircuit(), faced, clockwise);
        canvas.getProject().doAction(m.toAction(() -> Messages.get("keys.rotateAction")));
    }

    /** 회전 변경(GUI 없이 테스트). */
    public static com.cburch.logisim.circuit.CircuitMutation rotation(Circuit circuit, List<Component> comps,
            boolean clockwise) {
        com.cburch.logisim.circuit.CircuitMutation m = new com.cburch.logisim.circuit.CircuitMutation(circuit);
        for (Component c : comps) {
            Direction d = c.getAttributeSet().getValue(com.cburch.logisim.instance.StdAttr.FACING);
            if (d != null) {
                m.set(c, com.cburch.logisim.instance.StdAttr.FACING, next(d, clockwise));
            }
        }
        return m;
    }

    /** 다음 방향(시계 방향이면 오른쪽→아래→왼쪽→위). */
    public static Direction next(Direction d, boolean clockwise) {
        Direction[] cw = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
        for (int i = 0; i < 4; i++) {
            if (cw[i] == d) {
                return cw[(i + (clockwise ? 1 : 3)) % 4];
            }
        }
        return d;
    }

    /** 캔버스 마우스 처리 앞에서: 선택·편집 도구에서 Ctrl+클릭한 입력 핀·버튼은 찌르기 도구처럼. */
    public boolean mouse(MouseEvent e, java.awt.Graphics g) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            if (editing() && e.isControlDown() && javax.swing.SwingUtilities.isLeftMouseButton(e)
                    && pokeable(at(e)) != null) {
                poking = true;
                poke.mousePressed(canvas, g, e);
                return true;
            }
            if (editing() && e.getClickCount() == 2 && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                Component pin = inputPin(at(e));
                if (pin != null) {
                    askValue(pin);
                    return true;
                }
            }
        } else if (poking && e.getID() == MouseEvent.MOUSE_RELEASED) {
            poke.mouseReleased(canvas, g, e);
            poking = false;
            return true;
        } else if (poking && e.getID() == MouseEvent.MOUSE_DRAGGED) {
            return true;
        }
        return false;
    }

    private static Location at(MouseEvent e) {
        return Location.create(e.getX(), e.getY());
    }

    Component pokeable(Location p) {
        for (Component c : canvas.getCircuit().getAllContaining(p)) {
            String f = c.getFactory().getName();
            if ((f.equals("Pin") && !isOutputPin(c)) || f.equals("Button")) {
                return c;
            }
        }
        return null;
    }

    Component inputPin(Location p) {
        for (Component c : canvas.getCircuit().getAllContaining(p)) {
            if (c.getFactory().getName().equals("Pin") && !isOutputPin(c)) {
                return c;
            }
        }
        return null;
    }

    static boolean isOutputPin(Component c) {
        return Boolean.TRUE.equals(c.getAttributeSet().getValue(Pin.ATTR_TYPE));
    }

    /** 입력 핀 값 넣기: 0x1F, 0b1011, 31, -3. */
    void askValue(Component pin) {
        int width = pin.getEnds().get(0).getWidth().getWidth();
        Object s = JOptionPane.showInputDialog(canvas.getProject().getFrame(),
                Messages.get("keys.valuePrompt", Names.name(canvas.getCircuit(), pin), width),
                Messages.get("keys.valueTitle"), JOptionPane.PLAIN_MESSAGE, null, null, "");
        if (s == null) {
            return;
        }
        Long v = parseValue(s.toString(), width);
        if (v == null) {
            JOptionPane.showMessageDialog(canvas.getProject().getFrame(), Messages.get("keys.badValue", s));
            return;
        }
        setPinValue(canvas.getProject().getCircuitState(), pin, v);
        canvas.getProject().getSimulator().requestPropagate();
        canvas.repaint();
    }

    /**
     * 입력 핀에 값을 넣는다. 원조 찌르기 도구가 핀을 누를 때와 같은 경로(Pin.setValue)라 시뮬레이션 상태만 바뀌고
     * .circ에는 남지 않는다.
     */
    public static void setPinValue(CircuitState state, Component pin, long v) {
        int width = pin.getEnds().get(0).getWidth().getWidth();
        InstanceState is = state.getInstanceState(pin);
        Pin.FACTORY.setValue(is, Value.createKnown(BitWidth.create(width), (int) v));
    }

    /** 지금 입력 핀이 내는 값(테스트용). */
    static Value pinValue(CircuitState state, Component pin) {
        return Pin.FACTORY.getValue(state.getInstanceState(pin));
    }

    /** 값 해석: 0x·0b 접두사, 10진(음수는 2의 보수). 폭을 넘으면 null. */
    public static Long parseValue(String text, int width) {
        String t = text.trim().replace("_", "").replace(" ", "");
        if (t.isEmpty()) {
            return null;
        }
        long v;
        try {
            if (t.startsWith("0x") || t.startsWith("0X")) {
                v = Long.parseLong(t.substring(2), 16);
            } else if (t.startsWith("0b") || t.startsWith("0B")) {
                v = Long.parseLong(t.substring(2), 2);
            } else {
                v = Long.parseLong(t);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        long mask = width >= 64 ? -1L : (1L << width) - 1;
        if (v < 0) {
            if (-v > (1L << (width - 1))) {
                return null;
            }
            return v & mask;
        }
        return v > mask ? null : v;
    }

    /** 포트 위 툴팁: 포트 이름과 폭. 포트가 아니면 null. */
    public static String portTip(Circuit circuit, Location p) {
        // 포트는 부품 경계에 있어 경계 밖 몇 px도 포트로 본다
        for (Component c : circuit.getNonWires()) {
            if (!c.getBounds().expand(5).contains(p)) {
                continue;
            }
            for (int i = 0; i < c.getEnds().size(); i++) {
                EndData end = c.getEnds().get(i);
                Location e = end.getLocation();
                if (Math.abs(e.getX() - p.getX()) <= 4 && Math.abs(e.getY() - p.getY()) <= 4) {
                    return Messages.get("keys.portTip", Names.port(circuit, c, i), end.getWidth().getWidth(),
                            Kinds.portName(c, i));
                }
            }
        }
        return null;
    }

    void showTable() {
        StringBuilder sb = new StringBuilder("<html><table>");
        for (Map.Entry<String, String> e : TABLE.entrySet()) {
            sb.append("<tr><td><b>").append(e.getKey()).append("</b></td><td>").append(Messages.get(e.getValue()))
                    .append("</td></tr>");
        }
        sb.append("</table></html>");
        JOptionPane.showMessageDialog(canvas.getProject().getFrame(), sb.toString(), Messages.get("keys.title"),
                JOptionPane.PLAIN_MESSAGE);
    }

    /** 표의 모든 설명 문구 키(테스트용). */
    static List<String> messageKeys() {
        return Collections.unmodifiableList(new ArrayList<>(TABLE.values()));
    }

    /** 이 부품들 중 방향을 바꿀 수 있는 것(테스트용). */
    static List<Component> withFacing(Collection<Component> comps) {
        List<Component> ret = new ArrayList<>();
        for (Component c : comps) {
            if (CircuitEdits.side(c, 0) != null && c.getAttributeSet().getAttribute("facing") != null) {
                ret.add(c);
            }
        }
        return ret;
    }
}
