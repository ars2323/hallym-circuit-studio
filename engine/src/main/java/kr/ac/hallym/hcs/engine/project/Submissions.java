/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.project;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.Library;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.BundledLibraries;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * File › Create Submission…(E-06, N-21, D-162): v1 {@code Submission}의 규칙 그대로 창만 뺐다. 저장한 .circ와 그 파일이
 * 가리키는 것(불러온 .circ·JAR 라이브러리, 메모리 부품이 불러온 프로그램 파일 = 속성 {@code source}, 지금은 .hmx)을
 * .circ 폴더 기준 상대 경로 그대로 zip 하나에 넣는다. 그래서 풀면 원조 2.7.1과 이 도구에서 그대로 열린다. MIPS 부품
 * jar는 .circ가 가리키는 자리에 없으면 번들 jar를 그 이름으로 넣는다. 점검(저장, Messages 0건, 남은 Probe, 모두
 * 들어가는지)은 사실만 알리고 막지 않는다(학생이 판단한다). 문장은 화면이 만든다(여기는 사실과 수).
 */
public final class Submissions {
    /** 묶을 파일: zip 안 경로 → 실제 파일. */
    final Map<String, File> files = new LinkedHashMap<>();
    /** 가리키지만 찾지 못했거나 .circ 폴더 밖이라 넣지 못한 것(적힌 글자 그대로). */
    final List<String> missing = new ArrayList<>();
    int probes;
    boolean bundledJar;

    private Submissions() {
    }

    /** 점검하고 묶을 파일을 모은다(저장한 파일 기준: 디스크의 .circ가 들어간다). */
    static Submissions plan(LogisimFile file) {
        Submissions s = new Submissions();
        Loader loader = file.getLoader();
        File circ = loader.getMainFile();
        File dir = circ == null ? null : circ.getAbsoluteFile().getParentFile();
        if (circ != null) {
            s.files.put(circ.getName(), circ.getAbsoluteFile());
        }
        for (Library lib : file.getLibraries()) {
            String desc = loader.getDescriptor(lib);
            if (desc == null) {
                continue;
            }
            if (desc.startsWith("file#")) {
                s.add(dir, desc.substring("file#".length()), null);
            } else if (desc.startsWith("jar#")) {
                String rest = desc.substring("jar#".length());
                int sep = rest.lastIndexOf('#');
                String path = sep < 0 ? rest : rest.substring(0, sep);
                String cls = sep < 0 ? "" : rest.substring(sep + 1);
                File bundled = BundledLibraries.MIPS_CLASS.equals(cls) ? BundledLibraries.mipsJar() : null;
                s.add(dir, path, bundled);
            }
        }
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                String f = x.getFactory().getName();
                if (f.equals("Probe") || f.equals("Radix Probe")) {
                    s.probes++;
                }
                @SuppressWarnings("unchecked")
                Attribute<Object> src = (Attribute<Object>) x.getAttributeSet().getAttribute("source");
                Object v = src == null ? null : x.getAttributeSet().getValue(src);
                if (v != null && !v.toString().isEmpty()) {
                    s.add(dir, v.toString(), null);
                }
            }
        }
        return s;
    }

    /** .circ 폴더 기준 상대 경로 rel의 파일을 넣는다. fallback은 없을 때 대신 넣을 파일(번들 jar). */
    private void add(File dir, String rel, File fallback) {
        String norm = rel.replace('\\', '/');
        boolean inside = dir != null && !new File(norm).isAbsolute() && !norm.startsWith("/") && !norm.startsWith("../")
                && !norm.contains("/../") && !norm.equals("..") && !norm.matches("^[A-Za-z]:.*");
        if (!inside) {
            if (!missing.contains(rel)) {
                missing.add(rel);
            }
            return;
        }
        while (norm.startsWith("./")) {
            norm = norm.substring(2);
        }
        if (files.containsKey(norm)) {
            return;
        }
        File f = new File(dir, norm);
        if (f.isFile()) {
            files.put(norm, f);
        } else if (fallback != null && fallback.isFile()) {
            files.put(norm, fallback);
            bundledJar = true;
        } else if (!missing.contains(rel)) {
            missing.add(rel);
        }
    }

    /**
     * file.submission = {saved, dirty, messages, probes, missing:[…], files:[zip 안 경로], suggested}: 점검과 묶을
     * 파일. saved: 디스크에 저장한 적이 있다, dirty: 저장하지 않은 편집이 있다(zip에는 마지막으로 저장한 파일이 든다),
     * suggested: {@code <이름>-submission.zip}(.circ 옆). path를 주면(main만: 저장 창에서 고른 곳) 그 zip도 쓰고
     * {@code written:{path, bytes, count}}를 더한다. 한 번도 저장하지 않은 파일은 쓰지 않는다(오류 -32602).
     */
    public static JsonObject run(Doc d, int messages, String path) throws RpcError {
        Submissions s = plan(d.file());
        File circ = d.loader().getMainFile();
        JsonObject o = new JsonObject();
        o.addProperty("saved", circ != null);
        o.addProperty("dirty", d.isDirty());
        o.addProperty("messages", messages);
        o.addProperty("probes", s.probes);
        o.addProperty("bundledJar", s.bundledJar);
        JsonArray missing = new JsonArray();
        s.missing.forEach(missing::add);
        o.add("missing", missing);
        JsonArray names = new JsonArray();
        s.files.keySet().forEach(names::add);
        o.add("files", names);
        String base = circ == null ? d.file().getName() : circ.getName().replaceFirst("(?i)\\.circ$", "");
        o.addProperty("suggested", base + "-submission.zip");
        if (path != null) {
            if (circ == null) {
                throw RpcError.params("the file was never saved");
            }
            File zip = new File(path).getAbsoluteFile();
            if (!zip.getName().toLowerCase().endsWith(".zip")) {
                zip = new File(zip.getParentFile(), zip.getName() + ".zip");
            }
            try {
                long bytes = s.write(zip);
                JsonObject w = new JsonObject();
                w.addProperty("path", zip.getPath());
                w.addProperty("name", zip.getName());
                w.addProperty("bytes", bytes);
                w.addProperty("count", s.files.size());
                o.add("written", w);
            } catch (IOException e) {
                throw RpcError.file(zip.getPath(), "writeFailed", e.getMessage());
            }
        }
        return o;
    }

    /** zip으로 쓴다(옆의 임시 파일에 다 쓴 뒤 옮긴다: 실패하면 있던 zip이 그대로). 쓴 바이트 수. */
    long write(File zip) throws IOException {
        File dir = zip.getParentFile();
        File tmp = File.createTempFile(".hcs-submission-", ".tmp", dir);
        try {
            try (OutputStream out = Files.newOutputStream(tmp.toPath()); ZipOutputStream z = new ZipOutputStream(out)) {
                for (Map.Entry<String, File> e : files.entrySet()) {
                    if (e.getValue().getAbsoluteFile().equals(zip.getAbsoluteFile())) {
                        continue; // zip 자신(프로그램 경로가 우연히 zip이면)
                    }
                    ZipEntry entry = new ZipEntry(e.getKey());
                    entry.setTime(e.getValue().lastModified());
                    z.putNextEntry(entry);
                    Files.copy(e.getValue().toPath(), z);
                    z.closeEntry();
                }
            }
            Files.move(tmp.toPath(), zip.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return zip.length();
        } finally {
            Files.deleteIfExists(tmp.toPath());
        }
    }
}
