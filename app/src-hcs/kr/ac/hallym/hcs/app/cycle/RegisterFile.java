/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.ext.CircExtension;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Names;

/**
 * 레지스터 파일(C-05, PLAN.md 5.3). 학생이 만든 서브회로라 자동으로 찾기 어려워, 서브회로를 오른쪽 클릭해 "Mark as
 * Register File"로 표시한다(조교가 과제 템플릿에 해 둘 수도 있다). 표시는 .circ 확장 정보(PLAN.md 7.0,
 * {@code <hcs:ext>})의 그 회로에 {@code regfile}로 둔다. 안의 레지스터 부품을 $0~$31에 대응시키는 순서:
 * 라벨 숫자($5, R5, r5, 5) → 라벨 이름($t0, t0, sp) → 위치(위에서 아래, 왼쪽에서 오른쪽으로 남은 번호). 사용자가
 * 대응 창에서 고친 것은 {@code regmap}(번호 → 부품 자리)으로 저장한다. 레지스터 값은 판단하지 않고 보이기만 한다.
 */
public final class RegisterFile {
    static final String KIND_MARK = "regfile";
    static final String KIND_MAP = "regmap";
    /** 레지스터로 보는 부품. */
    static final String REGISTER = "Register";

    private static final Pattern NUMBER = Pattern.compile("(?i)^(?:\\$|r|reg)?\\s*(\\d{1,2})$");

    private RegisterFile() {
    }

    /** 레지스터 파일로 표시한 회로. 없으면 null. */
    public static Circuit marked(LogisimFile file) {
        if (file == null) {
            return null;
        }
        CircExtension ext = CircExtensions.of(file);
        for (Circuit c : file.getCircuits()) {
            for (CircExtension.Item item : ext.items(c.getName())) {
                if (item.kind().equals(KIND_MARK)) {
                    return c;
                }
            }
        }
        return null;
    }

    /** 라벨이 가리키는 레지스터 번호: 숫자($5, R5, r5, 5) 또는 이름($t0, t0, sp). 아니면 -1. */
    public static int numberOf(String label) {
        if (label == null) {
            return -1;
        }
        String t = label.trim();
        Matcher m = NUMBER.matcher(t);
        if (m.matches()) {
            int n = Integer.parseInt(m.group(1));
            return n < 32 ? n : -1;
        }
        String name = t.startsWith("$") ? t : "$" + t;
        name = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < MipsText.REG.length; i++) {
            if (MipsText.REG[i].equals(name)) {
                return i;
            }
        }
        if (name.equals("$s8")) {
            return 30;
        }
        return -1;
    }

    static List<Component> registers(Circuit c) {
        List<Component> out = new ArrayList<>();
        for (Component x : c.getNonWires()) {
            if (x.getFactory().getName().equals(REGISTER)) {
                out.add(x);
            }
        }
        out.sort(Comparator.<Component>comparingInt(x -> x.getLocation().getY())
                .thenComparingInt(x -> x.getLocation().getX()));
        return out;
    }

    /** 자동 대응: 번호 → 부품. 레지스터가 32개보다 많으면 남는 것은 빼고, 모자라면 빈 번호가 남는다. */
    public static Map<Integer, Component> estimate(Circuit rf) {
        Map<Integer, Component> byNumber = new TreeMap<>();
        List<Component> rest = new ArrayList<>();
        // 1. 라벨 숫자, 2. 라벨 이름
        for (Component r : registers(rf)) {
            int n = numberOf(Names.label(r));
            if (n >= 0 && !byNumber.containsKey(n)) {
                byNumber.put(n, r);
            } else {
                rest.add(r);
            }
        }
        // 3. 위치: 남은 번호를 작은 것부터
        int next = 0;
        for (Component r : rest) {
            while (next < 32 && byNumber.containsKey(next)) {
                next++;
            }
            if (next >= 32) {
                break;
            }
            byNumber.put(next, r);
        }
        return byNumber;
    }

    /** 대응: 저장된 수동 대응(regmap)을 자동 대응 위에 얹는다. */
    public static Map<Integer, Component> mapping(LogisimFile file, Circuit rf) {
        Map<Integer, Component> map = estimate(rf);
        Map<Integer, Location> manual = manual(file, rf);
        if (manual.isEmpty()) {
            return map;
        }
        Map<Location, Component> at = new HashMap<>();
        for (Component r : registers(rf)) {
            at.put(r.getLocation(), r);
        }
        for (Map.Entry<Integer, Location> e : manual.entrySet()) {
            Component r = e.getValue() == null ? null : at.get(e.getValue());
            map.values().remove(r); // 한 부품은 한 번호에만
            if (r == null) {
                map.remove(e.getKey());
            } else {
                map.put(e.getKey(), r);
            }
        }
        return map;
    }

    /** 저장된 수동 대응(번호 → 자리, 자리가 null이면 "없음"). */
    static Map<Integer, Location> manual(LogisimFile file, Circuit rf) {
        Map<Integer, Location> out = new TreeMap<>();
        for (CircExtension.Item item : CircExtensions.of(file).items(rf.getName())) {
            if (!item.kind().equals(KIND_MAP)) {
                continue;
            }
            for (Map.Entry<String, String> e : item.attrs().entrySet()) {
                if (!e.getKey().startsWith("r")) {
                    continue;
                }
                try {
                    int n = Integer.parseInt(e.getKey().substring(1));
                    out.put(n, parseLocation(e.getValue()));
                } catch (NumberFormatException ex) {
                    // 읽을 수 없는 항목은 건너뛴다
                }
            }
        }
        return out;
    }

    static Location parseLocation(String s) {
        if (s == null || s.isEmpty() || s.equals("-")) {
            return null;
        }
        String[] p = s.split(",");
        return Location.create(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
    }

    /** 최상위 root에서 rf 인스턴스까지의 첫 경로. rf가 root 자신이면 빈 경로, 쓰이지 않으면 null. */
    public static List<Component> pathTo(Circuit root, Circuit rf) {
        if (root == rf) {
            return Collections.emptyList();
        }
        List<List<Component>> paths = InstancePaths.paths(root, rf);
        return paths.isEmpty() ? null : paths.get(0);
    }

    /** 표시하지 않았을 때: 최상위에서 모든 레지스터 부품(인스턴스 경로별, PLAN.md 5.3). */
    public static final class Found {
        public final List<Component> path;
        public final Component register;
        public final String name;

        Found(List<Component> path, Component register, String name) {
            this.path = path;
            this.register = register;
            this.name = name;
        }
    }

    public static List<Found> all(Circuit root) {
        List<Found> out = new ArrayList<>();
        collect(root, root, new ArrayList<Component>(), out, new java.util.HashSet<Circuit>());
        return out;
    }

    private static void collect(Circuit root, Circuit c, List<Component> path, List<Found> out,
            java.util.Set<Circuit> onPath) {
        if (!onPath.add(c)) {
            return;
        }
        for (Component r : registers(c)) {
            String label = Names.label(r);
            String base = label != null ? label : Names.title(c, r);
            String name = path.isEmpty() ? base
                    : InstancePaths.describe(root, path).substring(root.getName().length() + Names.SEP.length())
                            + Names.SEP + base;
            out.add(new Found(new ArrayList<>(path), r, name));
        }
        for (Component s : c.getNonWires()) {
            if (s.getFactory() instanceof SubcircuitFactory) {
                path.add(s);
                collect(root, ((SubcircuitFactory) s.getFactory()).getSubcircuit(), path, out, onPath);
                path.remove(path.size() - 1);
            }
        }
        onPath.remove(c);
    }

    // ---- 동작(되돌리기 한 번씩) ----

    /** rf를 레지스터 파일로 표시하거나(on) 표시를 푼다. 다른 회로의 표시는 옮긴다(하나만). */
    public static Action markAction(LogisimFile file, Circuit rf, boolean on) {
        return new Action() {
            private final Map<String, List<CircExtension.Item>> before = new LinkedHashMap<>();

            @Override
            public String getName() {
                return Messages.get(on ? "regfile.markAction" : "regfile.unmarkAction", rf.getName());
            }

            @Override
            public void doIt(Project proj) {
                CircExtension ext = CircExtensions.of(file);
                before.clear();
                for (Circuit c : file.getCircuits()) {
                    for (CircExtension.Item item : ext.items(c.getName())) {
                        if (item.kind().equals(KIND_MARK)) {
                            before.computeIfAbsent(c.getName(), k -> new ArrayList<>()).add(item);
                            ext.remove(c.getName(), item);
                        }
                    }
                }
                if (on) {
                    ext.add(rf.getName(), new CircExtension.Item(KIND_MARK, new LinkedHashMap<String, String>()));
                }
                file.setDirty(true);
            }

            @Override
            public void undo(Project proj) {
                CircExtension ext = CircExtensions.of(file);
                for (CircExtension.Item item : ext.items(rf.getName())) {
                    if (item.kind().equals(KIND_MARK)) {
                        ext.remove(rf.getName(), item);
                    }
                }
                for (Map.Entry<String, List<CircExtension.Item>> e : before.entrySet()) {
                    for (CircExtension.Item item : e.getValue()) {
                        ext.add(e.getKey(), item);
                    }
                }
                file.setDirty(true);
            }
        };
    }

    /** 수동 대응을 저장한다(번호 → 부품, 부품이 null이면 "없음"). 자동 대응과 같으면 저장하지 않는다. */
    public static Action mapAction(LogisimFile file, Circuit rf, Map<Integer, Component> chosen) {
        return new Action() {
            private List<CircExtension.Item> before = new ArrayList<>();

            @Override
            public String getName() {
                return Messages.get("regfile.mapAction", rf.getName());
            }

            @Override
            public void doIt(Project proj) {
                CircExtension ext = CircExtensions.of(file);
                before = new ArrayList<>();
                for (CircExtension.Item item : ext.items(rf.getName())) {
                    if (item.kind().equals(KIND_MAP)) {
                        before.add(item);
                        ext.remove(rf.getName(), item);
                    }
                }
                Map<Integer, Component> auto = estimate(rf);
                Map<String, String> a = new LinkedHashMap<>();
                for (int n = 0; n < 32; n++) {
                    Component want = chosen.get(n);
                    if (want != auto.get(n)) {
                        a.put("r" + n, want == null ? "-"
                                : want.getLocation().getX() + "," + want.getLocation().getY());
                    }
                }
                if (!a.isEmpty()) {
                    ext.add(rf.getName(), new CircExtension.Item(KIND_MAP, a));
                }
                file.setDirty(true);
            }

            @Override
            public void undo(Project proj) {
                CircExtension ext = CircExtensions.of(file);
                for (CircExtension.Item item : ext.items(rf.getName())) {
                    if (item.kind().equals(KIND_MAP)) {
                        ext.remove(rf.getName(), item);
                    }
                }
                for (CircExtension.Item item : before) {
                    ext.add(rf.getName(), item);
                }
                file.setDirty(true);
            }
        };
    }
}
