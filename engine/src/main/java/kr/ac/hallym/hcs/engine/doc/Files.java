/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.doc;

import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.Library;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.engine.model.Ids;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 열린 파일들(file.*). 여는 길은 Swing 앱의 {@code ProjectActions.doOpen}·{@code createNewFile}·{@code doSave}와
 * 같다: 원조 {@code Loader}로 열고 저장하고(바이트 호환, D-006), 포크의 .circ 확장 정보(D-024)를 옆에서 읽고
 * 쓴다. 대화상자 대신 오류 응답을 낸다.
 */
public final class Files {
    static {
        // 저장 전에 가리키는 것이 없어진 확장 항목을 지운다(Swing 앱은 ContextMenus가 등록한다)
        CircExtensions.addPruner(kr.ac.hallym.hcs.app.splitter.SplitterEdits.PRUNER);
        CircExtensions.addPruner(kr.ac.hallym.hcs.app.labels.TunnelColorStore.PRUNER);
        CircExtensions.addPruner(kr.ac.hallym.hcs.app.groups.SignalGroups.PRUNER);
        CircExtensions.addPruner(kr.ac.hallym.hcs.app.sim.PcMark.PRUNER);
    }

    private final Map<String, Doc> docs = new LinkedHashMap<>();

    public Doc get(String fileId) throws RpcError {
        Doc d = fileId == null ? null : docs.get(fileId);
        if (d == null) {
            throw RpcError.notFound("file", fileId);
        }
        return d;
    }

    public Collection<Doc> all() {
        return docs.values();
    }

    /** 이미 열린 같은 파일(없으면 null). */
    public Doc findOpen(File f) {
        for (Doc d : docs.values()) {
            File main = d.loader().getMainFile();
            if (main != null && same(main, f)) {
                return d;
            }
        }
        return null;
    }

    /** File › New와 같다: 원조 기본 틀(default.templ)로 새 파일. */
    public Doc create() throws RpcError {
        EngineLoader loader = new EngineLoader();
        LogisimFile file;
        try (InputStream in = AppPreferences.getTemplate().createStream()) {
            file = loader.openLogisimFile(in);
        } catch (IOException | LoadFailedException | RuntimeException e) {
            throw RpcError.file(null, "templateFailed", "cannot create a new file: " + e);
        }
        if (file == null) {
            throw RpcError.file(null, "templateFailed", "cannot create a new file: " + loader.drainErrors());
        }
        return add(new Doc(Ids.nextFileId(), loader, file, false));
    }

    /** File › Open과 같다. 없는 라이브러리는 창으로 묻지 않고 오류로 알린다. */
    public Doc open(File f, boolean readOnly, List<String> messages) throws RpcError {
        String path = f.getPath();
        if (!f.isFile()) {
            throw RpcError.file(path, "notFound", "no such file: " + path);
        }
        if (!f.canRead()) {
            throw RpcError.file(path, "unreadable", "cannot read: " + path);
        }
        List<String> missing = LibraryCheck.missing(f);
        if (!missing.isEmpty()) {
            RpcError e = RpcError.file(path, "libraryMissing", "missing library: " + String.join(", ", missing));
            JsonArray m = new JsonArray();
            missing.forEach(m::add);
            ((JsonObject) e.data()).add("missing", m);
            throw e;
        }
        EngineLoader loader = new EngineLoader();
        LogisimFile file;
        try {
            file = loader.openLogisimFile(f);
        } catch (LoadFailedException e) {
            throw RpcError.file(path, "loadFailed", e.getMessage());
        } catch (HeadlessException e) {
            throw RpcError.file(path, "loadFailed", "the file needs a dialog to open (library or message): "
                    + loader.drainErrors());
        } catch (RuntimeException e) {
            List<String> errs = loader.drainErrors();
            throw RpcError.file(path, "loadFailed", errs.isEmpty() ? e.toString() : String.join("\n", errs));
        }
        if (file == null) {
            throw RpcError.file(path, "loadFailed", String.join("\n", loader.drainErrors()));
        }
        try {
            CircExtensions.afterOpen(file, f); // 확장 정보를 못 읽어도 회로는 연다(Swing과 같다)
        } catch (IOException e) {
            messages.add("extension info: " + e.getMessage());
        }
        messages.addAll(loader.drainErrors());
        return add(new Doc(Ids.nextFileId(), loader, file, readOnly));
    }

    /**
     * File › Save(As)와 같다: 원조 저장 코드, 확장 정보, 깨끗한 상태로. dest가 null이면 연 파일에 저장한다.
     */
    public File save(Doc d, File dest) throws RpcError {
        if (dest == null) {
            if (d.isReadOnly()) {
                throw RpcError.notEditable("readOnly", "read-only file: save it to a new path");
            }
            dest = d.loader().getMainFile();
            if (dest == null) {
                throw RpcError.params("path is required for a file that was never saved");
            }
        }
        String path = dest.getPath();
        d.loader().drainErrors(); // 앞에 남은 글은 이 저장과 무관하다
        boolean ok;
        try {
            ok = d.loader().save(d.file(), dest);
        } catch (HeadlessException | IllegalStateException e) {
            ok = false; // 원조가 오류 창을 열려던 자리
        }
        List<String> errs = d.loader().drainErrors();
        if (!ok) {
            throw RpcError.file(path, "writeFailed", errs.isEmpty() ? "cannot write " + path : String.join("\n", errs));
        }
        try {
            CircExtensions.afterSave(d.file(), dest);
        } catch (IOException e) {
            throw RpcError.file(path, "writeFailed", "extension info: " + e.getMessage());
        }
        d.project().setFileAsClean();
        d.setReadOnly(false);
        return dest;
    }

    public void close(Doc d) {
        docs.remove(d.id());
        d.close();
    }

    public void closeAll() {
        for (Doc d : docs.values()) {
            d.close();
        }
        docs.clear();
    }

    /** 파일이 쓰는 라이브러리 [{lib, display, kind, path?}]. */
    public static JsonArray libraryRefs(Doc d) {
        JsonArray a = new JsonArray();
        for (Library lib : d.file().getLibraries()) {
            a.add(libraryRef(d, lib));
        }
        return a;
    }

    static JsonObject libraryRef(Doc d, Library lib) {
        JsonObject o = new JsonObject();
        o.addProperty("lib", lib.getName());
        o.addProperty("display", lib.getDisplayName());
        String desc;
        try {
            desc = d.loader().getDescriptor(lib);
        } catch (RuntimeException e) {
            desc = null;
        }
        if (desc == null || desc.startsWith("#")) {
            o.addProperty("kind", "builtin");
        } else if (desc.startsWith("jar#")) {
            o.addProperty("kind", "jar");
            String rest = desc.substring(4);
            int last = rest.lastIndexOf('#');
            o.addProperty("path", last < 0 ? rest : rest.substring(0, last));
        } else if (desc.startsWith("file#")) {
            o.addProperty("kind", "circ");
            o.addProperty("path", desc.substring(5));
        } else {
            o.addProperty("kind", "builtin");
        }
        return o;
    }

    /** 파일이 MIPS 라이브러리를 쓰는데 저장한 .circ 옆에 hcs-mips.jar가 없는가(원조 2.7.1이 열려면 필요). */
    public static boolean needsMipsJarBeside(Doc d, File saved) {
        return MipsShadow.inFile(d.file()) != null && !MipsShadow.siblingJar(saved).exists();
    }

    private Doc add(Doc d) {
        docs.put(d.id(), d);
        return d;
    }

    private static boolean same(File a, File b) {
        try {
            return a.getCanonicalFile().equals(b.getCanonicalFile());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }
}
