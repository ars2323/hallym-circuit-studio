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
import java.util.Set;

import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
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
    /** 저장 전에 가리키는 것이 없어진 확장 항목을 지우는 쪽(Swing 앱은 ContextMenus가 등록한다). 복구 파일도 쓴다. */
    static final List<CircExtensions.Pruner> PRUNERS = List.of(
            kr.ac.hallym.hcs.app.splitter.SplitterEdits.PRUNER,
            kr.ac.hallym.hcs.app.labels.TunnelColorStore.PRUNER,
            kr.ac.hallym.hcs.app.groups.SignalGroups.PRUNER,
            kr.ac.hallym.hcs.app.sim.PcMark.PRUNER);

    static {
        for (CircExtensions.Pruner p : PRUNERS) {
            CircExtensions.addPruner(p);
        }
    }

    private final Map<String, Doc> docs = new LinkedHashMap<>();
    /**
     * 학생 파일 옆의 복구 파일을 엔진이 맡는가(N-19, D-152): 화면이 {@code engine.hello}의 {@code recoveryFiles}로
     * 켠다. 켜져 있으면 저장·닫기·정상 종료에 복구 파일을 지우고, 화면이 사라져 끝날 때(stdin 닫힘·부모 끝남) 저장하지
     * 않은 파일의 복구 파일을 쓴다. 꺼져 있으면(테스트, 다른 클라이언트) 명시한 요청({@code file.recoverWrite},
     * {@code file.open}의 {@code recovery}) 말고는 복구 파일을 건드리지 않는다.
     */
    private boolean recoveryFiles;

    public void manageRecoveryFiles(boolean on) {
        recoveryFiles = on;
    }

    public boolean managesRecoveryFiles() {
        return recoveryFiles;
    }

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

    /**
     * 다시 시작한 엔진이 파일을 되살릴 때 쓰는 앞 엔진의 id(D-142, docs/engine-api.md 7절): 파일 id와 {회로 이름: 회로 id}.
     */
    public static final class Restore {
        final String fileId;
        final Map<String, String> circuits;

        public Restore(String fileId, Map<String, String> circuits) {
            this.fileId = fileId;
            this.circuits = circuits == null ? Map.of() : circuits;
        }
    }

    /** File › New와 같다: 원조 기본 틀(default.templ)로 새 파일. */
    public Doc create() throws RpcError {
        return create(null);
    }

    /** restore가 있으면 앞 엔진의 파일·회로 id로 만든다. */
    public Doc create(Restore restore) throws RpcError {
        String restored = restoredId(restore);
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
        String id = restored != null ? restored : Ids.nextFileId();
        return add(new Doc(id, loader, file, false, restore == null ? null : restore.circuits));
    }

    /** File › Open과 같다. 없는 라이브러리는 창으로 묻지 않고 오류로 알린다. */
    public Doc open(File f, boolean readOnly, List<String> messages) throws RpcError {
        return open(f, readOnly, messages, null);
    }

    /** restore가 있으면 앞 엔진의 파일·회로 id로 연다. */
    public Doc open(File f, boolean readOnly, List<String> messages, Restore restore) throws RpcError {
        return open(f, readOnly, messages, restore, false);
    }

    /**
     * recover면 옆의 복구 파일({@link RecoveryFiles})의 내용을 이 파일 자리에서 연다(N-19, D-152): 원조 Loader의 바꿔
     * 읽기(명령줄 {@code -sub}와 같은 길)라 연 파일의 경로·이름·상대 경로 라이브러리는 f의 것이고, 내용은 저장하지 않은
     * 편집이다(dirty, 저장하면 f에 쓴다).
     */
    public Doc open(File f, boolean readOnly, List<String> messages, Restore restore, boolean recover)
            throws RpcError {
        String restored = restoredId(restore);
        String path = f.getPath();
        if (!f.isFile()) {
            throw RpcError.file(path, "notFound", "no such file: " + path);
        }
        if (!f.canRead()) {
            throw RpcError.file(path, "unreadable", "cannot read: " + path);
        }
        File source = f;
        if (recover) {
            source = RecoveryFiles.of(f);
            if (!source.isFile()) {
                throw RpcError.file(source.getPath(), "notFound", "no recovery file: " + source.getPath());
            }
            if (!source.canRead()) {
                throw RpcError.file(source.getPath(), "unreadable", "cannot read: " + source.getPath());
            }
            readOnly = false;
        }
        List<String> missing = LibraryCheck.missing(source);
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
            file = recover ? loader.openLogisimFile(f, RecoveryFiles.substitution(f)) : loader.openLogisimFile(f);
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
        if (recover) {
            file.setName(RecoveryFiles.projectName(f)); // 원조가 읽은 파일(복구 파일)의 이름 대신 학생 파일의 이름
        }
        try {
            CircExtensions.afterOpen(file, source); // 확장 정보를 못 읽어도 회로는 연다(Swing과 같다)
        } catch (IOException e) {
            messages.add("extension info: " + e.getMessage());
        }
        messages.addAll(loader.drainErrors());
        String id = restored != null ? restored : Ids.nextFileId();
        Doc d = add(new Doc(id, loader, file, readOnly, restore == null ? null : restore.circuits));
        d.setRecovered(recover);
        return d;
    }

    /** 되살리는 파일의 앞 id(없으면 null). 형식이 틀리거나 지금 열린 파일이 쓰면 -32602. */
    private String restoredId(Restore restore) throws RpcError {
        if (restore == null || restore.fileId == null) {
            return null;
        }
        String id = Ids.restoredFileId(restore.fileId);
        if (id == null) {
            throw RpcError.params("restore.fileId must be \"f\" and a number: " + restore.fileId);
        }
        if (docs.containsKey(id)) {
            throw RpcError.params("restore.fileId is in use: " + id);
        }
        return id;
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
        File before = RecoveryFiles.target(d);
        d.loader().drainErrors(); // 앞에 남은 글은 이 저장과 무관하다
        boolean ok;
        // 원조 저장은 부품의 라이브러리를 찾으며 앞 라이브러리의 도구를 불러온다: 저장이 만든 것은 되돌린다(D-149)
        Set<Tool> untouched = OwnTools.untouched(d.loader(), d.file());
        try {
            ok = d.loader().save(d.file(), dest);
        } catch (HeadlessException | IllegalStateException e) {
            ok = false; // 원조가 오류 창을 열려던 자리
        } finally {
            OwnTools.afterSave(d.loader(), d.file(), untouched);
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
        d.setRecovered(false);
        if (recoveryFiles) {
            // 저장했다: 저장하지 않은 편집이 없다. 앞 경로(다른 이름으로 저장이면 그 앞)와 새 경로 옆의 복구 파일을 지운다
            if (before != null) {
                RecoveryFiles.delete(before);
            }
            RecoveryFiles.delete(dest);
        }
        return dest;
    }

    /**
     * 복구 파일을 쓴다(N-19, D-152). 저장하지 않은 편집이 없으면 쓰지 않고 있던 것을 지운다. 둘 곳이 없는 파일(새 파일,
     * 읽기 전용)이면 null.
     */
    public File recoverWrite(Doc d) throws RpcError {
        File circ = RecoveryFiles.target(d);
        if (circ == null) {
            return null;
        }
        if (!d.isDirty()) {
            RecoveryFiles.delete(circ);
            return null;
        }
        try {
            return RecoveryFiles.write(d);
        } catch (IOException | RuntimeException e) {
            File rf = RecoveryFiles.of(circ);
            throw RpcError.file(rf.getPath(), "writeFailed", "cannot write " + rf.getPath() + ": " + e.getMessage());
        }
    }

    /** 닫기. 복구 파일을 맡고 있으면 지운다(학생이 저장하지 않고 닫기를 골랐다). keepRecovery면 둔다. */
    public void close(Doc d, boolean keepRecovery) {
        if (recoveryFiles && !keepRecovery) {
            RecoveryFiles.delete(RecoveryFiles.target(d));
        }
        close(d);
    }

    public void close(Doc d) {
        docs.remove(d.id());
        d.close();
    }

    /**
     * 엔진이 끝날 때. normal(화면이 {@code engine.shutdown}으로 끝냄: 앱을 정상 종료)이면 복구 파일을 지운다. 아니면
     * (stdin 닫힘·부모 끝남: 화면이 사라졌다) 저장하지 않은 파일마다 복구 파일을 써 둔다. 맡지 않으면 둘 다 하지 않는다.
     */
    public void closeAll(boolean normal) {
        if (recoveryFiles) {
            // 화면이 사라진 뒤의 쓰기는 이 시간 안에서만 한다(그 뒤의 파일은 앞서 쓴 복구 파일이 남는다). 엔진은 어차피
            // Server.haltAfter의 시한에 끝난다
            long budget = Long.getLong("hcs.recoveryWriteMs", 5_000L) * 1_000_000L;
            long start = System.nanoTime();
            for (Doc d : docs.values()) {
                try {
                    if (normal) {
                        RecoveryFiles.delete(RecoveryFiles.target(d));
                    } else if (d.isDirty() && System.nanoTime() - start < budget) {
                        RecoveryFiles.write(d);
                    }
                } catch (IOException | RuntimeException e) {
                    // 쓰지 못한 복구 파일: 앞서 쓴 것이 남는다
                }
            }
        }
        closeAll();
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
