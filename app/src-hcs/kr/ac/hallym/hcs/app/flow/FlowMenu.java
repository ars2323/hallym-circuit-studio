/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.flow;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.gui.main.Canvas;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.menu.ContextMenus;

/**
 * 우클릭 "Signal Flow" 묶음(P-07): Show Signal Flow, Show Signal Flow (Backward), Stop Signal Flow와 설정(Signal
 * Flow on Click, Flow Speed, Through Registers, Active Path Only, Reduce Motion, Smooth (60 fps)). 모든 도구에서
 * 우클릭으로 쓴다.
 */
public final class FlowMenu implements ContextMenus.Provider {
    @Override
    public void contribute(ContextMenus.Target t, JPopupMenu menu) {
        FlowController c = FlowController.of(t.canvas);
        JMenu m = new JMenu(Messages.get("flow.menu"));
        Component target = t.component;
        if (target != null) {
            JMenuItem fwd = new JMenuItem(Messages.get("flow.show"));
            fwd.addActionListener(e -> start(c, t, false));
            m.add(fwd);
            JMenuItem back = new JMenuItem(Messages.get("flow.showBackward"));
            back.addActionListener(e -> start(c, t, true));
            m.add(back);
        }
        if (c.running()) {
            JMenuItem stop = new JMenuItem(Messages.get("flow.stop"));
            stop.addActionListener(e -> c.stop());
            m.add(stop);
        }
        m.addSeparator();
        m.add(check("flow.onClick", FlowSettings.onClick(), FlowSettings::setOnClick));
        JMenu speed = new JMenu(Messages.get("flow.speed"));
        ButtonGroup group = new ButtonGroup();
        for (FlowSettings.Speed s : FlowSettings.Speed.values()) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(Messages.get("flow.speed." + s.name()),
                    FlowSettings.speed() == s);
            it.addActionListener(e -> FlowSettings.setSpeed(s));
            group.add(it);
            speed.add(it);
        }
        m.add(speed);
        m.add(check("flow.throughRegisters", FlowSettings.throughRegisters(), b -> {
            FlowSettings.setThroughRegisters(b);
            c.settingsChanged();
        }));
        m.add(check("flow.activePathOnly", FlowSettings.activePathOnly(), b -> {
            FlowSettings.setActivePathOnly(b);
            c.settingsChanged();
        }));
        m.add(check("flow.reduceMotion", FlowSettings.reduceMotion(), b -> {
            FlowSettings.setReduceMotion(b);
            c.settingsChanged();
        }));
        m.add(check("flow.smooth", FlowSettings.smooth(), b -> {
            FlowSettings.setSmooth(b);
            c.settingsChanged();
        }));
        menu.add(m);
    }

    private static JCheckBoxMenuItem check(String key, boolean on, java.util.function.Consumer<Boolean> set) {
        JCheckBoxMenuItem it = new JCheckBoxMenuItem(Messages.get(key), on);
        it.setToolTipText(Messages.get(key + ".tip"));
        it.addActionListener(e -> set.accept(it.isSelected()));
        return it;
    }

    private static void start(FlowController c, ContextMenus.Target t, boolean backward) {
        if (t.component instanceof Wire) {
            c.start((Wire) t.component, t.point, backward);
        } else {
            c.start(t.component, FlowController.outputNear(t.component, t.point), backward);
        }
    }

    /** 툴바 토글·단축키(Ctrl+Shift+F): 누르면 흐름 켜기를 켜고 끈다. 끄면 흐르던 것도 멈춘다. */
    public static void toggleOnClick(Canvas canvas) {
        boolean on = !FlowSettings.onClick();
        FlowSettings.setOnClick(on);
        if (!on) {
            FlowController.of(canvas).stop();
        }
        kr.ac.hallym.hcs.app.sim.SimControls.notice(canvas.getProject(),
                Messages.get(on ? "flow.onClickOn" : "flow.onClickOff"));
        ALL_TOGGLES.forEach(r -> r.run());
    }

    /** 툴바 토글 단추를 설정에 맞춘다. */
    public static final java.util.List<Runnable> ALL_TOGGLES = new java.util.concurrent.CopyOnWriteArrayList<>();
}
