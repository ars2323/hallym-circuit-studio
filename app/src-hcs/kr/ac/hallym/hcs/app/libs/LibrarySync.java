/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 저장 반영(P-03, PLAN.md 11.1). 파일을 저장하면 그 파일을 라이브러리로 쓰는 열린 프로젝트의 라이브러리를 새 버전으로
 * 바꾼다. 원조 {@code LibraryManager.fileSaved}가 그 일을 하려 하지만 찾지 못해 아무것도 하지 않으므로, 원조 공개
 * {@code Loader.reload}로 다시 불러온다(D-065).
 * <ul>
 * <li>저장 전: 저장할 파일의 회로 포트가 바뀌어 다른 열린 파일의 인스턴스 연결이 끊기면 "ripple_carry의 fa0, fa1
 * 연결 4곳이 끊깁니다"처럼 알리고 저장할지 묻는다(학생이 고른다). 끊긴 곳은 학생이 그 파일에서 다시 잇는다.</li>
 * <li>저장 뒤: 그 파일을 쓰는 열린 탭의 시뮬레이션을 리셋하고 탭에 "Updated"를 단다.</li>
 * <li>열 때: 이 파일이 쓰는 라이브러리 파일이 이 파일보다 나중에 바뀌었으면 알린다. 라이브러리 경로를 찾기 창으로 바꿨으면
 * 저장해서 상대 경로로 남기라고 알리고 저장할 것으로 표시한다.</li>
 * </ul>
 */
public final class LibrarySync {
    private LibrarySync() {
    }

    /** 이 파일을 라이브러리로 쓰는 다른 열린 프로젝트와 그 라이브러리. */
    public static final class Use {
        public final Project project;
        public final LoadedLibrary library;

        Use(Project project, LoadedLibrary library) {
            this.project = project;
            this.library = library;
        }
    }

    public static List<Use> users(Project saving, File file) {
        List<Use> out = new ArrayList<>();
        for (Project p : OpenFileLibraries.openProjects.get()) {
            if (p == saving) {
                continue;
            }
            LoadedLibrary lib = OpenFileLibraries.loaded(p, file);
            if (lib != null) {
                out.add(new Use(p, lib));
            }
        }
        return out;
    }

    // ---- 포트 변경 영향 ----

    /** 한 파일의 끊길 연결: 그 파일 이름, 인스턴스 이름들, 연결 수. */
    public static final class Cut {
        public final String file;
        public final List<String> instances;
        public final int connections;

        Cut(String file, List<String> instances, int connections) {
            this.file = file;
            this.instances = instances;
            this.connections = connections;
        }

        /** 알림 한 줄. */
        public String message() {
            List<String> names = instances.size() > 4 ? instances.subList(0, 4) : instances;
            String who = String.join(", ", names) + (instances.size() > 4 ? " …" : "");
            return Messages.get("libs.cut", file, who, connections);
        }
    }

    /**
     * saving의 지금 회로(저장할 내용)로 바꾸면 다른 열린 파일의 인스턴스에서 끊길 연결. 이어진 포트마다, 같은 이름의
     * 핀이 없어지거나 포트 자리(모양 기준 오프셋)가 달라지면 끊긴다.
     */
    public static List<Cut> impact(Project saving, File file) {
        List<Cut> out = new ArrayList<>();
        LogisimFile now = saving.getLogisimFile();
        for (Use u : users(saving, file)) {
            // 이 라이브러리가 지금 주는 회로들(원조 공개 API: 도구의 서브회로 부품)
            java.util.Set<Circuit> libCircuits = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (com.cburch.logisim.tools.Tool t : u.library.getTools()) {
                if (t instanceof com.cburch.logisim.tools.AddTool && ((com.cburch.logisim.tools.AddTool) t)
                        .getFactory() instanceof SubcircuitFactory) {
                    libCircuits.add(((SubcircuitFactory) ((com.cburch.logisim.tools.AddTool) t).getFactory())
                            .getSubcircuit());
                }
            }
            Set<String> names = new LinkedHashSet<>();
            int count = 0;
            for (Circuit parent : u.project.getLogisimFile().getCircuits()) {
                Netlist nl = null;
                for (Component inst : sorted(parent.getNonWires())) {
                    if (!(inst.getFactory() instanceof SubcircuitFactory)) {
                        continue;
                    }
                    Circuit oldC = ((SubcircuitFactory) inst.getFactory()).getSubcircuit();
                    if (!libCircuits.contains(oldC)) {
                        continue; // 이 라이브러리의 회로가 아니다
                    }
                    Circuit newC = now.getCircuit(oldC.getName());
                    if (nl == null) {
                        nl = Netlist.of(parent);
                    }
                    Direction facing = inst.getAttributeSet().getValue(StdAttr.FACING);
                    Map<String, Location> before = ports(oldC, facing);
                    Map<String, Location> after = newC == null ? new LinkedHashMap<>() : ports(newC, facing);
                    int i = 0;
                    for (Map.Entry<String, Location> e : before.entrySet()) {
                        int end = i++;
                        if (!connected(nl, inst, end)) {
                            continue;
                        }
                        Location a = after.get(e.getKey());
                        if (a == null || !a.equals(e.getValue())) {
                            count++;
                            String l = Names.label(inst);
                            names.add(l != null ? l : oldC.getName());
                        }
                    }
                }
            }
            if (count > 0) {
                String fn = OpenFileLibraries.fileOf(u.project) != null ? OpenFileLibraries.fileOf(u.project).getName()
                        : u.project.getLogisimFile().getName();
                out.add(new Cut(fn, new ArrayList<>(names), count));
            }
        }
        return out;
    }

    /** 회로 모양의 포트: 핀 이름(없으면 순번) → 오프셋. 원조 SubcircuitFactory와 같은 순서. */
    static Map<String, Location> ports(Circuit c, Direction facing) {
        SortedMap<Location, Instance> offs = c.getAppearance().getPortOffsets(facing == null ? Direction.EAST : facing);
        Map<String, Location> out = new LinkedHashMap<>();
        int i = 0;
        for (Map.Entry<Location, Instance> e : offs.entrySet()) {
            String l = Names.label(Instance.getComponentFor(e.getValue()));
            out.put(l != null ? l : "#" + i, e.getKey());
            i++;
        }
        return out;
    }

    static boolean connected(Netlist nl, Component inst, int end) {
        if (end >= inst.getEnds().size()) {
            return false;
        }
        Netlist.Net n = nl.netOf(inst, end);
        if (n == null) {
            return false;
        }
        if (!n.wires().isEmpty()) {
            return true;
        }
        for (Netlist.PortRef p : n.ports()) {
            if (p.component != inst) {
                return true;
            }
        }
        return false;
    }

    static List<Component> sorted(java.util.Collection<Component> cs) {
        List<Component> ret = new ArrayList<>(cs);
        ret.sort(java.util.Comparator.<Component>comparingInt(c -> c.getLocation().getY())
                .thenComparingInt(c -> c.getLocation().getX()));
        return ret;
    }

    // ---- 저장 앞뒤(ProjectActions의 HCS 한 줄씩) ----

    /** 저장 전. 끊길 연결이 있으면 알리고 학생이 고른다. 창이 없으면(테스트) 그냥 저장한다. false면 저장하지 않는다. */
    public static boolean beforeSave(Project proj, File f) {
        List<Cut> cuts = impact(proj, f);
        if (cuts.isEmpty() || proj.getFrame() == null) {
            return true;
        }
        StringBuilder msg = new StringBuilder();
        for (Cut c : cuts) {
            msg.append(c.message()).append('\n');
        }
        msg.append('\n').append(Messages.get("libs.cutQuestion"));
        Object[] options = {Messages.get("libs.saveAnyway"), Messages.get("libs.cancel")};
        int r = javax.swing.JOptionPane.showOptionDialog(proj.getFrame(), msg.toString(),
                Messages.get("libs.cutTitle"), javax.swing.JOptionPane.DEFAULT_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return r == 0;
    }

    /**
     * 저장 뒤: 이 파일을 쓰는 열린 탭의 라이브러리를 새 버전으로 다시 불러오고(원조 공개 {@code Loader.reload}, 인스턴스는
     * 원조가 새 회로로 바꾼다), 시뮬레이션을 리셋하고 "Updated"를 단다. 원조 {@code LibraryManager.fileSaved}는 파일로
     * 라이브러리를 찾지만 등록표 열쇠가 설명자라 찾지 못해 아무것도 바꾸지 않는다(D-065).
     */
    public static List<Project> afterSave(Project proj, File f) {
        List<Project> touched = new ArrayList<>();
        java.util.Set<LoadedLibrary> reloaded = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Use u : users(proj, f)) {
            if (reloaded.add(u.library)) {
                u.project.getLogisimFile().getLoader().reload(u.library); // 여러 파일이 같은 라이브러리를 나눠 쓴다
            }
            u.project.getSimulator().requestReset();
            kr.ac.hallym.hcs.app.tabs.FileTabs.get().model().markUpdated(u.project);
            kr.ac.hallym.hcs.app.sim.SimControls.notice(u.project, Messages.get("libs.updated", f.getName()));
            touched.add(u.project);
        }
        return touched;
    }

    // ---- 원본 파일에서 편집 ----

    /** 이 프로젝트가 라이브러리로 쓰는 파일 가운데 circuit을 주는 것. 이 파일의 회로면 null. */
    public static File originFile(Project p, Circuit circuit) {
        if (p.getLogisimFile().getCircuits().contains(circuit)) {
            return null;
        }
        for (Library lib : p.getLogisimFile().getLibraries()) {
            if (!(lib instanceof LoadedLibrary)) {
                continue;
            }
            for (com.cburch.logisim.tools.Tool t : lib.getTools()) {
                if (t instanceof com.cburch.logisim.tools.AddTool && ((com.cburch.logisim.tools.AddTool) t)
                        .getFactory() instanceof SubcircuitFactory && ((SubcircuitFactory) ((com.cburch.logisim.tools
                                .AddTool) t).getFactory()).getSubcircuit() == circuit) {
                    return OpenFileLibraries.libraryFile(p, (LoadedLibrary) lib);
                }
            }
        }
        return null;
    }

    /** "Edit Original File": 원본 파일이 열려 있으면 그 탭으로, 아니면 연다. 그리고 그 회로를 보인다. */
    public static Project editOriginal(Project from, File file, String circuit) {
        Project target = null;
        for (Project p : OpenFileLibraries.openProjects.get()) {
            if (OpenFileLibraries.same(OpenFileLibraries.fileOf(p), file)) {
                target = p;
            }
        }
        if (target == null) {
            target = com.cburch.logisim.proj.ProjectActions.doOpen(from.getFrame(), from, file);
        } else {
            kr.ac.hallym.hcs.app.tabs.FileTabs.get().model().activate(target);
            if (target.getFrame() != null) {
                target.getFrame().toFront();
            }
        }
        if (target != null) {
            Circuit c = target.getLogisimFile().getCircuit(circuit);
            if (c != null) {
                target.setCurrentCircuit(c);
            }
        }
        return target;
    }

    // ---- 열 때 ----

    /** 창을 만들 때: 열린 뒤 한 번 확인한다. */
    public static void install(Frame frame) {
        javax.swing.SwingUtilities.invokeLater(() -> opened(frame.getProject()));
    }

    /** 열 때 알릴 것(테스트가 부른다): 나중에 바뀐 라이브러리 파일 이름들, 경로가 바뀐 라이브러리 이름들. */
    public static final class OnOpen {
        public final List<String> newer = new ArrayList<>();
        public final List<String> moved = new ArrayList<>();
    }

    public static OnOpen check(Project p) {
        OnOpen o = new OnOpen();
        File mine = OpenFileLibraries.fileOf(p);
        if (mine == null || !mine.exists() || p.getLogisimFile() == null) {
            return o;
        }
        Set<String> saved = savedLibraryDescriptors(mine);
        for (Library lib : p.getLogisimFile().getLibraries()) {
            if (!(lib instanceof LoadedLibrary)) {
                continue;
            }
            File f = OpenFileLibraries.libraryFile(p, (LoadedLibrary) lib);
            if (f == null) {
                continue;
            }
            if (f.exists() && f.lastModified() > mine.lastModified()) {
                o.newer.add(f.getName());
            }
            String desc;
            try {
                desc = p.getLogisimFile().getLoader().getDescriptor(lib);
            } catch (RuntimeException e) {
                continue;
            }
            if (!saved.isEmpty() && !saved.contains(desc)) {
                o.moved.add(f.getName());
            }
        }
        return o;
    }

    static void opened(Project p) {
        OnOpen o = check(p);
        if (!o.moved.isEmpty()) {
            // 찾기 창으로 바꾼 경로: 저장하면 원조 방식대로 상대 경로로 남는다
            p.getLogisimFile().setDirty(true);
            kr.ac.hallym.hcs.app.sim.SimControls.notice(p, Messages.get("libs.moved", String.join(", ", o.moved)));
        } else if (!o.newer.isEmpty()) {
            kr.ac.hallym.hcs.app.sim.SimControls.notice(p, Messages.get("libs.newer", String.join(", ", o.newer)));
        }
    }

    private static final Pattern LIB = Pattern.compile("<lib\\s+desc=\"([^\"]*)\"");

    /** 저장된 파일에 적힌 라이브러리 설명자들("file#…"만). */
    static Set<String> savedLibraryDescriptors(File circ) {
        Set<String> out = new LinkedHashSet<>();
        try {
            String s = new String(Files.readAllBytes(circ.toPath()), StandardCharsets.UTF_8);
            Matcher m = LIB.matcher(s);
            while (m.find()) {
                String d = m.group(1).replace("&amp;", "&");
                if (d.startsWith("file#")) {
                    out.add(d);
                }
            }
        } catch (java.io.IOException e) {
            // 읽을 수 없으면 비교하지 않는다
        }
        return out;
    }
}
