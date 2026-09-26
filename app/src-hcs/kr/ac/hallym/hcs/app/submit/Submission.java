/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.submit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
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

import kr.ac.hallym.hcs.app.BundledLibraries;
import kr.ac.hallym.hcs.app.Messages;

/**
 * 제출 파일 만들기(E-06, PLAN.md 11.13): .circ와 불러온 .s, 불러온 라이브러리(.circ, JAR)를 zip 하나로 묶는다. 묶기 전에
 * 점검한다: 저장했는지, Messages 0건인지, 남은 Probe, 원조 2.7.1에서 열리는지(가리키는 파일이 모두 함께 들어가는지).
 * 점검은 알리기만 하고 막지 않는다(학생이 판단한다). 파일은 .circ 폴더 기준 상대 경로 그대로 넣어, 풀면 원조 2.7.1과
 * 이 도구에서 그대로 열린다. MIPS 부품 jar는 .circ가 가리키는 이름으로 번들 jar를 넣는다. GUI 없이 테스트한다.
 */
public final class Submission {
    /** 점검 한 줄. */
    public static final class Check {
        public final boolean ok;
        public final String text;

        Check(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }

        @Override
        public String toString() {
            return (ok ? "OK " : "! ") + text;
        }
    }

    /** 묶을 파일: zip 안 경로 → 실제 파일. */
    public final Map<String, File> files = new LinkedHashMap<>();
    /** 가리키지만 찾지 못했거나 .circ 폴더 밖에 있어 넣지 못한 파일. */
    public final List<String> missing = new ArrayList<>();
    public final List<Check> checks = new ArrayList<>();

    private Submission() {
    }

    /**
     * 점검하고 묶을 파일을 모은다. messages는 Messages 탭의 진단 수, dirty는 저장하지 않은 변경이 있는지(GUI가 준다).
     */
    public static Submission plan(LogisimFile file, int messages, boolean dirty) {
        Submission s = new Submission();
        Loader loader = file.getLoader();
        File circ = loader.getMainFile();
        File dir = circ == null ? null : circ.getParentFile();
        s.checks.add(new Check(circ != null && !dirty, Messages.get(circ == null ? "submit.notSaved"
                : dirty ? "submit.dirty" : "submit.saved")));
        if (circ != null) {
            s.files.put(circ.getName(), circ);
        }
        // 라이브러리
        for (Library lib : file.getLibraries()) {
            String desc = loader.getDescriptor(lib);
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
        // 메모리에 불러온 .s(속성 source)
        int probes = 0;
        for (Circuit c : file.getCircuits()) {
            for (Component x : c.getNonWires()) {
                String f = x.getFactory().getName();
                if (f.equals("Probe") || f.equals("Radix Probe")) {
                    probes++;
                }
                @SuppressWarnings("unchecked")
                Attribute<Object> src = (Attribute<Object>) x.getAttributeSet().getAttribute("source");
                Object v = src == null ? null : x.getAttributeSet().getValue(src);
                if (v != null && !v.toString().isEmpty()) {
                    s.add(dir, v.toString(), null);
                }
            }
        }
        s.checks.add(new Check(messages == 0, messages == 0 ? Messages.get("submit.noMessages")
                : Messages.get("submit.messages", messages)));
        s.checks.add(new Check(probes == 0, probes == 0 ? Messages.get("submit.noProbes")
                : Messages.get("submit.probes", probes)));
        s.checks.add(new Check(s.missing.isEmpty(), s.missing.isEmpty() ? Messages.get("submit.opens")
                : Messages.get("submit.missing", String.join(", ", s.missing))));
        return s;
    }

    /** .circ 폴더 기준 상대 경로 rel의 파일을 넣는다. fallback은 없을 때 대신 넣을 파일(번들 jar). */
    private void add(File dir, String rel, File fallback) {
        String norm = rel.replace('\\', '/');
        File f = new File(norm).isAbsolute() || dir == null ? new File(norm) : new File(dir, norm);
        boolean inside = !new File(norm).isAbsolute() && !norm.startsWith("../") && !norm.contains("/../");
        if (!inside) {
            missing.add(rel);
            return;
        }
        if (files.containsKey(norm)) {
            return;
        }
        if (f.isFile()) {
            files.put(norm, f);
        } else if (fallback != null && fallback.isFile()) {
            files.put(norm, fallback);
        } else {
            missing.add(rel);
        }
    }

    /** 이름 제안: {@code <파일>-submission.zip}(.circ 옆). */
    public static File suggestedZip(LogisimFile file) {
        File circ = file.getLoader().getMainFile();
        String base = circ == null ? file.getName() : circ.getName().replaceFirst("\\.circ$", "");
        return new File(circ == null ? new File(".") : circ.getParentFile(), base + "-submission.zip");
    }

    /** zip으로 쓴다. */
    public void write(File zip) throws IOException {
        try (OutputStream out = Files.newOutputStream(zip.toPath()); ZipOutputStream z = new ZipOutputStream(out)) {
            for (Map.Entry<String, File> e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                Files.copy(e.getValue().toPath(), z);
                z.closeEntry();
            }
        }
    }
}
