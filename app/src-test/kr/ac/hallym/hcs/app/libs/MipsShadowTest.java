/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.libs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.StringUtil;

import kr.ac.hallym.hcs.app.palette.Palette;
import kr.ac.hallym.hcs.app.palette.ToolboxSearch;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;
import kr.ac.hallym.hcs.regress.Engine;

/** V-01 (D-096): 새 파일에서도 Hallym MIPS가 보이고, 첫 부품을 놓는 순간에만 파일에 들어간다. */
class MipsShadowTest {
    static final File MIPS_JAR = new File(System.getProperty("hcs.mipsJar"));
    static final File ORIGINAL_JAR = new File(System.getProperty("hcs.logisimJar"));

    @TempDir
    Path tmp;

    @BeforeEach
    void bundle() {
        MipsShadow.reset();
        System.setProperty("hcs.bundledMips", MIPS_JAR.getPath());
    }

    @AfterEach
    void clearBundle() {
        System.clearProperty("hcs.bundledMips");
        MipsShadow.reset();
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static Library named(List<?> items, String name) {
        for (Object o : items) {
            if (o instanceof Library && ((Library) o).getDisplayName().equals(name)) {
                return (Library) o;
            }
        }
        return null;
    }

    static Action place(Library lib, Circuit c, String part, int x, int y) {
        ComponentFactory f = ((AddTool) lib.getTool(part)).getFactory();
        CircuitMutation m = new CircuitMutation(c);
        m.add(f.createComponent(Location.create(x, y), f.createAttributeSet()));
        return m.toAction(StringUtil.constantGetter("add " + part));
    }

    @Test
    void aNewFileListsHallymMipsInTheTreeAndInSearch() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        assertNull(MipsShadow.inFile(file), "a new file has no MIPS library");
        Library tree = named(MipsShadow.treeElements(file), "Hallym MIPS");
        assertNotNull(tree, "tree shows Hallym MIPS");
        assertTrue(MipsShadow.isPending(file, tree), "shown as not yet in the file");
        assertFalse(file.getElements().contains(tree), "the file itself is untouched");
        assertNotNull(tree.getTool("Instruction Memory"));

        for (String q : new String[] {"instruction memory", "console"}) {
            boolean found = false;
            for (Palette.Item it : ToolboxSearch.results(q, file)) {
                found |= it.kind == Palette.Kind.COMPONENT && it.library == tree;
            }
            assertTrue(found, q + " is found in a new file");
        }
    }

    @Test
    void savingWithoutMipsPartsIsByteIdenticalToTheOriginalNewFile() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Project proj = new Project(file);
        MipsShadow.treeElements(file); // 목록에 보였다
        proj.doAction(place(file.getLoader().getBuiltin().getLibrary("Gates"), file.getMainCircuit(), "AND Gate", 100, 100));
        File saved = tmp.resolve("plain.circ").toFile();
        CircuitBuilder.save(file, saved);
        String out = read(saved);
        assertFalse(out.contains("jar#"), "no jar library in the file");
        assertFalse(out.contains("hcs:"), "no extension block");
        String template;
        try (InputStream in = Loader.class.getResourceAsStream("/resources/logisim/default.templ")) {
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // 원조 새 파일의 <lib> 목록 그대로(순서·이름)
        assertEquals(libLines(template), libLines(out));
    }

    /** {@code <lib>} 줄의 desc 값들(순서대로). 템플릿은 속성 순서만 다르다. */
    static String libLines(String xml) {
        StringBuilder b = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<lib [^>]*desc=\"([^\"]*)\"").matcher(xml);
        while (m.find()) {
            b.append(m.group(1)).append('\n');
        }
        return b.toString();
    }

    @Test
    void placingInstructionMemoryAddsTheSiblingDescriptorAndUndoRemovesIt() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Project proj = new Project(file);
        Library mips = named(MipsShadow.treeElements(file), "Hallym MIPS");
        proj.doAction(place(mips, file.getMainCircuit(), "Instruction Memory", 200, 200));
        assertEquals(mips, MipsShadow.inFile(file), "the library is now in the file");
        assertFalse(MipsShadow.isPending(file, mips));
        assertEquals(MipsShadow.DESCRIPTOR, file.getLoader().getDescriptor(mips));

        File saved = tmp.resolve("imem.circ").toFile();
        CircuitBuilder.save(file, saved);
        String out = read(saved);
        assertTrue(out.contains("<lib desc=\"jar#hcs-mips.jar#kr.ac.hallym.hcs.mips.MipsLibrary\""), out);
        assertFalse(out.contains(MIPS_JAR.getParentFile().getName()), "the install path is never saved");

        proj.undoAction();
        assertNull(MipsShadow.inFile(file), "undo takes the library out again");
        assertEquals(0, file.getMainCircuit().getNonWires().size());
        File plain = tmp.resolve("undone.circ").toFile();
        CircuitBuilder.save(file, plain);
        assertFalse(read(plain).contains("jar#"));
    }

    @Test
    void theSavedFileOpensInTheOriginalWhenTheJarSitsBesideIt() throws Exception {
        Path dir = tmp.resolve("student");
        Files.createDirectories(dir);
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), dir.toFile());
        Project proj = new Project(file);
        Library mips = named(MipsShadow.treeElements(file), "Hallym MIPS");
        proj.doAction(place(mips, file.getMainCircuit(), "Instruction Memory", 200, 200));
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        b.constant("halt", 1, 1, 400, 100); // -tty table은 halt 핀이 1이 될 때 끝난다
        b.output("halt", 1, 500, 100);
        b.constant("k", 1, 1, 400, 300);
        b.output("out", 1, 500, 300);
        b.commit();
        File saved = dir.resolve("imem.circ").toFile();
        CircuitBuilder.save(file, saved);

        Files.copy(MIPS_JAR.toPath(), dir.resolve("hcs-mips.jar"));
        Engine original = Engine.current(ORIGINAL_JAR);
        String run = original.run(dir.toFile(), "imem");
        // jar를 못 찾으면 헤드리스 파일 선택 창 예외로 exit이 0이 아니다. 값 행이 하나 찍힌다.
        assertTrue(run.startsWith("exit=0\n") && run.trim().split("\n").length == 2, "the original 2.7.1 opens it: " + run);

        // jar 없이 포크로 열면 번들로 연결된다(D-007)
        Path other = tmp.resolve("nojar");
        Files.createDirectories(other);
        File copy = other.resolve("imem.circ").toFile();
        Files.copy(saved.toPath(), copy.toPath());
        LogisimFile reopened = new Loader(null).openLogisimFile(copy);
        Library lib = MipsShadow.inFile(reopened);
        assertNotNull(lib);
        assertNotNull(lib.getTool("Instruction Memory"));
        assertEquals(MipsShadow.DESCRIPTOR, reopened.getLoader().getDescriptor(lib));
        assertFalse(MipsShadow.isPending(reopened, lib));
        assertNull(named(MipsShadow.treeElements(reopened), "Hallym MIPS") == lib ? null : "x",
                "the file's own library is the one in the tree");
        File resaved = other.resolve("resaved.circ").toFile();
        CircuitBuilder.save(reopened, resaved);
        assertEquals(CircNormalizer.normalize(read(saved)), CircNormalizer.normalize(read(resaved)));
    }

    @Test
    void redoAddsTheLibraryBackWithThePart() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Project proj = new Project(file);
        Library mips = named(MipsShadow.treeElements(file), "Hallym MIPS");
        Action a = place(mips, file.getMainCircuit(), "Console", 200, 200);
        proj.doAction(a);
        proj.undoAction();
        assertNull(MipsShadow.inFile(file));
        proj.doAction(a); // RedoStack도 doAction으로 다시 적용한다
        assertEquals(mips, MipsShadow.inFile(file));
        // 부품을 지워도(되돌리기가 아니면) 라이브러리는 남는다: 학생이 뺀 것이 아니다
        CircuitMutation m = new CircuitMutation(file.getMainCircuit());
        m.removeAll(file.getMainCircuit().getNonWires());
        proj.doAction(m.toAction(StringUtil.constantGetter("clear")));
        assertEquals(mips, MipsShadow.inFile(file));
    }

    @Test
    void savingNextToNoJarAsksOncePerFileAndCopiesOnRequest() throws Exception {
        Path dir = tmp.resolve("save");
        Files.createDirectories(dir);
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), dir.toFile());
        Project proj = new Project(file);
        File saved = dir.resolve("a.circ").toFile();
        assertFalse(MipsShadow.needsJarNotice(proj, saved), "no MIPS parts: nothing to say");
        Library mips = named(MipsShadow.treeElements(file), "Hallym MIPS");
        proj.doAction(place(mips, file.getMainCircuit(), "Data Memory", 200, 200));
        assertTrue(MipsShadow.needsJarNotice(proj, saved));
        assertFalse(MipsShadow.needsJarNotice(proj, saved), "only once per file");
        assertFalse(MipsShadow.siblingJar(saved).exists(), "nothing is copied quietly");
        assertTrue(MipsShadow.needsJarNotice(proj, dir.resolve("b.circ").toFile()), "another name asks again");

        File dest = MipsShadow.copyJarBeside(saved);
        assertEquals(new File(dir.toFile(), "hcs-mips.jar"), dest);
        assertEquals(MIPS_JAR.length(), dest.length());
        assertFalse(MipsShadow.needsJarNotice(proj, dir.resolve("c.circ").toFile()), "the jar is there now");
    }

    @Test
    void aFileThatAlreadyHasMipsShowsItsOwnLibraryOnly() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Library own = file.getLoader().loadJarLibrary(MIPS_JAR, kr.ac.hallym.hcs.app.BundledLibraries.MIPS_CLASS);
        file.addLibrary(own);
        List<?> tree = MipsShadow.treeElements(file);
        int n = 0;
        for (Object o : tree) {
            if (o instanceof Library && ((Library) o).getDisplayName().equals("Hallym MIPS")) {
                n++;
            }
        }
        assertEquals(1, n, "one Hallym MIPS, the file's own");
        assertEquals(own, MipsShadow.inFile(file));
        assertEquals(file.getLibraries().size(), MipsShadow.libraries(file).size());
    }
}
