/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.mips.image.HmxParser;

/**
 * 테스트 도우미: ref-mips.circ를 임시 폴더에 열고, 실행 이미지(tests/hmx의 .hmx, vendor/spim이 있을 때 hcs-asm
 * {@code -exception}으로 만들어 굳힌 것, D-141)를 읽어 Instruction Memory·Data Memory의 초기 내용 속성("contents",
 * hcs-words 형식)에 넣는다. 메뉴의 Load Program(파일 고르기 창)을 거치지 않는다.
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

    /** tests 아래 파일. 예: {@code hmx/mips/factorial.hmx}. */
    public static Path program(String relative) {
        return new File(System.getProperty("hcs.testsDir"), relative).toPath();
    }

    /** 워드들(주소 → 워드)을 hcs-words 형식으로. 비어 있으면 빈 글. */
    static String words(Map<Long, Integer> words) {
        if (words.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("hcs-words 1\n");
        for (Map.Entry<Long, Integer> e : words.entrySet()) {
            sb.append(String.format("%08x %08x", e.getKey(), e.getValue())).append('\n');
        }
        return sb.toString();
    }

    /** 실행 이미지(.hmx)를 읽어 main 회로의 Instruction Memory와 Data Memory 초기 내용으로 둔다. */
    @SuppressWarnings("unchecked")
    public static void load(LogisimFile file, Path image) throws Exception {
        HmxParser.Result r = HmxParser.read(image.toFile());
        if (!r.ok()) {
            throw new IllegalStateException(image + ": " + r.errors);
        }
        Circuit main = file.getMainCircuit();
        CircuitMutation m = new CircuitMutation(main);
        for (Component c : main.getNonWires()) {
            String kind = c.getFactory().getName();
            String text = kind.equals("Instruction Memory") ? words(r.image.textWords())
                    : kind.equals("Data Memory") ? words(r.image.dataWords()) : null;
            if (text == null || text.isEmpty()) {
                continue;
            }
            Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute("contents");
            m.set(c, a, a.parse(text));
        }
        m.execute();
    }
}
