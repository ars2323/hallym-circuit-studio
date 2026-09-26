/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.splitter.SplitterMenu;

/**
 * 대상별 우클릭 메뉴(#72, #105). 원조 Menu Tool이 만든 메뉴(삭제, 속성 보기, 부품 자체 항목) 뒤에 우리 항목을
 * 덧붙인다. 우클릭이 Menu Tool로 가는 것은 원조 .circ의 마우스 매핑 그대로이고 그 값은 바꾸지 않는다.
 */
public final class ContextMenus {
    /** 우클릭한 곳의 대상. */
    public static final class Target {
        public final Canvas canvas;
        public final Project project;
        public final Circuit circuit;
        public final Location point;
        /** 우클릭한 점의 부품(선 포함). 없으면 null. */
        public final Component component;
        /** 여러 개를 골랐을 때 고른 순서. */
        public final List<Component> selection;
        /** 고른 순서를 아는가(사각형으로 한꺼번에 골랐으면 모른다). */
        public final boolean selectionOrderKnown;

        Target(Canvas canvas, Location point, Component component, List<Component> selection, boolean known) {
            this.canvas = canvas;
            this.project = canvas.getProject();
            this.circuit = canvas.getCircuit();
            this.point = point;
            this.component = component;
            this.selection = selection;
            this.selectionOrderKnown = known;
        }

        public boolean isWire() {
            return component instanceof Wire;
        }
    }

    /** 메뉴에 항목을 더하는 쪽. */
    public interface Provider {
        void contribute(Target target, JPopupMenu menu);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();
    private static final Map<Project, SelectionOrder<Component>> ORDERS = new WeakHashMap<>();
    /** 선택 리스너를 프로젝트 동안 붙잡아 둔다. */
    private static final Map<Project, Selection.Listener> LISTENERS = new WeakHashMap<>();

    static {
        PROVIDERS.add(new EditMenus());
        PROVIDERS.add(new SplitterMenu());
        PROVIDERS.add(new kr.ac.hallym.hcs.app.probe.ProbeMenu());
        PROVIDERS.add(new kr.ac.hallym.hcs.app.influence.InfluenceMenu());
        PROVIDERS.add(new kr.ac.hallym.hcs.app.flow.FlowMenu());
        PROVIDERS.add(new kr.ac.hallym.hcs.app.memo.MemoMenu());
        kr.ac.hallym.hcs.app.ext.CircExtensions.addPruner(kr.ac.hallym.hcs.app.splitter.SplitterEdits.PRUNER);
        kr.ac.hallym.hcs.app.ext.CircExtensions.addPruner(kr.ac.hallym.hcs.app.labels.TunnelColorStore.PRUNER);
        kr.ac.hallym.hcs.app.ext.CircExtensions.addPruner(kr.ac.hallym.hcs.app.groups.SignalGroups.PRUNER);
    }

    private ContextMenus() {
    }

    /** 창을 만들 때: 그 프로젝트의 선택 순서를 따라간다. 선택은 창이 다 만들어진 뒤에 생기므로 뒤로 미룬다. */
    public static void install(Frame frame) {
        javax.swing.SwingUtilities.invokeLater(() -> watch(frame.getProject()));
    }

    private static synchronized void watch(Project proj) {
        if (LISTENERS.containsKey(proj) || proj.getSelection() == null) {
            return;
        }
        SelectionOrder<Component> order = new SelectionOrder<>();
        // 같은 AWT 이벤트(마우스 한 번) 안의 선택 변경은 한 묶음: 사각형 선택은 순서가 없다
        Selection.Listener l = e -> order.update(proj.getSelection().getComponents(),
                java.awt.EventQueue.getCurrentEvent());
        proj.getSelection().addListener(l);
        ORDERS.put(proj, order);
        LISTENERS.put(proj, l);
    }

    static synchronized SelectionOrder<Component> selectionOrder(Project proj) {
        SelectionOrder<Component> o = ORDERS.get(proj);
        if (o == null) {
            o = new SelectionOrder<>();
        }
        o.update(proj.getSelection().getComponents(), java.awt.EventQueue.getCurrentEvent());
        return o;
    }

    /**
     * Menu Tool이 만든 menu(없으면 null)에 대상별 항목을 더하고 다시 짠다(검토 반영 1): 맨 위 대상 요약, 대상별
     * 동작(부품 자체 항목·우리 항목), 공통(복제·속성 패널·잘라내기·복사), 맨 아래 삭제. 원조 "속성 보기"는 "속성
     * 패널에서 보기"(그 부품을 고르고 오른쪽 속성 패널을 편다)로 바꾼다. 더할 것도 없으면 null.
     */
    public static JPopupMenu extend(Canvas canvas, JPopupMenu menu, Location pt, Graphics g) {
        Project proj = canvas.getProject();
        Component comp = null;
        Collection<Component> here = canvas.getCircuit().getAllContaining(pt, g);
        if (!here.isEmpty()) {
            comp = here.iterator().next();
        }
        SelectionOrder<Component> sel = selectionOrder(proj);
        Target t = new Target(canvas, pt, comp, sel.order(), sel.known());
        List<List<java.awt.Component>> parts = new ArrayList<>();
        if (menu != null) {
            parts.add(original(menu));
        }
        for (Provider p : PROVIDERS) {
            JPopupMenu part = new JPopupMenu();
            p.contribute(t, part);
            parts.add(new ArrayList<>(java.util.Arrays.asList(part.getComponents())));
        }
        boolean any = false;
        for (List<java.awt.Component> part : parts) {
            any |= !part.isEmpty();
        }
        if (!any) {
            return null;
        }
        int selected = EditMenus.nonWires(t.selection).size();
        boolean inSelection = comp == null || t.selection.contains(comp);
        return MenuLayout.arrange(MenuLayout.summary(t.circuit, comp, pt, inSelection ? selected : 1), parts);
    }

    /**
     * 원조 Menu Tool 메뉴의 항목에 묶음을 단다. 부품 메뉴는 [삭제, 속성 보기, 부품 자체 항목…], 여러 부품 메뉴는 [삭제,
     * 잘라내기, 복사] 순서로 만들어진다(원조 MenuTool). 원조 "속성 보기"는 우리 "속성 패널에서 보기"가 대신하므로 뺀다.
     */
    static List<java.awt.Component> original(JPopupMenu menu) {
        List<java.awt.Component> ret = new ArrayList<>();
        java.awt.Component[] items = menu.getComponents();
        String kind = menu.getClass().getSimpleName();
        for (int i = 0; i < items.length; i++) {
            java.awt.Component c = items[i];
            if (kind.equals("MenuComponent") && i == 0 || kind.equals("MenuSelection") && i == 0) {
                ret.add(MenuLayout.group((javax.swing.JComponent) c, MenuLayout.DELETE));
            } else if (kind.equals("MenuComponent") && i == 1) {
                continue; // 원조 "속성 보기"
            } else if (kind.equals("MenuSelection") && i <= 2) {
                ret.add(MenuLayout.group((javax.swing.JComponent) c, MenuLayout.COMMON));
            } else {
                ret.add(c);
            }
        }
        return ret;
    }
}
