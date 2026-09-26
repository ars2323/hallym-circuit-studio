/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

/**
 * 테스트 도우미: ref-mips.circ를 임시 폴더에 열고, .s를 hcs-asm으로 어셈블해 Instruction Memory·Data Memory의 초기
 * 내용 속성("contents", hcs-words 형식)에 넣는다. 메뉴의 .s 불러오기(파일 고르기 창)를 거치지 않는다.
 */
public final class RecordingTestSupport {
    private RecordingTestSupport() {
    }

    public static LogisimFile openRefMips(Path tmp) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "ref");
        Files.copy(new File(System.getProperty("hcs.mipsJar")).toPath(), dir.resolve("hcs-mips.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Path to = dir.resolve("ref-mips.circ");
        Files.copy(new File(System.getProperty("hcs.refMips")).toPath(), to);
        return new Loader(null).openLogisimFile(to.toFile());
    }

    /** tests/circ의 회로를 임시 폴더에 hcs-mips.jar와 함께 연다(예: demo-datapath.circ). */
    public static LogisimFile openCirc(Path tmp, String name) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "circ");
        Files.copy(new File(System.getProperty("hcs.mipsJar")).toPath(), dir.resolve("hcs-mips.jar"),
                StandardCopyOption.REPLACE_EXISTING);
        Path to = dir.resolve(name);
        Files.copy(new File(System.getProperty("hcs.circDir"), name).toPath(), to);
        return new Loader(null).openLogisimFile(to.toFile());
    }

    public static Path program(String relative) {
        return new File(System.getProperty("hcs.testsDir"), relative).toPath();
    }

    /** hcs-asm의 JSON에서 text 또는 data 칸의 워드를 hcs-words 형식으로. */
    static String words(String json, String section) {
        int start = json.indexOf("\"" + section + "\"");
        if (start < 0) {
            return "";
        }
        int end = json.indexOf(']', start);
        Matcher m = Pattern.compile("\"addr\":\\s*\"0x([0-9a-f]+)\",\\s*\"word\":\\s*\"0x([0-9a-f]+)\"")
                .matcher(json.substring(start, end));
        TreeMap<Long, String> words = new TreeMap<>();
        while (m.find()) {
            words.put(Long.parseLong(m.group(1), 16), m.group(2));
        }
        if (words.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("hcs-words 1\n");
        for (java.util.Map.Entry<Long, String> e : words.entrySet()) {
            sb.append(String.format("%08x", e.getKey())).append(' ').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public static String assemble(Path source) throws Exception {
        Process p = new ProcessBuilder(System.getProperty("hcs.asm"), source.toString()).redirectErrorStream(false)
                .start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException("hcs-asm failed for " + source);
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    /** .s를 어셈블해 main 회로의 Instruction Memory와 Data Memory 초기 내용으로 둔다. */
    @SuppressWarnings("unchecked")
    public static void load(LogisimFile file, Path source) throws Exception {
        String json = assemble(source);
        Circuit main = file.getMainCircuit();
        CircuitMutation m = new CircuitMutation(main);
        for (Component c : main.getNonWires()) {
            String kind = c.getFactory().getName();
            String text = kind.equals("Instruction Memory") ? words(json, "text")
                    : kind.equals("Data Memory") ? words(json, "data") : null;
            if (text == null || text.isEmpty()) {
                continue;
            }
            Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute("contents");
            m.set(c, a, a.parse(text));
        }
        m.execute();
    }
}
