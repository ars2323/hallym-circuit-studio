/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * 탭 간 라이브러리(P-03, PLAN.md 11.1). 다른 탭에 열린 .circ의 회로를 이 파일에 넣으면 원조 Load Library(Logisim
 * 라이브러리)를 자동으로 하고, 원조 방식대로 상대 경로로 저장된다(원조 2.7.1에서도 열린다). 순환 참조(그 파일이 이미
 * 이 파일을 쓰는 경우)는 막는다. GUI 없이 쓸 수 있다.
 */
public final class OpenFileLibraries {
    /** 열린 프로젝트들(테스트가 바꿀 수 있다). */
    static Supplier<Collection<Project>> openProjects = Projects::getOpenProjects;

    private OpenFileLibraries() {
    }

    /** 다른 열린 파일의 회로 하나. */
    public static final class OpenCircuit {
        public final Project project;
        public final File file;
        public final Circuit circuit;

        OpenCircuit(Project project, File file, Circuit circuit) {
            this.project = project;
            this.file = file;
            this.circuit = circuit;
        }

        public String fileName() {
            return file.getName();
        }
    }

    public static File fileOf(Project p) {
        return p == null || p.getLogisimFile() == null ? null : p.getLogisimFile().getLoader().getMainFile();
    }

    static boolean same(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    /**
     * 이 프로젝트(cur)에 넣을 수 있는 다른 열린 파일의 회로들("Open Files" 묶음). 저장한 적 없는(또는 지워진) 파일, 같은 파일, 이미 라이브러리로 쓰는 파일,
     * 넣으면 순환이 되는 파일(그 파일이 이미 cur을 라이브러리로 쓰는 경우)은 뺀다.
     */
    public static List<OpenCircuit> candidates(Project cur) {
        List<OpenCircuit> out = new ArrayList<>();
        File mine = fileOf(cur);
        for (Project p : openProjects.get()) {
            File f = fileOf(p);
            // 이미 라이브러리로 쓰는 파일의 회로는 부품 목록에 이미 있다
            if (p == cur || f == null || !f.exists() || same(f, mine) || circular(mine, p.getLogisimFile()) || loaded(cur, f) != null) {
                continue;
            }
            for (Circuit c : p.getLogisimFile().getCircuits()) {
                out.add(new OpenCircuit(p, f, c));
            }
        }
        return out;
    }

    /**
     * other 파일(또는 그 라이브러리들)이 mine 파일을 쓰는가: 그러면 mine에 other를 넣을 때 순환이다. 원조 공개 API만
     * 쓴다(라이브러리 설명자로 파일을 알아내고 안쪽 라이브러리까지 따라간다).
     */
    public static boolean circular(File mine, LogisimFile other) {
        if (mine == null || other == null) {
            return false;
        }
        File base = other.getLoader().getMainFile();
        return uses(other.getLoader(), base == null ? null : base.getParentFile(), other.getLibraries(), mine, 0);
    }

    private static boolean uses(com.cburch.logisim.file.Loader loader, File dir, List<Library> libs, File target,
            int depth) {
        if (depth > 32) {
            return false;
        }
        for (Library lib : libs) {
            if (!(lib instanceof LoadedLibrary)) {
                continue;
            }
            File f = descriptorFile(loader, dir, lib);
            if (f != null && same(f, target)) {
                return true;
            }
            if (uses(loader, dir, lib.getLibraries(), target, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /** 원조 설명자("file#경로", 상대 경로는 dir 기준)의 파일. Logisim 라이브러리가 아니면 null. */
    static File descriptorFile(com.cburch.logisim.file.Loader loader, File dir, Library lib) {
        String desc;
        try {
            desc = loader.getDescriptor(lib);
        } catch (RuntimeException e) {
            return null;
        }
        if (desc == null || !desc.startsWith("file#")) {
            return null;
        }
        File f = new File(desc.substring("file#".length()));
        return f.isAbsolute() || dir == null ? f : new File(dir, f.getPath());
    }

    /** cur이 file을 이미 (직접) 라이브러리로 쓰고 있으면 그 라이브러리. */
    public static LoadedLibrary loaded(Project cur, File file) {
        for (Library lib : cur.getLogisimFile().getLibraries()) {
            if (lib instanceof LoadedLibrary && same(libraryFile(cur, (LoadedLibrary) lib), file)) {
                return (LoadedLibrary) lib;
            }
        }
        return null;
    }

    /** 라이브러리의 파일(원조 설명자 "file#경로"에서, 이 파일 자리를 기준으로). Logisim 라이브러리가 아니면 null. */
    public static File libraryFile(Project cur, LoadedLibrary lib) {
        File mine = fileOf(cur);
        return descriptorFile(cur.getLogisimFile().getLoader(), mine == null ? null : mine.getParentFile(), lib);
    }

    /** 결과: 불러온 라이브러리, 또는 막힌 까닭. */
    public static final class Result {
        public final LoadedLibrary library;
        public final String refused;

        Result(LoadedLibrary library, String refused) {
            this.library = library;
            this.refused = refused;
        }
    }

    /** cur에 file을 Logisim 라이브러리로 넣는다(이미 있으면 그것). 순환이면 넣지 않는다. 되돌리기 한 번. */
    public static Result ensureLoaded(Project cur, File file) {
        LoadedLibrary have = loaded(cur, file);
        if (have != null) {
            return new Result(have, null);
        }
        File mine = fileOf(cur);
        if (same(mine, file)) {
            return new Result(null, "self");
        }
        LogisimFile opened = null;
        for (Project p : openProjects.get()) {
            if (same(fileOf(p), file)) {
                opened = p.getLogisimFile();
            }
        }
        if (opened != null && circular(mine, opened)) {
            return new Result(null, "circular");
        }
        Library lib = cur.getLogisimFile().getLoader().loadLogisimLibrary(file);
        if (!(lib instanceof LoadedLibrary)) {
            return new Result(null, "load");
        }
        LoadedLibrary ll = (LoadedLibrary) lib;
        if (mine != null && uses(cur.getLogisimFile().getLoader(), mine.getParentFile(), ll.getLibraries(), mine, 0)) {
            return new Result(null, "circular");
        }
        cur.doAction(LogisimFileActions.loadLibrary(ll));
        return new Result(ll, null);
    }

    /** 라이브러리 안 회로의 부품 도구. */
    public static AddTool toolFor(Library lib, String circuit) {
        if (lib == null) {
            return null;
        }
        Tool t = lib.getTool(circuit);
        return t instanceof AddTool ? (AddTool) t : null;
    }
}
