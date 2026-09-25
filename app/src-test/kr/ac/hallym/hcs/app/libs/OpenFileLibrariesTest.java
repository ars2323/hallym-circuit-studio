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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.palette.Palette;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * P-03 탭 간 라이브러리(GUI 없이): 1bit_adder.circ와 ripple_carry.circ. "Open Files" 후보, 자동 Load Library와
 * 상대 경로 저장, 순환 차단, 포트 변경으로 끊길 연결, 다시 열 때 알림.
 */
class OpenFileLibrariesTest {
    @TempDir
    Path tmp;

    private Supplier<Collection<Project>> saved;
    private final List<Project> open = new ArrayList<>();

    @BeforeEach
    void setUp() {
        saved = OpenFileLibraries.openProjects;
        OpenFileLibraries.openProjects = () -> open;
    }

    @AfterEach
    void tearDown() {
        OpenFileLibraries.openProjects = saved;
    }

    /** 1bit_adder: 입력 a, b, cin과 출력 s, cout(속은 선 하나씩이면 충분하다). */
    static File adder(File dir) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        Circuit c = f.getMainCircuit();
        c.setName("1bit_adder");
        CircuitBuilder b = new CircuitBuilder(f, c);
        b.add("Wiring", "Pin", 100, 100, "label", "a");
        b.add("Wiring", "Pin", 100, 140, "label", "b");
        b.add("Wiring", "Pin", 100, 180, "label", "cin");
        b.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "s");
        b.add("Wiring", "Pin", 300, 180, "facing", "west", "output", "true", "label", "cout");
        b.wire(Location.create(100, 100), Location.create(300, 100));
        b.wire(Location.create(100, 180), Location.create(300, 180));
        b.commit();
        File out = new File(dir, "1bit_adder.circ");
        CircuitBuilder.save(f, out);
        return out;
    }

    static Project open(File circ) throws Exception {
        Project p = new Project(new Loader(null).openLogisimFile(circ));
        p.getSimulator().setIsRunning(false);
        return p;
    }

    /** 저장한 빈 ripple_carry.circ의 프로젝트. */
    static Project ripple(File dir) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), dir);
        File out = new File(dir, "ripple_carry.circ");
        CircuitBuilder.save(f, out);
        return open(out);
    }

    /** ripple의 main에 1bit_adder 인스턴스 fa0, fa1을 놓고 포트마다 선을 단다(이어진 포트 5개씩). */
    static List<Component> placeAdders(Project ripple, LoadedLibrary lib) {
        LogisimFile f = ripple.getLogisimFile();
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        Component fa0 = b.add(lib, "1bit_adder", 300, 200, "label", "fa0");
        Component fa1 = b.add(lib, "1bit_adder", 300, 400, "label", "fa1");
        b.commit();
        CircuitMutation m = new CircuitMutation(f.getMainCircuit());
        for (Component inst : Arrays.asList(fa0, fa1)) {
            for (int i = 0; i < inst.getEnds().size(); i++) {
                Location p = inst.getEnd(i).getLocation();
                m.add(Wire.create(p, p.translate(inst.getEnd(i).isOutput() ? 30 : -30, 0)));
            }
        }
        m.execute();
        return Arrays.asList(fa0, fa1);
    }

    @Test
    void openFilesOffersOtherSavedFilesUntilLoaded() throws Exception {
        File dir = tmp.toFile();
        Project adder = open(adder(dir));
        Project ripple = ripple(dir);
        Project unsaved = new Project(CircuitBuilder.newFile(new Loader(null), dir));
        open.addAll(Arrays.asList(adder, ripple, unsaved));

        List<OpenFileLibraries.OpenCircuit> c = OpenFileLibraries.candidates(ripple);
        assertEquals(1, c.size(), "only the saved other file (not itself, not the unsaved one)");
        assertEquals("1bit_adder", c.get(0).circuit.getName());
        assertEquals("1bit_adder.circ", c.get(0).fileName());

        // 검색 "Open Files" 묶음
        List<Palette.Item> found = Palette.search("adder", new ArrayList<>(ripple.getLogisimFile().getLibraries()),
                ripple.getLogisimFile().getCircuits(), Collections.emptyList(), Collections.emptyList(), c);
        Palette.Item item = found.stream().filter(it -> it.kind == Palette.Kind.OPEN_FILE).findFirst().orElse(null);
        assertNotNull(item, "Open Files item in the search");
        assertEquals(new File(dir, "1bit_adder.circ").getPath(), item.command);

        // 고르면 Load Library(되돌리기 한 번), 두 번째는 같은 라이브러리
        OpenFileLibraries.Result r = OpenFileLibraries.ensureLoaded(ripple, c.get(0).file);
        assertNotNull(r.library);
        assertEquals("file#1bit_adder.circ", ripple.getLogisimFile().getLoader().getDescriptor(r.library),
                "relative path, like the original Load Library");
        assertTrue(OpenFileLibraries.ensureLoaded(ripple, c.get(0).file).library == r.library);
        assertNotNull(OpenFileLibraries.toolFor(r.library, "1bit_adder"));
        assertTrue(OpenFileLibraries.candidates(ripple).isEmpty(), "already a library: its circuits are in the tree");
        assertNotNull(ripple.getLastAction(), "one undoable Load Library");
        ripple.undoAction();
        assertNull(OpenFileLibraries.loaded(ripple, c.get(0).file), "undo removes the library");
    }

    @Test
    void circularLibrariesAreRefused() throws Exception {
        File dir = tmp.toFile();
        File adderFile = adder(dir);
        Project adder = open(adderFile);
        Project ripple = ripple(dir);
        open.addAll(Arrays.asList(adder, ripple));
        assertNotNull(OpenFileLibraries.ensureLoaded(ripple, adderFile).library);
        File rippleFile = OpenFileLibraries.fileOf(ripple);
        CircuitBuilder.save(ripple.getLogisimFile(), rippleFile);

        assertTrue(OpenFileLibraries.circular(adderFile, ripple.getLogisimFile()));
        assertTrue(OpenFileLibraries.candidates(adder).isEmpty(), "ripple_carry uses 1bit_adder: not offered");
        OpenFileLibraries.Result r = OpenFileLibraries.ensureLoaded(adder, rippleFile);
        assertNull(r.library);
        assertEquals("circular", r.refused);
        assertEquals("self", OpenFileLibraries.ensureLoaded(adder, adderFile).refused);
        assertFalse(adder.getLogisimFile().getLibraries().stream().anyMatch(l -> l instanceof LoadedLibrary));
    }

    @Test
    void portChangesReportTheCutConnections() throws Exception {
        File dir = tmp.toFile();
        File adderFile = adder(dir);
        Project adder = open(adderFile);
        Project ripple = ripple(dir);
        open.addAll(Arrays.asList(adder, ripple));
        LoadedLibrary lib = OpenFileLibraries.ensureLoaded(ripple, adderFile).library;
        placeAdders(ripple, lib);
        assertEquals(1, LibrarySync.users(adder, adderFile).size());

        // 속만 바꾸면(포트 그대로) 끊기는 곳이 없다
        Circuit c = adder.getLogisimFile().getCircuit("1bit_adder");
        CircuitBuilder b = new CircuitBuilder(adder.getLogisimFile(), c);
        b.add("Gates", "NOT Gate", 200, 300);
        b.commit();
        assertTrue(LibrarySync.impact(adder, adderFile).isEmpty(), "inside change only");

        // 출력 s를 지운다: fa0, fa1의 s 연결 2곳이 끊긴다(다른 포트도 모양이 바뀌어 밀리면 그만큼 더)
        Component s = c.getNonWires().stream().filter(x -> "s".equals(kr.ac.hallym.hcs.app.model.Names.label(x)))
                .findFirst().get();
        CircuitMutation m = new CircuitMutation(c);
        m.remove(s);
        m.execute();
        List<LibrarySync.Cut> cuts = LibrarySync.impact(adder, adderFile);
        assertEquals(1, cuts.size());
        assertEquals("ripple_carry.circ", cuts.get(0).file);
        assertEquals(Arrays.asList("fa0", "fa1"), cuts.get(0).instances);
        assertTrue(cuts.get(0).connections >= 2, "at least the two s ports: " + cuts.get(0).connections);
        String msg = cuts.get(0).message();
        assertTrue(msg.contains("ripple_carry.circ") && msg.contains("fa0, fa1"), msg);
    }

    @Test
    void reopeningNoticesNewerAndMovedLibraries() throws Exception {
        File dir = tmp.toFile();
        File adderFile = adder(dir);
        Project ripple = ripple(dir);
        open.add(ripple);
        OpenFileLibraries.ensureLoaded(ripple, adderFile);
        File rippleFile = OpenFileLibraries.fileOf(ripple);
        CircuitBuilder.save(ripple.getLogisimFile(), rippleFile);
        String xml = new String(Files.readAllBytes(rippleFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<lib desc=\"file#1bit_adder.circ\""), "saved as a relative path");

        // 그대로 다시 열면 알릴 것 없음
        rippleFile.setLastModified(adderFile.lastModified() + 10_000);
        LibrarySync.OnOpen o = LibrarySync.check(open(rippleFile));
        assertTrue(o.newer.isEmpty() && o.moved.isEmpty(), o.newer + " " + o.moved);

        // 1bit_adder를 나중에 고쳤다
        adderFile.setLastModified(rippleFile.lastModified() + 10_000);
        assertEquals(Collections.singletonList("1bit_adder.circ"), LibrarySync.check(open(rippleFile)).newer);

        // 저장된 경로가 지금 계산한 경로와 다르다(다른 자리에서 절대 경로로 저장했거나 찾기 창으로 새 자리를 골랐다):
        // 저장하면 상대 경로로 남는다고 알린다
        Files.write(rippleFile.toPath(), xml.replace("file#1bit_adder.circ", "file#" + adderFile.getAbsolutePath())
                .getBytes(StandardCharsets.UTF_8));
        rippleFile.setLastModified(adderFile.lastModified() + 10_000);
        LibrarySync.OnOpen o2 = LibrarySync.check(open(rippleFile));
        assertEquals(Collections.singletonList("1bit_adder.circ"), o2.moved);
    }

    @Test
    void originFileNamesTheLibraryFile() throws Exception {
        File dir = tmp.toFile();
        File adderFile = adder(dir);
        Project ripple = ripple(dir);
        open.add(ripple);
        LoadedLibrary lib = OpenFileLibraries.ensureLoaded(ripple, adderFile).library;
        Circuit inLib = ((SubcircuitFactory) OpenFileLibraries.toolFor(lib, "1bit_adder").getFactory())
                .getSubcircuit();
        assertTrue(OpenFileLibraries.same(adderFile, LibrarySync.originFile(ripple, inLib)));
        assertNull(LibrarySync.originFile(ripple, ripple.getLogisimFile().getMainCircuit()), "own circuit");
    }
}
