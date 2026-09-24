/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.splitter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.ext.CircExtensionIO;
import kr.ac.hallym.hcs.app.ext.CircExtensions;
import kr.ac.hallym.hcs.app.menu.SelectionOrder;
import kr.ac.hallym.hcs.app.model.Netlist;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** #105: 스플리터 편집의 회로 변경, 팔 이름 저장, 비트 그림 경계, 고른 순서. */
class SplitterEditsTest {
    @TempDir
    Path tmp;

    static Component splitterAt(Circuit c, Location at) {
        for (Component comp : c.getNonWires()) {
            if (comp.getFactory().getName().equals("Splitter") && comp.getLocation().equals(at)) {
                return comp;
            }
        }
        return null;
    }

    @Test
    void changeReplacesAttributesOnly() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component sp = b.add("Wiring", "Splitter", 300, 300, "facing", "south");
        b.commit();
        Circuit main = file.getMainCircuit();
        SplitterSpec r = SplitterSpec.Preset.MIPS_R.spec(true);
        SplitterEdits.change(main, sp, r).execute();
        Component now = splitterAt(main, Location.create(300, 300));
        assertEquals(r.toStandardAttrs(), SplitterSpec.fromStandardAttrs(SplitterEdits.standardAttrs(now),
                null).toStandardAttrs());
        assertEquals("south", SplitterEdits.standardAttrs(now).get("facing"), "facing kept");
        assertEquals(7, now.getEnds().size());
    }

    @Test
    void createOnAWireConnectsTheCombinedEnd() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component src = b.add("Wiring", "Pin", 100, 200, "width", "8");
        b.wire(Location.create(100, 200), Location.create(400, 200));
        b.commit();
        Circuit main = file.getMainCircuit();
        Location at = Location.create(250, 200);
        SplitterEdits.create(file, main, at, Direction.EAST, SplitterSpec.parse("7:4, 3:0", 8, true)).execute();
        Component sp = splitterAt(main, at);
        Netlist nl = Netlist.of(main);
        assertSame(nl.netOf(src, 0), nl.netOf(sp, 0), "a splitter placed on the wire joins its net");
        assertFalse(nl.netOf(sp, 1) == nl.netOf(src, 0), "arms stay off the wire");
        assertEquals(8, nl.bitLinks().size(), "every bit of the wire goes to an arm");
    }

    /** 팔 이름은 확장 정보에만. 이름이 없으면 아무것도 남기지 않아 저장 결과가 원조와 같다. */
    @Test
    void armNamesLiveInTheExtensionOnly() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        b.add("Wiring", "Splitter", 300, 300);
        b.commit();
        Circuit main = file.getMainCircuit();
        Location at = Location.create(300, 300);
        SplitterSpec named = SplitterSpec.Preset.MIPS_J.spec(true);
        assertTrue(SplitterEdits.setNames(file, main, at, named));
        assertEquals(Arrays.asList("op", "addr"), SplitterEdits.names(file, main, at));
        assertFalse(SplitterEdits.setNames(file, main, at, named), "same names: no change");

        File saved = tmp.resolve("named.circ").toFile();
        CircuitBuilder.save(file, saved);
        CircExtensions.afterSave(file, saved);
        String text = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("<hcs:splitter x=\"300\" y=\"300\" arm0=\"op\" arm1=\"addr\"/>"), text);
        assertEquals(Arrays.asList("op", "addr"), readBack(saved));

        assertTrue(SplitterEdits.setNames(file, main, at, named.withNames(Collections.<String>emptyList())));
        assertTrue(CircExtensions.of(file).isEmpty(), "no names, nothing to save");
    }

    static List<String> readBack(File f) throws Exception {
        LogisimFile file = new Loader(null).openLogisimFile(f);
        CircExtensions.afterOpen(file, f);
        return SplitterEdits.names(file, file.getMainCircuit(), Location.create(300, 300));
    }

    /** 팔 이름 바꾸기는 회로 변경과 같은 되돌리기 한 단계다. */
    @Test
    void namesAreUndoneWithTheEdit() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        b.add("Wiring", "Splitter", 300, 300);
        b.commit();
        Circuit main = file.getMainCircuit();
        Location at = Location.create(300, 300);
        SplitterSpec named = SplitterSpec.parse("1 hi, 0 lo", 2, true);
        com.cburch.logisim.proj.Action act = SplitterEdits.withNames(null, "names", file, main, at, named);
        act.doIt(null);
        assertEquals(Arrays.asList("hi", "lo"), SplitterEdits.names(file, main, at));
        act.undo(null);
        assertTrue(SplitterEdits.names(file, main, at).isEmpty());
        assertTrue(CircExtensions.of(file).isEmpty(), "undo leaves nothing to save");
    }

    /** 지운 스플리터의 이름은 저장할 때 빠진다. 팔 수가 다른 스플리터는 이름을 물려받지 않는다. */
    @Test
    void staleNamesAreDroppedOnSave() throws Exception {
        kr.ac.hallym.hcs.app.ext.CircExtensions.addPruner(SplitterEdits.PRUNER);
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(file, file.getMainCircuit());
        Component sp = b.add("Wiring", "Splitter", 300, 300);
        b.commit();
        Circuit main = file.getMainCircuit();
        Location at = Location.create(300, 300);
        SplitterEdits.setNames(file, main, at, SplitterSpec.parse("1 hi, 0 lo", 2, true));
        com.cburch.logisim.circuit.CircuitMutation del = new com.cburch.logisim.circuit.CircuitMutation(main);
        del.remove(sp);
        del.execute();
        File saved = tmp.resolve("deleted.circ").toFile();
        CircuitBuilder.save(file, saved);
        CircExtensions.afterSave(file, saved);
        assertFalse(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8).contains("hcs:"));

        CircuitBuilder b2 = new CircuitBuilder(file, main);
        b2.add("Wiring", "Splitter", 300, 300, "fanout", "3", "incoming", "3");
        b2.commit();
        SplitterEdits.setNames(file, main, at, SplitterSpec.parse("1 hi, 0 lo", 2, true));
        CircuitBuilder.save(file, saved);
        CircExtensions.afterSave(file, saved);
        assertFalse(new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8).contains("hcs:"),
                "two names do not belong to a three-arm splitter");
    }

    /** 이름 없이 편집한 스플리터는 원조 API로 같은 속성을 준 스플리터와 똑같이 저장된다. */
    @Test
    void editedSplitterWithoutNamesSavesLikeTheOriginal() throws Exception {
        SplitterSpec spec = SplitterSpec.Preset.MIPS_I.spec(true);
        LogisimFile edited = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(edited, edited.getMainCircuit());
        Component sp = b.add("Wiring", "Splitter", 300, 300);
        b.commit();
        SplitterEdits.change(edited.getMainCircuit(), sp, spec).execute();
        File a = tmp.resolve("edited.circ").toFile();
        CircuitBuilder.save(edited, a);
        CircExtensions.afterSave(edited, a);

        LogisimFile plain = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder p = new CircuitBuilder(plain, plain.getMainCircuit());
        List<String> attrs = new ArrayList<>();
        for (java.util.Map.Entry<String, String> e : spec.toStandardAttrs().entrySet()) {
            attrs.add(e.getKey());
            attrs.add(e.getValue());
        }
        p.add("Wiring", "Splitter", 300, 300, attrs.toArray(new String[0]));
        p.commit();
        File c = tmp.resolve("plain.circ").toFile();
        CircuitBuilder.save(plain, c);
        String sa = new String(Files.readAllBytes(a.toPath()), StandardCharsets.UTF_8);
        String sc = new String(Files.readAllBytes(c.toPath()), StandardCharsets.UTF_8);
        assertFalse(sa.contains("hcs:"));
        assertEquals(kr.ac.hallym.hcs.regress.CircNormalizer.normalize(sc),
                kr.ac.hallym.hcs.regress.CircNormalizer.normalize(sa));
    }

    @Test
    void clickingABoundarySplitsOrJoins() throws Exception {
        SplitterSpec s = SplitterSpec.parse("7:0", 8, true);
        SplitterSpec split = SplitterEditor.toggle(s, 4).ordered(true);
        assertEquals("7:4, 3:0", split.toText());
        SplitterSpec joined = SplitterEditor.toggle(split, 4).ordered(true);
        assertEquals("7:0", joined.toText());
        assertEquals("7:0", SplitterEditor.toggle(s, 0).toText(), "no boundary right of bit 0");
    }

    @Test
    void selectionOrderFollowsClicks() {
        SelectionOrder<String> o = new SelectionOrder<>();
        String pc = new String("PC[31:28]");
        String addr = new String("addr");
        String zero = new String("00");
        o.update(Collections.singletonList(pc));
        o.update(Arrays.asList(addr, pc));
        o.update(Arrays.asList(zero, pc, addr));
        assertEquals(Arrays.asList(pc, addr, zero), o.order());
        o.update(Arrays.asList(zero, pc));
        assertEquals(Arrays.asList(pc, zero), o.order(), "deselected ones drop out, order kept");
        assertTrue(o.known());
        List<String> none = new ArrayList<>();
        o.update(none);
        assertTrue(o.order().isEmpty());
        o.update(Arrays.asList(pc, addr, zero)); // addAll처럼 한 번에
        assertFalse(o.known(), "several at once: the order is not known");
        o.update(none);
        // 원조 사각형 선택: 같은 마우스 입력 안에서 부품마다 이벤트가 따로 온다
        Object drag = new Object();
        o.update(Collections.singletonList(pc), drag);
        o.update(Arrays.asList(pc, addr), drag);
        o.update(Arrays.asList(pc, addr, zero), drag);
        assertFalse(o.known(), "one drag selecting three wires: the order is not known");
        o.update(none, new Object());
        // Shift+클릭: 클릭마다 다른 입력
        o.update(Collections.singletonList(zero), new Object());
        o.update(Arrays.asList(zero, pc), new Object());
        o.update(Arrays.asList(zero, pc, addr), new Object());
        assertTrue(o.known());
        assertEquals(Arrays.asList(zero, pc, addr), o.order());
        assertTrue(CircExtensionIO.NS.startsWith("urn:"));
    }
}
