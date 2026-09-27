/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine;

import static kr.ac.hallym.hcs.engine.Client.params;
import static kr.ac.hallym.hcs.engine.Client.xy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.WiringTool;
import com.cburch.logisim.tools.move.MoveGesture;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.wiring.SafeMove;
import kr.ac.hallym.hcs.regress.CircNormalizer;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * 편집 의도와 Swing 도구가 같은 .circ를 만든다(N-09 골든 전의 자체 확인). 같은 base 파일 두 벌에서, 한쪽은 엔진
 * 의도로, 다른 쪽은 원조 도구(WiringTool·AddTool·선택 지우기, 포크의 SafeMove 옮기기)를 화면 없이 마우스 사건으로
 * 움직여 편집하고, 저장한 글자(D-006 정규화)를 비교한다.
 */
class EditParityTest {
    @TempDir
    Path tmp;

    InProcess e;

    @BeforeAll
    static void addToolStaysSelected() {
        // AddTool은 놓은 뒤 Edit Tool로 바꾸려 창(Frame)을 찾는다. 창이 없는 테스트에서는 도구를 그대로 둔다(메모리 설정)
        AppPreferences.ADD_AFTER.set(AppPreferences.ADD_AFTER_UNCHANGED);
    }

    @BeforeEach
    void start() throws Exception {
        e = new InProcess();
    }

    @AfterEach
    void stop() {
        e.close();
    }

    /** 원조 도구를 화면 없이 움직이는 쪽. */
    static final class Swing {
        final LogisimFile file;
        final Project proj;
        final Canvas canvas;
        final Graphics g = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics();

        Swing(File f) throws Exception {
            file = new Loader(null).openLogisimFile(f);
            proj = new Project(file);
            canvas = new Canvas(proj);
            canvas.closeCanvas();
        }

        Circuit circuit() {
            return proj.getCurrentCircuit();
        }

        MouseEvent ev(int id, int[] p) {
            return new MouseEvent(canvas, id, System.currentTimeMillis(), 0, p[0], p[1], 1, false, MouseEvent.BUTTON1);
        }

        /** 선 도구로 끈다: 누름, 지나는 점마다 끌기, 뗌. */
        void drag(int[]... path) {
            WiringTool t = new WiringTool();
            t.select(canvas);
            t.mousePressed(canvas, g, ev(MouseEvent.MOUSE_PRESSED, path[0]));
            for (int i = 1; i < path.length; i++) {
                t.mouseDragged(canvas, g, ev(MouseEvent.MOUSE_DRAGGED, path[i]));
            }
            t.mouseReleased(canvas, g, ev(MouseEvent.MOUSE_RELEASED, path[path.length - 1]));
        }

        void add(String lib, String name, int x, int y) {
            AddTool t = (AddTool) file.getLibrary(lib).getTool(name);
            t.select(canvas);
            t.mousePressed(canvas, g, ev(MouseEvent.MOUSE_PRESSED, xy(x, y)));
            t.mouseReleased(canvas, g, ev(MouseEvent.MOUSE_RELEASED, xy(x, y)));
        }

        Component find(String name) {
            for (Component c : circuit().getNonWires()) {
                if (c.getFactory().getName().equals(name)) {
                    return c;
                }
            }
            throw new AssertionError("no " + name);
        }

        /** 고른 것을 Delete 키로 지운다(EditTool). */
        void delete(Component c) {
            Selection sel = canvas.getSelection();
            sel.add(c);
            proj.doAction(SelectionActions.clear(sel));
        }

        /** 포크의 끌어 옮기기(SelectTool → SafeMove, 연결 유지). */
        void move(Component c, int dx, int dy) {
            Selection sel = canvas.getSelection();
            sel.add(c);
            MoveGesture gst = new MoveGesture((gesture, x, y) -> { }, circuit(), sel.getAnchoredComponents());
            SafeMove.move(proj, sel, dx, dy, gst.forceRequest(dx, dy));
        }

        String save(File out) throws Exception {
            CircuitBuilder.save(file, out);
            return CircNormalizer.normalize(new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
        }
    }

    /** base를 만들고, 엔진 쪽과 Swing 쪽을 편집해 저장한 글자를 비교한다. */
    void same(Consumer<CircuitBuilder> base, Consumer<EngineSide> engineSide, Consumer<Swing> swingSide) throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        CircuitBuilder b = new CircuitBuilder(f, f.getMainCircuit());
        base.accept(b);
        b.commit();
        File baseFile = tmp.resolve("base.circ").toFile();
        CircuitBuilder.save(f, baseFile);
        String original = CircNormalizer.normalize(new String(Files.readAllBytes(baseFile.toPath()),
                StandardCharsets.UTF_8));

        JsonObject opened = e.client.callObject("file.open", params("path", baseFile.getPath()));
        EngineSide en = new EngineSide(e.client, opened.get("fileId").getAsString(), opened.get("main").getAsString());
        engineSide.accept(en);
        File a = tmp.resolve("engine.circ").toFile();
        e.client.call("file.save", params("fileId", en.fileId, "path", a.getPath()));
        String viaEngine = CircNormalizer.normalize(new String(Files.readAllBytes(a.toPath()), StandardCharsets.UTF_8));

        Swing sw = new Swing(baseFile);
        swingSide.accept(sw);
        String viaSwing = sw.save(tmp.resolve("swing.circ").toFile());
        sw.proj.getSimulator().shutDown();

        assertNotEquals(original, viaSwing, "the scenario changes the file");
        assertEquals(viaSwing, viaEngine);
    }

    /** 엔진 쪽 편집(의도). */
    static final class EngineSide {
        final Client client;
        final String fileId;
        final String main;

        EngineSide(Client client, String fileId, String main) {
            this.client = client;
            this.fileId = fileId;
            this.main = main;
        }

        JsonObject edit(String method, Object... kv) {
            List<Object> all = new ArrayList<>(List.of("fileId", fileId, "circuitId", main));
            all.addAll(List.of(kv));
            return client.callObject(method, params(all.toArray()));
        }

        String id(String name) {
            JsonObject s = client.callObject("model.circuit", params("fileId", fileId, "circuitId", main));
            return Fixtures.byName(s.getAsJsonArray("components"), name).get(0).get("id").getAsString();
        }
    }

    @Test
    void addGate() throws Exception {
        same(b -> { }, en -> en.edit("edit.addComponent", "lib", "Gates", "name", "AND Gate", "loc", xy(200, 200)),
                sw -> sw.add("Gates", "AND Gate", 200, 200));
    }

    @Test
    void addPinAndTunnel() throws Exception {
        same(b -> { }, en -> {
            en.edit("edit.addComponent", "lib", "Wiring", "name", "Pin", "loc", xy(100, 100));
            en.edit("edit.addComponent", "lib", "Wiring", "name", "Tunnel", "loc", xy(300, 100));
        }, sw -> {
            sw.add("Wiring", "Pin", 100, 100);
            sw.add("Wiring", "Tunnel", 300, 100);
        });
    }

    @Test
    void straightWire() throws Exception {
        same(b -> { }, en -> en.edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(250, 100)}),
                sw -> sw.drag(xy(100, 100), xy(180, 100), xy(250, 100)));
    }

    @Test
    void lWireHorizontalFirst() throws Exception {
        same(b -> { }, en -> en.edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(200, 100),
            xy(200, 200)}), sw -> sw.drag(xy(100, 100), xy(150, 100), xy(200, 100), xy(200, 150), xy(200, 200)));
    }

    @Test
    void lWireVerticalFirst() throws Exception {
        same(b -> { }, en -> en.edit("edit.addWire", "points", new Object[] {xy(100, 100), xy(100, 200),
            xy(200, 200)}), sw -> sw.drag(xy(100, 100), xy(100, 150), xy(100, 200), xy(150, 200), xy(200, 200)));
    }

    @Test
    void tSplitsAWire() throws Exception {
        same(b -> b.wire(Location.create(100, 100), Location.create(300, 100)),
                en -> en.edit("edit.addWire", "points", new Object[] {xy(200, 100), xy(200, 200)}),
                sw -> sw.drag(xy(200, 100), xy(200, 150), xy(200, 200)));
    }

    @Test
    void collinearWiresMerge() throws Exception {
        same(b -> b.wire(Location.create(100, 100), Location.create(200, 100)),
                en -> en.edit("edit.addWire", "points", new Object[] {xy(200, 100), xy(300, 100)}),
                sw -> sw.drag(xy(200, 100), xy(250, 100), xy(300, 100)));
    }

    @Test
    void draggingBackAlongAWireShortensIt() throws Exception {
        same(b -> b.wire(Location.create(100, 100), Location.create(300, 100)),
                en -> en.edit("edit.addWire", "points", new Object[] {xy(300, 100), xy(200, 100)}),
                sw -> sw.drag(xy(300, 100), xy(250, 100), xy(200, 100)));
    }

    @Test
    void aWireOneStepShortOfAGateInputIsRepaired() throws Exception {
        same(b -> b.add("Gates", "AND Gate", 300, 200),
                en -> en.edit("edit.addWire", "points", new Object[] {xy(100, 180), xy(260, 180)}),
                sw -> sw.drag(xy(100, 180), xy(200, 180), xy(260, 180)));
    }

    @Test
    void deleteAGate() throws Exception {
        same(b -> {
            Component and = b.add("Gates", "AND Gate", 300, 200);
            b.wire(Location.create(100, 200), CircuitBuilder.port(and, 3));
        }, en -> en.edit("edit.delete", "ids", new Object[] {en.id("AND Gate")}),
                sw -> sw.delete(sw.find("AND Gate")));
    }

    @Test
    void moveAGateWithFollowingWires() throws Exception {
        same(b -> {
            Component pin = b.add("Wiring", "Pin", 100, 200, "tristate", "false");
            Component not = b.add("Gates", "NOT Gate", 300, 200);
            b.wire(CircuitBuilder.port(pin, 0), CircuitBuilder.port(not, 1));
        }, en -> en.edit("edit.move", "ids", new Object[] {en.id("NOT Gate")}, "dx", 0, "dy", 50),
                sw -> sw.move(sw.find("NOT Gate"), 0, 50));
    }
}
