/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.record.RecordingTestSupport;

/**
 * C-09(C-10): .s 자동 재로드는 파일이 바뀐 때만 다시 어셈블해 메모리 초기 내용을 바꾸고(되돌리기 한 번), 어셈블 오류면
 * 옛 내용을 둔다. Console 탭은 Console 출력 전체와 exit를 모은다.
 */
class ProgramReloadTest {
    @TempDir
    Path tmp;

    LogisimFile file;
    Component imem;

    /** ref-mips 옆에 .s를 두고 메뉴 ".s 불러오기"와 같이 source를 상대 경로로 둔다. */
    File setUp(String program) throws Exception {
        file = RecordingTestSupport.openRefMips(tmp);
        Path s = RecordingTestSupport.program(program);
        File circ = file.getLoader().getMainFile();
        File copy = new File(circ.getParentFile(), "prog.s");
        Files.copy(s, copy.toPath());
        RecordingTestSupport.load(file, copy.toPath());
        Circuit main = file.getMainCircuit();
        CircuitMutation m = new CircuitMutation(main);
        for (Component c : main.getNonWires()) {
            String f = c.getFactory().getName();
            if (f.equals("Instruction Memory") || f.equals("Data Memory")) {
                @SuppressWarnings("unchecked")
                Attribute<Object> a = (Attribute<Object>) c.getAttributeSet().getAttribute("source");
                m.set(c, a, "prog.s");
                if (f.equals("Instruction Memory")) {
                    imem = c;
                }
            }
        }
        m.execute();
        return copy;
    }

    String contents() {
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) imem.getAttributeSet().getAttribute("contents");
        return a.toStandardString(imem.getAttributeSet().getValue(a));
    }

    @Test
    void reloadsOnlyWhenTheFileChanges() throws Exception {
        File s = setUp("mips/sum.s");
        Project proj = new Project(file);
        String before = contents();
        // 연 때(처음 봄): 내용이 같으니 바꾸지 않는다
        ProgramReload.Result r = ProgramReload.check(proj, false);
        assertFalse(r.changed());
        assertTrue(r.errors.isEmpty());
        // 바뀌지 않은 파일: 다시 어셈블하지도 않는다
        assertFalse(ProgramReload.check(proj, false).changed());

        // .s를 고쳐 저장: 다시 불러온다
        String text = new String(Files.readAllBytes(s.toPath()), StandardCharsets.UTF_8)
                .replace("li    $t2, 11", "li    $t2, 5");
        Files.write(s.toPath(), text.getBytes(StandardCharsets.UTF_8));
        s.setLastModified(s.lastModified() + 2000);
        r = ProgramReload.check(proj, false);
        assertTrue(r.changed());
        assertEquals("prog.s", r.reloaded.get(0).getName());
        String after = contents();
        assertFalse(before.equals(after));
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) imem.getAttributeSet().getAttribute("contents");
        assertEquals(a.toStandardString(a.parse(ProgramReload.words(ProgramReload.assemble(s), "text"))), after,
                "the new program's words");
        // 되돌리기 한 번에 옛 내용
        proj.undoAction();
        assertEquals(before, contents());
        // 되돌린 뒤 다시 확인해도 파일이 바뀌지 않았으니 그대로(되돌린 것을 다시 덮지 않는다)
        assertFalse(ProgramReload.check(proj, false).changed());

        // 어셈블 오류: 옛 내용을 두고 줄 번호와 함께 알린다
        Files.write(s.toPath(), (text + "\n        addi $t0, $t0,\n").getBytes(StandardCharsets.UTF_8));
        s.setLastModified(s.lastModified() + 4000);
        r = ProgramReload.check(proj, false);
        assertFalse(r.changed());
        assertEquals(1, r.errors.size());
        assertTrue(r.errors.values().iterator().next().contains("syntax error"), r.errors.toString());
        assertEquals(before, contents());
    }

    /** Console 탭 글: sum.s가 exit까지 돌면 "sum=55\n"과 "-- exit --". */
    @Test
    void consoleTextCollectsTheWholeOutput() throws Exception {
        setUp("mips/sum.s");
        Project proj = new Project(file);
        proj.getSimulator().setIsRunning(false);
        CircuitState root = new CircuitState(proj, file.getMainCircuit());
        root.getPropagator().propagate();
        List<ConsoleText.Entry> entries = null;
        for (int step = 1; step <= 600; step++) {
            Propagator p = root.getPropagator();
            p.tick();
            p.propagate();
            entries = ConsoleText.collect(root);
            if (entries.get(0).exited) {
                break;
            }
        }
        assertEquals(1, entries.size());
        assertTrue(entries.get(0).exited);
        assertEquals("sum=55\n", entries.get(0).text);
        assertEquals("sum=55\n-- exit --\n", ConsolePanel.render(entries));
    }
}
