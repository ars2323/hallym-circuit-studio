/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.instance;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 서브회로 인스턴스 안내(P-02, PLAN.md 11.10). 캔버스 위 띠 하나.
 * <ul>
 * <li>탐색기에서 연 서브회로가 main 안에서 실행 중인 인스턴스가 아니면(자기만의 상태) 알리고 "Go to Instance in
 * main"을 준다. 인스턴스가 여럿이면 경로 목록에서 고른다.</li>
 * <li>인스턴스가 있는 서브회로에서 핀을 고르면 지우거나 옮길 때 끊길 인스턴스 연결 수를, 핀 도구를 들면 핀을 더할 때
 * 모양이 바뀔 인스턴스 수를 미리 보인다.</li>
 * <li>핀을 바꾼 뒤 끊긴 인스턴스 연결을 세어 알리고, 옛 선 끝이 그대로 있으면 새 포트 자리까지 선을 이어 되살린다
 * (새 선은 {@link kr.ac.hallym.hcs.app.wiring.WireGuard}를 거친다).</li>
 * </ul>
 */
public final class InstanceBanner implements ProjectListener {
    private final Project proj;
    private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, Tokens.SPACE_2, 2));
    private final JLabel message = new JLabel();
    private final JLabel go = new JLabel();
    private final JLabel preview = new JLabel();
    private List<List<Component>> paths = Collections.emptyList();
    private List<InstancePaths.PortUse> before;
    private Circuit beforeCircuit;
    private final Selection.Listener selectionListener = e -> refresh();

    private InstanceBanner(Project proj) {
        this.proj = proj;
        panel.setBackground(Tokens.BLUE_TINT);
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.BLUE_TINT_2));
        message.setForeground(Tokens.NAVY);
        preview.setForeground(Tokens.AMBER_TEXT);
        go.setText("<html><u>" + Messages.get("instance.goTo") + "</u></html>");
        go.setForeground(Tokens.BLUE);
        go.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        go.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                goTo(e);
            }
        });
        panel.add(message);
        panel.add(go);
        panel.add(preview);
        panel.setVisible(false);
    }

    /** 창을 만들 때: 띠를 돌려준다(캔버스 위, 시뮬레이션 띠 옆에 둔다). */
    public static JPanel install(Frame frame) {
        InstanceBanner b = new InstanceBanner(frame.getProject());
        frame.getProject().addProjectListener(b);
        SwingUtilities.invokeLater(() -> {
            Selection sel = frame.getProject().getSelection();
            if (sel != null) {
                sel.addListener(b.selectionListener);
            }
            b.refresh();
        });
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(b.panel, BorderLayout.CENTER);
        wrap.putClientProperty(InstanceBanner.class, b);
        return wrap;
    }

    // ---- 띠 ----

    /** 지금 보는 회로가 main 안에서 실행 중인 인스턴스가 아닌가(자기만의 상태로 열렸나). */
    static boolean standalone(Project proj, Circuit cur, CircuitState st) {
        Circuit main = proj.getLogisimFile().getMainCircuit();
        return cur != null && main != null && cur != main && st != null && st.getParentState() == null
                && !InstancePaths.paths(main, cur).isEmpty();
    }

    void refresh() {
        Circuit cur = proj.getCurrentCircuit();
        LogisimFile file = proj.getLogisimFile();
        Circuit main = file.getMainCircuit();
        CircuitState st = proj.getCircuitState();
        boolean alone = standalone(proj, cur, st);
        paths = alone ? InstancePaths.paths(main, cur) : Collections.emptyList();
        message.setText(alone ? Messages.get("instance.standalone", cur.getName(), main.getName()) : "");
        message.setVisible(alone);
        go.setVisible(alone);
        String pv = previewText(cur);
        preview.setText(pv == null ? "" : pv);
        preview.setVisible(pv != null);
        panel.setVisible(alone || pv != null);
        panel.revalidate();
        panel.repaint();
    }

    /** 핀을 고른 경우와 핀 도구를 든 경우의 미리 보기. 인스턴스가 없으면 null. */
    String previewText(Circuit cur) {
        LogisimFile file = proj.getLogisimFile();
        if (cur == null || !hasInstances(file, cur)) {
            return null;
        }
        Selection sel = proj.getSelection();
        List<Component> pins = new ArrayList<>();
        if (sel != null) {
            for (Component c : sel.getComponents()) {
                if (c.getFactory().getName().equals("Pin")) {
                    pins.add(c);
                }
            }
        }
        if (!pins.isEmpty()) {
            List<InstancePaths.PortUse> a = InstancePaths.affected(file, cur, pins);
            if (!a.isEmpty()) {
                return Messages.get("instance.pinPreview", a.size(), InstancePaths.instances(a));
            }
            return null;
        }
        Tool t = proj.getTool();
        if (t instanceof AddTool && ((AddTool) t).getFactory() != null
                && ((AddTool) t).getFactory().getName().equals("Pin")) {
            List<InstancePaths.PortUse> all = InstancePaths.snapshot(file, cur);
            int connected = 0;
            for (InstancePaths.PortUse u : all) {
                connected += u.connected ? 1 : 0;
            }
            return Messages.get("instance.pinAddPreview", cur.getName(), countInstances(all), connected);
        }
        return null;
    }

    static boolean hasInstances(LogisimFile file, Circuit sub) {
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                if (x.getFactory() instanceof SubcircuitFactory
                        && ((SubcircuitFactory) x.getFactory()).getSubcircuit() == sub) {
                    return true;
                }
            }
        }
        return false;
    }

    static int countInstances(List<InstancePaths.PortUse> uses) {
        java.util.Set<Component> s = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (InstancePaths.PortUse u : uses) {
            s.add(u.instance);
        }
        return s.size();
    }

    /** Go to Instance in main: 하나면 바로, 여럿이면 경로 목록. */
    void goTo(MouseEvent e) {
        if (paths.isEmpty()) {
            return;
        }
        if (paths.size() == 1) {
            goTo(paths.get(0));
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        Circuit main = proj.getLogisimFile().getMainCircuit();
        for (List<Component> p : paths) {
            JMenuItem it = new JMenuItem(InstancePaths.describe(main, p));
            it.addActionListener(ev -> goTo(p));
            menu.add(it);
        }
        menu.show(go, 0, go.getHeight());
    }

    /** 경로를 따라 실행 중인 인스턴스의 상태로 간다(원조 시뮬레이션 트리에서 고르는 것과 같다). */
    public void goTo(List<Component> path) {
        Circuit main = proj.getLogisimFile().getMainCircuit();
        CircuitState s = InstancePaths.stateFor(proj.getCircuitState(main), path);
        if (s != null) {
            proj.setCircuitState(s);
        }
    }

    // ---- 핀 변경 뒤 ----

    @Override
    public void projectChanged(ProjectEvent e) {
        int a = e.getAction();
        if (a == ProjectEvent.ACTION_START) {
            Circuit cur = proj.getCurrentCircuit();
            LogisimFile file = proj.getLogisimFile();
            if (cur != null && cur != file.getMainCircuit() && hasInstances(file, cur)) {
                before = InstancePaths.snapshot(file, cur);
                beforeCircuit = cur;
            } else {
                before = null;
            }
        } else if (a == ProjectEvent.ACTION_COMPLETE && before != null) {
            List<InstancePaths.PortUse> b = before;
            Circuit sub = beforeCircuit;
            before = null;
            // 이 사건 안에서 다른 동작을 하지 않는다: 이벤트 뒤에 처리한다
            SwingUtilities.invokeLater(() -> afterPinChange(sub, b));
        }
        if (a == ProjectEvent.ACTION_SET_CURRENT || a == ProjectEvent.ACTION_SET_STATE
                || a == ProjectEvent.ACTION_SET_TOOL || a == ProjectEvent.ACTION_COMPLETE
                || a == ProjectEvent.UNDO_COMPLETE || a == ProjectEvent.ACTION_SET_FILE) {
            SwingUtilities.invokeLater(this::refresh);
        }
    }

    void afterPinChange(Circuit sub, List<InstancePaths.PortUse> b) {
        LogisimFile file = proj.getLogisimFile();
        List<InstancePaths.Broken> broken = InstancePaths.broken(b, InstancePaths.snapshot(file, sub));
        if (broken.isEmpty()) {
            return;
        }
        int kept = reconnect(proj, broken);
        kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("instance.broken", sub.getName(),
                broken.size(), kept));
    }

    /**
     * 끊긴 곳 가운데 옛 선 끝이 옛 포트 자리에 그대로 있고 새 포트 자리가 있으면, 새 자리에서 옛 자리까지 선을 잇는다
     * (한 줄이면 곧게, 아니면 가로 먼저 ㄱ자). 부모 회로마다 한 번의 변경이고 {@link kr.ac.hallym.hcs.app.wiring.WireGuard}
     * 검사를 통과할 때만 남긴다. 되살린 수.
     */
    public static int reconnect(Project proj, List<InstancePaths.Broken> broken) {
        java.util.Map<Circuit, List<InstancePaths.Broken>> byParent = new java.util.LinkedHashMap<>();
        for (InstancePaths.Broken x : broken) {
            // 새 자리가 비어 있고(다른 것에 닿지 않음) 옛 자리에 선 끝이 남은 경우만 잇는다
            if (x.now != null && InstancePaths.wireEndsAt(x.before.parent, x.before.at)
                    && !InstancePaths.touchesAnything(x.before.parent, x.before.instance, x.now)) {
                byParent.computeIfAbsent(x.before.parent, k -> new ArrayList<>()).add(x);
            }
        }
        int kept = 0;
        for (java.util.Map.Entry<Circuit, List<InstancePaths.Broken>> e : byParent.entrySet()) {
            Circuit parent = e.getKey();
            CircuitMutation m = new CircuitMutation(parent);
            List<Location> allowed = new ArrayList<>();
            for (InstancePaths.Broken x : e.getValue()) {
                Location from = x.now;
                Location to = x.before.at;
                if (from.getX() == to.getX() || from.getY() == to.getY()) {
                    m.add(Wire.create(from, to));
                } else {
                    Location bend = Location.create(to.getX(), from.getY());
                    m.add(Wire.create(from, bend));
                    m.add(Wire.create(bend, to));
                }
                allowed.add(from);
                allowed.add(to);
            }
            if (kr.ac.hallym.hcs.app.wiring.WireGuard.run(proj, parent, m, allowed,
                    () -> Messages.get("instance.reconnectAction"))) {
                kept += e.getValue().size();
            }
        }
        return kept;
    }

    /** 테스트: 띠가 보이는가, 무슨 글자인가. */
    public static InstanceBanner of(JPanel wrap) {
        return (InstanceBanner) wrap.getClientProperty(InstanceBanner.class);
    }

    public boolean visible() {
        return panel.isVisible();
    }

    public boolean offersGoTo() {
        return go.isVisible();
    }

    public String previewShown() {
        return preview.isVisible() ? preview.getText() : null;
    }

    public List<List<Component>> paths() {
        return paths;
    }
}
