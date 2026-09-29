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
 * <li>저장 전: 저장할 파일의 회로 포트가 바뀌어 다른 열린 파일의 인스턴스 연결이 끊기는 곳({@link #impact}).</li>
 * <li>원본 파일: 라이브러리 회로를 주는 파일({@link #originFile}).</li>
 * <li>열 때: 이 파일이 쓰는 라이브러리 파일이 이 파일보다 나중에 바뀌었는지, 경로가 바뀌었는지({@link #check}).</li>
 * </ul>
 * 엔진이 이 사실로 답하고 저장 뒤 다시 불러오기는 엔진이 한다({@code CircuitService}). v1 Swing판의 묻는 창·탭 표시·상태
 * 표시줄 알림은 화면 코드와 함께 지웠다(N-27, D-163, 옛 코드는 태그 {@code swing-final}).
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

    // ---- 열 때 ----

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
