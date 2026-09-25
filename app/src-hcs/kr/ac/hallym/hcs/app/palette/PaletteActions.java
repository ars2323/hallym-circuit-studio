/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.palette;

import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.MenuExtender;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.edit.CircuitEdits;

/** 팔레트에서 고른 것 실행(#76): 부품은 커서 자리에 놓고(되돌리기 한 번), 명령은 원조 동작을 부른다. */
public final class PaletteActions {
    static final String RECENT = "palette.recent";
    static final String FAVORITES = "palette.favorites";

    private PaletteActions() {
    }

    /** 부품(또는 서브회로)을 at에 놓는 변경. 속성은 질의에서 읽은 값. */
    public static CircuitMutation place(Circuit circuit, Palette.Item item, Location at) {
        ComponentFactory f = factory(item);
        AttributeSet as = f.createAttributeSet();
        for (Map.Entry<String, String> e : item.attrs.entrySet()) {
            CircuitEdits.set(as, e.getKey(), e.getValue());
        }
        Location snapped = Location.create(Math.round(at.getX() / 10f) * 10, Math.round(at.getY() / 10f) * 10);
        CircuitMutation m = new CircuitMutation(circuit);
        m.add(f.createComponent(snapped, as));
        return m;
    }

    /**
     * "Open Files" 항목: 그 파일을 라이브러리로 불러오고(이미 있으면 그대로, 순환이면 막고 알림) 그 회로의 도구.
     * 불러오기는 되돌리기 한 번(원조 Load Library 동작)이다.
     */
    public static AddTool openFileTool(Project proj, Palette.Item item) {
        java.io.File file = new java.io.File(item.command);
        kr.ac.hallym.hcs.app.libs.OpenFileLibraries.Result r =
                kr.ac.hallym.hcs.app.libs.OpenFileLibraries.ensureLoaded(proj, file);
        if (r.library == null) {
            if ("circular".equals(r.refused)) {
                kr.ac.hallym.hcs.app.sim.SimControls.notice(proj, Messages.get("libs.circular"));
            }
            return null;
        }
        return kr.ac.hallym.hcs.app.libs.OpenFileLibraries.toolFor(r.library, item.name);
    }

    /**
     * 다른 탭의 파일을 캔버스에 떨어뜨렸다: 그 파일을 라이브러리로 불러오고 회로 circuit을 at에 놓는다(P-03).
     * 막히면(자기 자신, 순환, 저장 안 한 파일) 알림만 하고 false.
     */
    public static boolean dropFile(Project proj, java.io.File file, String circuit, Location at) {
        Palette.Item it = new Palette.Item(Palette.Kind.OPEN_FILE, circuit, null, null, file.getPath(),
                java.util.Collections.<String, String>emptyMap(), 0);
        AddTool t = openFileTool(proj, it);
        if (t == null) {
            return false;
        }
        placeFactory(proj, t.getFactory(), at, circuit);
        return true;
    }

    /** 부품 하나를 at(격자에 맞춤)에 놓는다. */
    public static void placeFactory(Project proj, ComponentFactory f, Location at, String name) {
        Location snapped = Location.create(Math.round(at.getX() / 10f) * 10, Math.round(at.getY() / 10f) * 10);
        CircuitMutation m = new CircuitMutation(proj.getCurrentCircuit());
        m.add(f.createComponent(snapped, f.createAttributeSet()));
        proj.doAction(m.toAction(() -> Messages.get("palette.placeAction", name)));
    }

    static ComponentFactory factory(Palette.Item item) {
        if (item.kind == Palette.Kind.SUBCIRCUIT) {
            return item.circuit.getSubcircuitFactory();
        }
        return ((AddTool) item.library.getTool(item.name)).getFactory();
    }

    /** 고른 것 실행. at은 캔버스의 커서 자리(논리 좌표). */
    public static void run(Project proj, Palette.Item item, Location at) {
        if (item.kind == Palette.Kind.COMMAND) {
            command(proj, item.command);
            return;
        }
        if (item.kind == Palette.Kind.OPEN_FILE) {
            AddTool t = openFileTool(proj, item);
            if (t != null) {
                placeFactory(proj, t.getFactory(), at, Palette.displayName(item));
            }
            return;
        }
        Circuit c = proj.getCurrentCircuit();
        if (item.kind == Palette.Kind.SUBCIRCUIT && item.circuit == c) {
            return; // 자기 자신은 넣을 수 없다(원조도 막는다)
        }
        proj.doAction(place(c, item, at).toAction(() -> Messages.get("palette.placeAction",
                Palette.displayName(item))));
        if (item.kind == Palette.Kind.COMPONENT) {
            remember(item.name);
        }
    }

    /** 최근 목록 맨 앞에(부품 저장 이름). */
    public static void remember(String factory) {
        Settings s = Settings.get();
        s.setList(RECENT, Palette.touch(s.getList(RECENT), factory));
        try {
            s.save();
        } catch (java.io.IOException e) {
            // 최근 목록만 잃는다
        }
    }

    static void command(Project proj, String id) {
        com.cburch.logisim.circuit.Simulator sim = proj.getSimulator();
        switch (id) {
        case "reset":
            kr.ac.hallym.hcs.app.record.Recorder.requestReset(proj);
            break;
        case "tick":
            sim.tick();
            break;
        case "step":
            sim.step();
            break;
        case "run":
            sim.setIsRunning(!sim.isRunning());
            break;
        case "fit":
            if (proj.getFrame().getCanvas().getHcsZoom() != null) {
                proj.getFrame().getCanvas().getHcsZoom().fitCircuit();
            }
            break;
        case "find":
            kr.ac.hallym.hcs.app.find.FindDialog.open(proj.getFrame());
            break;
        case "keys":
            kr.ac.hallym.hcs.app.keys.Shortcuts.showTable(proj.getFrame());
            break;
        case "loadS":
            loadProgram(proj);
            break;
        default:
            break;
        }
    }

    /** .s 불러오기: 이 회로의 Instruction Memory 메뉴 항목(Hallym MIPS 라이브러리)을 누른다. */
    public static void loadProgram(Project proj) {
        for (Component c : proj.getCurrentCircuit().getNonWires()) {
            if (c.getFactory().getName().equals("Instruction Memory")) {
                Object ext = c.getFeature(MenuExtender.class);
                if (ext instanceof MenuExtender) {
                    JPopupMenu menu = new JPopupMenu();
                    ((MenuExtender) ext).configureMenu(menu, proj);
                    for (java.awt.Component mc : menu.getComponents()) {
                        if (mc instanceof JMenuItem && ((JMenuItem) mc).getText().contains(".s")) {
                            ((JMenuItem) mc).doClick();
                            return;
                        }
                    }
                }
            }
        }
        javax.swing.JOptionPane.showMessageDialog(proj.getFrame(), Messages.get("palette.noImem"));
    }

    public static List<String> recent() {
        return Settings.get().getList(RECENT);
    }

    public static List<String> favorites() {
        return Settings.get().getList(FAVORITES);
    }

    /** 즐겨찾기 넣기·빼기. */
    public static void toggleFavorite(String factory) {
        Settings s = Settings.get();
        List<String> fav = new java.util.ArrayList<>(s.getList(FAVORITES));
        if (!fav.remove(factory)) {
            fav.add(factory);
        }
        s.setList(FAVORITES, fav);
        try {
            s.save();
        } catch (java.io.IOException e) {
            // 무시
        }
    }
}
