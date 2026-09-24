/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;

/**
 * 직접 지정한 터널 색(PLAN.md 11.12, D-042). 파일과 함께 가야 하는 정보라 .circ의 확장 정보(PLAN.md 7.0,
 * {@code <hcs:ext>})에 회로마다 {@code tunnel label=… color=#RRGGBB}로 둔다. 원조 2.7.1은 이 요소를 건너뛴다.
 * 지정하지 않은 터널은 이름 해시 색({@link TunnelColors})이고 저장하지 않는다. 같은 회로의 같은 이름 터널은 같은 색이다.
 */
public final class TunnelColorStore {
    static final String KIND = "tunnel";

    /** 저장 전: 그 회로에 그 이름의 터널이 더 없으면 항목을 지운다. */
    public static final CircExtensions.Pruner PRUNER = (file, ext) -> {
        for (Circuit c : file.getCircuits()) {
            for (CircExtension.Item item : ext.items(c.getName())) {
                if (item.kind().equals(KIND) && !hasTunnel(c, item.get("label"))) {
                    ext.remove(c.getName(), item);
                }
            }
        }
    };

    private TunnelColorStore() {
    }

    /** 터널 부품의 이름(원조가 연결에 쓰는 라벨 그대로). 없으면 null. */
    public static String name(Component c) {
        if (!c.getFactory().getName().equals("Tunnel")) {
            return null;
        }
        String s = c.getAttributeSet().getValue(StdAttr.LABEL);
        return s == null || s.isEmpty() ? null : s;
    }

    static boolean hasTunnel(Circuit c, String label) {
        for (Component comp : c.getNonWires()) {
            if (label != null && label.equals(name(comp))) {
                return true;
            }
        }
        return false;
    }

    /** 직접 지정한 색. 없으면 null. */
    public static Color get(LogisimFile file, Circuit circuit, String label) {
        for (CircExtension.Item item : CircExtensions.of(file).items(circuit.getName())) {
            if (item.kind().equals(KIND) && label.equals(item.get("label"))) {
                try {
                    return Color.decode(item.get("color"));
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 그릴 색: 직접 지정한 색, 없으면 이름 해시 색. */
    public static Color display(LogisimFile file, Circuit circuit, String label) {
        Color c = file == null ? null : get(file, circuit, label);
        return c != null ? c : TunnelColors.of(label);
    }

    /** 색을 둔다(null이면 지정 해제 = 이름 해시 색). */
    static void set(LogisimFile file, Circuit circuit, String label, Color color) {
        CircExtension ext = CircExtensions.of(file);
        for (CircExtension.Item item : ext.items(circuit.getName())) {
            if (item.kind().equals(KIND) && label.equals(item.get("label"))) {
                ext.remove(circuit.getName(), item);
            }
        }
        if (color != null) {
            Map<String, String> a = new LinkedHashMap<>();
            a.put("label", label);
            a.put("color", String.format("#%06X", color.getRGB() & 0xFFFFFF));
            ext.add(circuit.getName(), new CircExtension.Item(KIND, a));
        }
    }

    /** 색 지정 동작(되돌리기 한 번). 파일을 수정한 것으로 친다. */
    public static Action action(LogisimFile file, Circuit circuit, String label, Color color) {
        return new Action() {
            private Color before;

            @Override
            public String getName() {
                return Messages.get("tunnel.colorAction", label);
            }

            @Override
            public void doIt(Project proj) {
                before = get(file, circuit, label);
                set(file, circuit, label, color);
                proj.repaintCanvas();
            }

            @Override
            public void undo(Project proj) {
                set(file, circuit, label, before);
                proj.repaintCanvas();
            }
        };
    }
}
