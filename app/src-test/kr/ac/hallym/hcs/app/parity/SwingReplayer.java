/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.parity;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListModel;
import javax.swing.SwingUtilities;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitTransactionResult;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;
import com.cburch.logisim.gui.main.AttrTableCircuitModel;
import com.cburch.logisim.gui.main.AttrTableToolModel;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.EditHandler;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.gui.menu.ProjectLibraryActions;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.appear.AutoAppearance;
import kr.ac.hallym.hcs.app.appear.PortOrderDialog;
import kr.ac.hallym.hcs.app.edit.Arrange;
import kr.ac.hallym.hcs.app.edit.ArrangeActions;
import kr.ac.hallym.hcs.app.edit.RedoStack;
import kr.ac.hallym.hcs.app.groups.SignalGroups;
import kr.ac.hallym.hcs.app.labels.TunnelColorStore;
import kr.ac.hallym.hcs.app.labels.TunnelColors;
import kr.ac.hallym.hcs.app.libs.ImportDialog;
import kr.ac.hallym.hcs.app.libs.MipsShadow;
import kr.ac.hallym.hcs.app.memo.AreaMemos;
import kr.ac.hallym.hcs.app.memo.MemoMenu;
import kr.ac.hallym.hcs.app.splitter.ParitySplitterBridge;
import kr.ac.hallym.hcs.app.splitter.SplitterEditor;

/**
 * 의도를 지금의 Swing 앱으로 실행하고 앱의 저장 코드로 저장한다(N-01, D-136). 원조 도구(AddTool, Wiring Tool, Edit
 * Tool의 선택·끌기)는 실제 창의 캔버스에 논리 좌표를 배율·원점으로 바꾼 합성 마우스 사건을 보내 움직인다. 편집 메뉴는
 * 창의 LayoutEditHandler, 속성은 속성 표 모델의 행, 되돌리기는 원조 {@link Project#undoAction}과 v1의 다시 실행,
 * v1 기능은 메뉴 항목이 부르는 진입점을 그대로 부르고 뜨는 대화상자는 {@link Dialogs}가 사람처럼 채운다. 도구가 하는
 * 일을 여기서 다시 짜지 않는다.
 */
final class SwingReplayer implements AutoCloseable {
    /** 원조 File › New가 쓰는 기본 템플릿(AppPreferences의 plain 템플릿과 같은 자원). */
    static final String TEMPLATE = "resources/logisim/default.templ";

    private final IntentScript script;
    private final Path parityDir;
    private final Path work;
    private final File circ;
    private Project proj;
    private Frame frame;
    private Canvas canvas;
    private final Map<String, Component> symbols = new LinkedHashMap<>();
    private final Map<Component, Component> replaced = new IdentityHashMap<>();
    private final Set<Circuit> watched = Collections.newSetFromMap(new IdentityHashMap<>());
    private final CircuitListener tracker = this::track;
    private final List<String> trace = new ArrayList<>();
    /** 이번 의도에서 새로 놓은 부품과 포트 자리(기록용, 장면을 쓸 때 좌표를 고르는 데 쓴다). */
    private String note = "";
    private final List<String> dialogLog = new ArrayList<>();
    private String savedBundled;
    private String savedAddAfter;
    private boolean savedKeepConnect;

    SwingReplayer(IntentScript script, Path parityDir, Path work) {
        this.script = script;
        this.parityDir = parityDir;
        this.work = work;
        this.circ = work.resolve(script.name + ".circ").toFile();
    }

    /** 실행 기록: 의도마다 새로 생긴 되돌리기 단계 이름. */
    List<String> trace() {
        return trace;
    }

    /** 의도를 모두 실행하고 저장한 바이트를 돌려준다. */
    byte[] run() throws Exception {
        prepare();
        open();
        for (Intent i : script.intents) {
            if (i.method.equals("file.open")) {
                continue;
            }
            List<Action> before = edtGet(() -> new ArrayList<>(proj.getUndoActions()));
            try {
                apply(i);
            } catch (Exception | AssertionError e) {
                throw new AssertionError(i + " failed: " + e.getMessage() + "\ntrace:\n" + String.join("\n", trace),
                        e);
            }
            flush();
            List<Action> after = edtGet(() -> new ArrayList<>(proj.getUndoActions()));
            String msg = edtGet(() -> canvas.getErrorMessage() == null ? "" : " canvas says '"
                    + canvas.getErrorMessage().get() + "'");
            trace.add(i.line + " " + i.method + " -> " + undoDelta(before, after) + note + msg
                    + (dialogLog.isEmpty() ? "" : " dialogs " + dialogLog));
            note = "";
            dialogLog.clear();
            edt(this::followSymbols);
        }
        edt(() -> {
            if (!ProjectActions.doSave(proj)) {
                throw new IOException("the app did not save " + circ);
            }
        });
        flush();
        return Files.readAllBytes(circ.toPath());
    }

    private static String undoDelta(List<Action> before, List<Action> after) {
        int common = 0;
        while (common < before.size() && common < after.size() && before.get(common) == after.get(common)) {
            common++;
        }
        List<String> names = new ArrayList<>();
        for (int k = common; k < after.size(); k++) {
            names.add(after.get(k).getName());
        }
        return "undo " + before.size() + "->" + after.size() + " " + names;
    }

    // ------------------------------------------------------------------ 준비

    private void prepare() throws IOException {
        Path inputs = parityDir.resolve("inputs");
        if (Files.isDirectory(inputs)) {
            try (Stream<Path> s = Files.walk(inputs)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    Path to = work.resolve(parityDir.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(to);
                    } else {
                        Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        String jar = System.getProperty("hcs.mipsJar");
        if (jar != null && new File(jar).canRead()) {
            // 원조 2.7.1이 여는 모양: .circ 옆의 hcs-mips.jar(D-096). 번들(그림자) 라이브러리도 이 jar다.
            Files.copy(new File(jar).toPath(), work.resolve("hcs-mips.jar"), StandardCopyOption.REPLACE_EXISTING);
        }
        Intent first = script.intents.isEmpty() ? null : script.intents.get(0);
        if (first != null && first.method.equals("file.open")) {
            Files.copy(parityDir.resolve(first.str("path")), circ.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in = Frame.class.getClassLoader().getResourceAsStream(TEMPLATE)) {
                if (in == null) {
                    throw new IOException("no " + TEMPLATE);
                }
                Files.copy(in, circ.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void open() throws Exception {
        savedBundled = System.getProperty("hcs.bundledMips");
        String jar = System.getProperty("hcs.mipsJar");
        if (jar != null) {
            System.setProperty("hcs.bundledMips", jar);
        }
        edt(() -> {
            // 새로 띄운 앱처럼: 원조 Wiring 라이브러리의 도구 7개(Splitter, Pin, Probe, Tunnel, Pull Resistor, Clock,
            // Constant)는 static이라 한 JVM의 모든 파일이 도구 속성을 함께 쓴다. 앞 장면·앞 GUI 테스트가 바꾼 도구
            // 속성이 이 장면으로 새지 않게 처음 값으로 되돌린 뒤 파일을 연다(파일의 <lib><tool> 값은 열 때 다시 얹힌다).
            resetTools(new com.cburch.logisim.std.wiring.Wiring());
            MipsShadow.reset();
            LoadedLibrary shadow = MipsShadow.shadow(new com.cburch.logisim.file.Loader(null));
            if (shadow != null) {
                resetTools(shadow);
            }
            savedAddAfter = AppPreferences.ADD_AFTER.get();
            savedKeepConnect = AppPreferences.MOVE_KEEP_CONNECT.getBoolean();
            AppPreferences.ADD_AFTER.set(AppPreferences.ADD_AFTER_EDIT);
            AppPreferences.MOVE_KEEP_CONNECT.setBoolean(true);
            // File › Open과 같은 길(확장 정보 읽기 포함)
            proj = ProjectActions.doOpen(null, null, circ);
            if (proj == null) {
                throw new IOException("the app could not open " + circ);
            }
            frame = proj.getFrame();
            canvas = frame.getCanvas();
        });
        flush();
        edt(() -> canvas.getHcsZoom().zoomTo(1.0));
        flush();
    }

    @Override
    public void close() throws Exception {
        edt(() -> {
            if (frame != null) {
                frame.dispose();
            }
            resetTools(new com.cburch.logisim.std.wiring.Wiring()); // 뒤 테스트에 도구 속성을 남기지 않는다
            if (savedAddAfter != null) {
                AppPreferences.ADD_AFTER.set(savedAddAfter);
                AppPreferences.MOVE_KEEP_CONNECT.setBoolean(savedKeepConnect);
            }
            for (Circuit c : watched) {
                c.removeCircuitListener(tracker);
            }
        });
        if (savedBundled == null) {
            System.clearProperty("hcs.bundledMips");
        } else {
            System.setProperty("hcs.bundledMips", savedBundled);
        }
    }

    // ------------------------------------------------------------------ 의도

    private void apply(Intent i) throws Exception {
        edt(this::watchCircuits);
        edt(() -> canvas.setErrorMessage(null)); // 앞 의도의 캔버스 알림을 이 의도의 것으로 읽지 않게
        if (i.has("circuit")) {
            String name = i.str("circuit");
            edt(() -> {
                Circuit c = circuit(name, i);
                if (proj.getCurrentCircuit() != c) {
                    proj.setCurrentCircuit(c); // 부품 목록에서 회로를 두 번 누른 것과 같다
                }
            });
            flush();
        }
        switch (i.method) {
        case "view.zoom":
            edt(() -> canvas.getHcsZoom().zoomTo(i.number("factor")));
            break;
        case "edit.addComponent":
            addComponent(i);
            break;
        case "edit.setToolAttr":
            edt(() -> setToolAttr(addTool(i), i.str("attr"), i.text("value"), i));
            break;
        case "edit.addWire":
            addWire(i);
            break;
        case "edit.select":
            select(i);
            break;
        case "edit.move":
            move(i);
            break;
        case "edit.delete":
            selectIfGiven(i);
            edt(() -> editHandler().delete());
            break;
        case "edit.copy":
            selectIfGiven(i);
            edt(() -> editHandler().copy());
            break;
        case "edit.cut":
            selectIfGiven(i);
            edt(() -> editHandler().cut());
            break;
        case "edit.paste":
            edt(() -> editHandler().paste());
            break;
        case "edit.duplicate":
            selectIfGiven(i);
            edt(() -> editHandler().duplicate());
            break;
        case "edit.setAttr":
            setAttr(i);
            break;
        case "edit.setCircuitAttr":
            edt(() -> setRow(new AttrTableCircuitModel(proj, circuit(i.str("target"), i)), i.str("attr"),
                    i.text("value"), i));
            break;
        case "edit.undo":
            edt(() -> proj.undoAction()); // Edit › Undo
            break;
        case "edit.redo":
            edt(() -> RedoStack.of(proj).redo()); // Edit › Redo(v1)
            break;
        case "edit.createCircuit":
            createCircuit(i);
            break;
        case "edit.setMainCircuit":
            edt(() -> ProjectCircuitActions.doSetAsMainCircuit(proj, circuit(i.str("target"), i)));
            break;
        case "edit.duplicateN":
            duplicateN(i);
            break;
        case "edit.align":
            arrange(i, true);
            break;
        case "edit.distribute":
            arrange(i, false);
            break;
        case "edit.portOrder":
            portOrder(i);
            break;
        case "edit.autoAppearance":
            autoAppearance(i);
            break;
        case "edit.importCircuits":
            importCircuits(i);
            break;
        case "edit.loadLibrary":
            loadLibrary(i);
            break;
        case "edit.unloadLibrary":
            unloadLibrary(i);
            break;
        case "edit.tunnelColor":
            tunnelColor(i);
            break;
        case "edit.signalGroup":
            signalGroup(i);
            break;
        case "edit.areaMemo":
            areaMemo(i);
            break;
        case "edit.splitterEdit":
            splitter(i, false);
            break;
        case "edit.splitterSplit":
            splitter(i, true);
            break;
        default:
            throw i.error("not supported by the Swing replayer");
        }
    }

    /** 부품 목록에서 도구를 고르고(필요하면 속성 표에서 도구 속성을 바꾸고) 캔버스를 누른다. */
    private void addComponent(Intent i) throws Exception {
        AtomicReference<AddTool> tool = new AtomicReference<>();
        edt(() -> {
            tool.set(addTool(i));
            use(tool.get());
            for (Map.Entry<String, Object> e : i.map("attrs").entrySet()) {
                if (e.getValue() == null) {
                    throw i.error("attribute " + e.getKey() + " has no value");
                }
                setToolAttr(tool.get(), e.getKey(), text(e.getValue()), i);
            }
        });
        flush();
        Set<Component> before = edtGet(() -> identity(canvas.getCircuit().getNonWires()));
        Location at = i.loc("loc");
        edt(() -> mouse(MouseEvent.MOUSE_MOVED, at, 0, MouseEvent.NOBUTTON, 0));
        edt(() -> mouse(MouseEvent.MOUSE_PRESSED, at, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1, 1));
        edt(() -> mouse(MouseEvent.MOUSE_RELEASED, at, 0, MouseEvent.BUTTON1, 1));
        flush();
        edt(() -> {
            List<Component> added = new ArrayList<>();
            for (Component c : canvas.getCircuit().getNonWires()) {
                if (!before.contains(c)) {
                    added.add(c);
                }
            }
            if (added.size() != 1) {
                throw i.error("expected one new component, got " + added + " (canvas message: "
                        + (canvas.getErrorMessage() == null ? "none" : canvas.getErrorMessage().get()) + ", tool: "
                        + proj.getTool() + ")");
            }
            Component c = added.get(0);
            StringBuilder b = new StringBuilder(" placed ").append(c.getFactory().getName()).append(" at ")
                    .append(c.getLocation()).append(" ports");
            for (int k = 0; k < c.getEnds().size(); k++) {
                b.append(' ').append(c.getEnd(k).getLocation());
            }
            note = b.toString();
            if (i.has("as")) {
                String name = i.str("as");
                if (symbols.containsKey(name)) {
                    throw i.error("symbol " + name + " is already defined");
                }
                symbols.put(name, added.get(0));
            }
        });
    }

    /** Wiring Tool로 첫 점에서 눌러 (꺾이는 점을 지나) 끝점에서 놓는다. */
    private void addWire(Intent i) throws Exception {
        List<Location> pts = i.points("points");
        if (pts.size() == 2) {
            Location a = pts.get(0);
            Location b = pts.get(1);
            if (a.getX() != b.getX() && a.getY() != b.getY()) {
                throw i.error("a two-point wire must be straight; give the corner as the middle point");
            }
        } else if (pts.size() == 3) {
            Location a = pts.get(0);
            Location m = pts.get(1);
            Location b = pts.get(2);
            boolean hFirst = m.getX() == b.getX() && m.getY() == a.getY() && m.getX() != a.getX();
            boolean vFirst = m.getX() == a.getX() && m.getY() == b.getY() && m.getY() != a.getY();
            if (!hFirst && !vFirst) {
                throw i.error("the middle point must be the corner of an L");
            }
        } else {
            throw i.error("points must have 2 or 3 points");
        }
        edt(() -> use(baseTool("Wiring Tool")));
        drag(pts, false, false);
    }

    private void select(Intent i) throws Exception {
        edt(() -> use(baseTool("Edit Tool")));
        boolean add = i.bool("add", false);
        if (i.params.containsKey("ids")) {
            selectRefs(i, i.strings("ids"), add);
        } else if (!add && !i.has("rect")) {
            selectRefs(i, Collections.emptyList(), false);
        }
        if (i.has("rect")) {
            int[] r = i.ints("rect", 4);
            Location a = Location.create(r[0], r[1]);
            Location b = Location.create(r[2], r[3]);
            edt(() -> {
                if (!canvas.getCircuit().getAllContaining(a, canvas.getGraphics()).isEmpty() || wiringPoint(a)) {
                    throw i.error("a rectangle selection must start on empty background: " + a);
                }
            });
            drag(List.of(a, b), add, add);
        }
        if (i.has("filter")) {
            String f = i.str("filter");
            if (!f.equals("components") && !f.equals("wires")) {
                throw i.error("filter is components or wires");
            }
            // 여러 개를 고른 우클릭 메뉴의 Only Components / Only Wires
            edt(() -> ArrangeActions.filter(proj, new ArrayList<>(proj.getSelection().getComponents()),
                    f.equals("wires")));
        }
    }

    private void selectIfGiven(Intent i) throws Exception {
        if (i.params.containsKey("ids")) {
            edt(() -> use(baseTool("Edit Tool")));
            selectRefs(i, i.strings("ids"), false);
        }
    }

    /**
     * 고르기: 다른 것을 누르면 원조 선택 도구가 하는 것처럼 떠 있는 것을 내려놓고(dropAll) 새로 고른다. 하나씩 따로
     * GUI 사건으로 더해 고른 순서가 남는다(우클릭 메뉴의 순서 기록과 같게).
     */
    private List<Component> selectRefs(Intent i, List<String> refs, boolean add) throws Exception {
        List<Component> comps = edtGet(() -> resolveAll(i, refs));
        if (!add) {
            edt(() -> {
                Action drop = SelectionActions.dropAll(proj.getSelection());
                if (drop != null) {
                    proj.doAction(drop);
                }
            });
        }
        for (Component c : comps) {
            edt(() -> proj.getSelection().add(c));
        }
        flush();
        return comps;
    }

    /** Edit Tool로 고른 것 안을 눌러 (dx, dy)만큼 끌어 놓는다. keepConnected=false면 끄는 동안 Shift. */
    private void move(Intent i) throws Exception {
        edt(() -> use(baseTool("Edit Tool")));
        if (i.params.containsKey("ids")) {
            selectRefs(i, i.strings("ids"), false);
        }
        int dx = i.integer("dx");
        int dy = i.integer("dy");
        boolean keep = i.bool("keepConnected", true);
        Location p = edtGet(() -> pressPoint(i));
        drag(List.of(p, p.translate(dx, dy)), false, !keep);
    }

    private void setAttr(Intent i) throws Exception {
        edt(() -> use(baseTool("Edit Tool")));
        if (i.params.containsKey("ids")) {
            selectRefs(i, i.strings("ids"), false);
        }
        edt(() -> {
            if (proj.getSelection().isEmpty()) {
                throw i.error("nothing selected; use edit.setCircuitAttr for circuit attributes");
            }
            AttributeSetTableModel model = (AttributeSetTableModel) field(frame, "attrTableSelectionModel");
            model.attributeListChanged(null);
            setRow(model, i.str("attr"), i.text("value"), i);
        });
    }

    private void createCircuit(Intent i) throws Exception {
        String name = i.str("name");
        dialogs().expect("circuit name", d -> {
            Dialogs.first(d, JTextField.class).setText(name);
            Dialogs.click(d, Dialogs.ok());
        }).run(() -> ProjectCircuitActions.doAddCircuit(proj)); // Project › Add Circuit…
    }

    /** 우클릭 Duplicate N…: 개수·방향·간격·라벨 번호 창을 채우고 OK. */
    private void duplicateN(Intent i) throws Exception {
        edt(() -> use(baseTool("Edit Tool")));
        List<Component> comps = i.params.containsKey("ids") ? selectRefs(i, i.strings("ids"), false)
                : edtGet(() -> nonWires(proj.getSelection().getComponents()));
        List<Component> parts = nonWires(comps);
        if (parts.isEmpty()) {
            throw i.error("nothing to duplicate");
        }
        Arrange.Dir dir = Arrange.Dir.valueOf(i.str("direction").toUpperCase());
        int count = i.integer("count");
        dialogs().expect("Duplicate N", d -> {
            List<JSpinner> spinners = Dialogs.all(d, JSpinner.class);
            @SuppressWarnings("unchecked")
            JComboBox<String> box = Dialogs.first(d, JComboBox.class);
            box.setSelectedIndex(dir.ordinal()); // 간격 기본값이 방향을 따라 바뀐다
            spinners.get(0).setValue(count);
            if (i.has("spacing")) {
                spinners.get(1).setValue(i.integer("spacing"));
            }
            if (i.has("number")) {
                JCheckBox number = Dialogs.first(d, JCheckBox.class);
                if (!number.isEnabled() && i.bool("number", false)) {
                    throw i.error("label numbering is off: no labeled part");
                }
                number.setSelected(i.bool("number", false));
            }
            Dialogs.click(d, Dialogs.ok());
        }).run(() -> ArrangeActions.duplicateN(proj, canvas.getCircuit(), parts));
    }

    /** 여러 개를 고른 우클릭 메뉴의 Align ›, Distribute ›. */
    private void arrange(Intent i, boolean align) throws Exception {
        edt(() -> use(baseTool("Edit Tool")));
        List<Component> parts = nonWires(selectRefs(i, i.strings("ids"), false));
        if (align) {
            String mode = i.str("mode");
            Arrange.Align a;
            switch (mode) {
            case "left":
                a = Arrange.Align.LEFT;
                break;
            case "centerX":
                a = Arrange.Align.CENTER_X;
                break;
            case "right":
                a = Arrange.Align.RIGHT;
                break;
            case "top":
                a = Arrange.Align.TOP;
                break;
            case "centerY":
                a = Arrange.Align.CENTER_Y;
                break;
            case "bottom":
                a = Arrange.Align.BOTTOM;
                break;
            default:
                throw i.error("mode is left, centerX, right, top, centerY or bottom");
            }
            if (parts.size() < 2) {
                throw i.error("the menu shows Align only for two or more parts");
            }
            edt(() -> ArrangeActions.align(proj, canvas.getCircuit(), parts, a));
        } else {
            String axis = i.str("axis");
            if (!axis.equals("h") && !axis.equals("v")) {
                throw i.error("axis is h or v");
            }
            if (parts.size() < 3) {
                throw i.error("the menu shows Distribute only for three or more parts");
            }
            edt(() -> ArrangeActions.distribute(proj, canvas.getCircuit(), parts, axis.equals("h")));
        }
    }

    /** 서브회로 인스턴스 우클릭 Port Order…: 변마다 ▲로 순서를 맞추고 Apply(끊어질 연결 확인은 confirm). */
    private void portOrder(Intent i) throws Exception {
        Circuit target = edtGet(() -> circuit(i.str("target"), i));
        Map<String, Object> order = i.map("order");
        boolean confirm = i.bool("confirm", true);
        String title = Messages.get("portOrder.title", target.getName());
        dialogs().expect("Port Order", d -> {
            reorderPorts(d, order, i);
            Dialogs.click(d, Messages.get("portOrder.apply"));
        }).maybe(title, d -> Dialogs.click(d, Messages.get(confirm ? "autoAppearance.apply"
                : "autoAppearance.cancel"))).run(() -> PortOrderDialog.show(proj, target, canvas));
    }

    private static void reorderPorts(java.awt.Dialog d, Map<String, Object> order, Intent i) {
        Set<String> seen = new HashSet<>();
        for (JList<?> list : Dialogs.all(d, JList.class)) {
            java.awt.Container col = list.getParent().getParent().getParent();
            String title = ((JLabel) col.getComponent(0)).getText();
            String side = null;
            for (String s : new String[] {"north", "south", "east", "west"}) {
                if (Messages.get("portOrder.side." + s).equals(title)) {
                    side = s;
                }
            }
            if (side == null) {
                throw i.error("unknown side column " + title);
            }
            seen.add(side);
            if (!order.containsKey(side)) {
                continue;
            }
            List<String> want = new ArrayList<>();
            for (Object o : (List<?>) order.get(side)) {
                want.add((String) o);
            }
            JButton up = null;
            for (JButton b : Dialogs.all(col, JButton.class)) {
                if ("▲".equals(b.getText())) {
                    up = b;
                }
            }
            @SuppressWarnings("unchecked")
            JList<Instance> ports = (JList<Instance>) list;
            List<String> now = names(ports.getModel());
            if (!new HashSet<>(now).equals(new HashSet<>(want)) || now.size() != want.size()) {
                throw i.error(side + " side has ports " + now + ", not " + want);
            }
            for (int k = 0; k < want.size(); k++) {
                int j = names(ports.getModel()).indexOf(want.get(k));
                while (j > k) {
                    ports.setSelectedIndex(j);
                    up.doClick(0);
                    j--;
                }
            }
            if (!names(ports.getModel()).equals(want)) {
                throw i.error("could not order the " + side + " side");
            }
        }
        for (String s : order.keySet()) {
            if (!seen.contains(s)) {
                throw i.error("the dialog has no " + s + " side");
            }
        }
    }

    private static List<String> names(ListModel<Instance> m) {
        List<String> out = new ArrayList<>();
        for (int k = 0; k < m.getSize(); k++) {
            out.add(AutoAppearance.portName(m.getElementAt(k)));
        }
        return out;
    }

    /** 서브회로 인스턴스 우클릭 Auto Appearance(끊어질 연결이 있으면 확인 창). */
    private void autoAppearance(Intent i) throws Exception {
        Circuit target = edtGet(() -> circuit(i.str("target"), i));
        boolean confirm = i.bool("confirm", true);
        dialogs().maybe(Messages.get("autoAppearance.title"), d -> Dialogs.click(d,
                Messages.get(confirm ? "autoAppearance.apply" : "autoAppearance.cancel")))
                .run(() -> AutoAppearance.run(proj, target, canvas));
    }

    /** File › Import Subcircuits…: 파일을 고르고, 가져올 회로에 표시하고, 계획을 Apply. */
    private void importCircuits(Intent i) throws Exception {
        File f = work.resolve(i.str("path")).toFile();
        List<String> want = i.strings("circuits");
        dialogs().expect("file chooser", d -> choose(d, f)).expect("choose circuits", d -> {
            Set<String> found = new HashSet<>();
            for (JCheckBox b : Dialogs.all(d, JCheckBox.class)) {
                b.setSelected(want.contains(b.getText()));
                found.add(b.getText());
            }
            if (!found.containsAll(want)) {
                throw i.error(f.getName() + " has circuits " + found + ", not all of " + want);
            }
            Dialogs.click(d, Dialogs.ok());
        }).expect("import plan", d -> Dialogs.click(d, Messages.get("import.apply")))
                .run(() -> ImportDialog.show(proj));
    }

    /** Project › Load Library › Built-in / Logisim / JAR. */
    private void loadLibrary(Intent i) throws Exception {
        String kind = i.str("kind");
        switch (kind) {
        case "builtin": {
            String name = i.str("name");
            String display = edtGet(() -> {
                Library lib = proj.getLogisimFile().getLoader().getBuiltin().getLibrary(name);
                if (lib == null) {
                    throw i.error("no built-in library " + name);
                }
                return lib.getDisplayName();
            });
            dialogs().expect("built-in libraries", d -> {
                JList<?> list = Dialogs.first(d, JList.class);
                int found = -1;
                for (int k = 0; k < list.getModel().getSize(); k++) {
                    if (display.equals(String.valueOf(list.getModel().getElementAt(k)))) {
                        found = k;
                    }
                }
                if (found < 0) {
                    throw i.error(display + " is not offered (already loaded?)");
                }
                list.setSelectedIndex(found);
                Dialogs.click(d, Dialogs.ok());
            }).run(() -> ProjectLibraryActions.doLoadBuiltinLibrary(proj));
            break;
        }
        case "circ": {
            File f = work.resolve(i.str("path")).toFile();
            dialogs().expect("Logisim library", d -> choose(d, f))
                    .run(() -> ProjectLibraryActions.doLoadLogisimLibrary(proj));
            break;
        }
        case "jar": {
            File f = work.resolve(i.str("path")).toFile();
            dialogs().expect("JAR library", d -> choose(d, f))
                    .run(() -> ProjectLibraryActions.doLoadJarLibrary(proj));
            break;
        }
        default:
            throw i.error("kind is builtin, circ or jar");
        }
    }

    /** 부품 목록에서 라이브러리 우클릭 Unload Library. */
    private void unloadLibrary(Intent i) throws Exception {
        Library lib = edtGet(() -> library(i.str("name"), i));
        dialogs().run(() -> ProjectLibraryActions.doUnloadLibrary(proj, lib));
    }

    private static void choose(java.awt.Dialog d, File f) {
        JFileChooser fc = Dialogs.first(d, JFileChooser.class);
        if (fc == null) {
            throw new IllegalStateException("not a file chooser: " + Dialogs.describe(d));
        }
        fc.setSelectedFile(f);
        fc.approveSelection();
    }

    /** 터널 우클릭 Tunnel Color › 색(또는 Auto). */
    private void tunnelColor(Intent i) throws Exception {
        edt(() -> {
            Circuit c = canvas.getCircuit();
            Component t = resolve(i, i.str("id"));
            String name = TunnelColorStore.name(t);
            if (name == null) {
                throw i.error("not a named tunnel: " + t);
            }
            Color col = null;
            if (i.has("color")) {
                Color want = Color.decode(i.str("color"));
                for (Color p : TunnelColors.PALETTE) {
                    if (p.getRGB() == want.getRGB()) {
                        col = p;
                    }
                }
                if (col == null) {
                    throw i.error("the menu offers only the palette colors");
                }
            }
            proj.doAction(TunnelColorStore.action(proj.getLogisimFile(), c, name, col));
        });
    }

    /** 선 우클릭 Signal Group › 그룹(또는 None). */
    private void signalGroup(Intent i) throws Exception {
        edt(() -> {
            Component w = resolve(i, i.str("wire"));
            if (!(w instanceof Wire)) {
                throw i.error("not a wire: " + w);
            }
            SignalGroups.Group g = i.has("group") ? SignalGroups.Group.valueOf(i.str("group").toUpperCase()) : null;
            proj.doAction(SignalGroups.action(proj.getLogisimFile(), canvas.getCircuit(), (Wire) w, g));
        });
    }

    /** 빈 곳 우클릭 Add Area Memo…(고른 것을 감싼다) 또는 메모 안 우클릭 Delete Area Memo. */
    private void areaMemo(Intent i) throws Exception {
        Location at = i.loc("at");
        if (i.bool("delete", false)) {
            edt(() -> {
                AreaMemos.Memo m = AreaMemos.at(proj.getLogisimFile(), canvas.getCircuit(), at);
                if (m == null) {
                    throw i.error("no memo at " + at);
                }
                proj.doAction(AreaMemos.action(proj.getLogisimFile(), canvas.getCircuit(), m, null));
            });
            return;
        }
        edt(() -> use(baseTool("Edit Tool")));
        List<Component> sel = selectRefs(i, i.strings("ids"), false);
        edt(() -> {
            if (!canvas.getCircuit().getAllContaining(at, canvas.getGraphics()).isEmpty()
                    || AreaMemos.at(proj.getLogisimFile(), canvas.getCircuit(), at) != null) {
                throw i.error("Add Area Memo is offered only on empty space outside memos: " + at);
            }
        });
        dialogs().expect("Area Memo", d -> {
            JTextField text = Dialogs.first(d, JTextField.class);
            text.setText(i.has("text") ? i.str("text") : "");
            if (i.has("color")) {
                Dialogs.first(d, JComboBox.class).setSelectedIndex(i.integer("color"));
            }
            if (i.has("bounds")) {
                int[] b = i.ints("bounds", 4);
                List<JSpinner> sp = Dialogs.all(d, JSpinner.class);
                for (int k = 0; k < 4; k++) {
                    sp.get(k).setValue(b[k]);
                }
            }
            Dialogs.click(d, Dialogs.ok());
        }).run(() -> MemoMenu.add(proj, canvas.getCircuit(), sel, at));
    }

    /** 스플리터 우클릭 Edit Splitter… 또는 여러 비트 선 우클릭 Split Bits Here…: 편집 창을 채우고 Apply. */
    private void splitter(Intent i, boolean split) throws Exception {
        Component target = edtGet(() -> resolve(i, split ? i.str("wire") : i.str("id")));
        String ranges = i.str("ranges");
        List<String> names = i.strings("names");
        boolean lsb = i.bool("lsbTop", false);
        dialogs().expect("Splitter", d -> {
            if (!(d instanceof SplitterEditor)) {
                throw i.error("not the splitter editor: " + Dialogs.describe(d));
            }
            if (lsb) {
                ((JRadioButton) field(d, "lsb")).doClick(0);
            }
            ((JTextField) field(d, "ranges")).setText(ranges);
            @SuppressWarnings("unchecked")
            List<JTextField> fields = (List<JTextField>) field(d, "nameFields");
            if (names.size() > fields.size()) {
                throw i.error("the editor shows " + fields.size() + " arms");
            }
            for (int k = 0; k < names.size(); k++) {
                fields.get(k).setText(names.get(k));
            }
            JButton apply = (JButton) field(d, "apply");
            if (!apply.isEnabled()) {
                throw i.error("Apply is disabled: " + ((JLabel) field(d, "problems")).getText());
            }
            apply.doClick(0);
        }).run(() -> {
            if (split) {
                if (!(target instanceof Wire)) {
                    throw i.error("not a wire: " + target);
                }
                ParitySplitterBridge.splitBitsHere(proj, canvas.getCircuit(), (Wire) target, i.loc("at"));
            } else {
                if (!target.getFactory().getName().equals("Splitter")) {
                    throw i.error("not a splitter: " + target);
                }
                SplitterEditor.editExisting(proj, canvas.getCircuit(), target);
            }
        });
    }

    private Dialogs dialogs() {
        return new Dialogs(dialogLog);
    }

    // ------------------------------------------------------------------ 도구와 속성

    private AddTool addTool(Intent i) {
        String libName = i.optStr("lib");
        String name = i.str("name");
        Tool t;
        if (libName == null) {
            Circuit sub = proj.getLogisimFile().getCircuit(name);
            if (sub == null) {
                throw i.error("no circuit " + name + " in this file");
            }
            t = proj.getLogisimFile().getAddTool(sub);
        } else {
            t = library(libName, i).getTool(name);
        }
        if (!(t instanceof AddTool)) {
            throw i.error("no component " + libName + "/" + name);
        }
        return (AddTool) t;
    }

    /** 부품 목록의 라이브러리(파일의 라이브러리와, 아직 안 들어간 번들 Hallym MIPS). 이름이나 보이는 이름으로. */
    private Library library(String name, Intent i) {
        for (Library l : MipsShadow.libraries(proj.getLogisimFile())) {
            if (name.equals(l.getName()) || name.equals(l.getDisplayName())) {
                return l;
            }
        }
        throw i.error("no library " + name);
    }

    private Tool baseTool(String name) {
        return proj.getLogisimFile().getLibrary("Base").getTool(name);
    }

    /** 부품 목록·도구 모음에서 도구를 누른 것과 같다. */
    private void use(Tool t) {
        if (proj.getTool() != t) {
            proj.setTool(t);
        }
    }

    /** 도구를 고른 채 속성 표에서 값을 바꾼다(값이 이미 같으면 손대지 않는다). */
    private void setToolAttr(Tool tool, String attr, String value, Intent i) throws Exception {
        AttrTableToolModel m = new AttrTableToolModel(proj, tool);
        @SuppressWarnings("unchecked")
        Attribute<Object> a = (Attribute<Object>) tool.getAttributeSet().getAttribute(attr);
        if (a == null) {
            throw i.error(tool.getName() + " has no attribute " + attr);
        }
        Object now = tool.getAttributeSet().getValue(a);
        if (now != null && now.equals(a.parse(value))) {
            return;
        }
        setRow(m, attr, value, i);
    }

    /** 속성 표의 한 행에 글자를 넣은 것과 같다(표 편집기가 부르는 행의 setValue). */
    private static void setRow(AttributeSetTableModel m, String attr, String value, Intent i) throws Exception {
        List<Attribute<?>> attrs = m.getAttributeSet().getAttributes();
        for (int k = 0; k < attrs.size(); k++) {
            if (attrs.get(k).getName().equals(attr)) {
                m.getRow(k).setValue(value);
                return;
            }
        }
        List<String> names = new ArrayList<>();
        for (Attribute<?> a : attrs) {
            names.add(a.getName());
        }
        throw i.error("no attribute " + attr + " in the table " + names);
    }

    private EditHandler editHandler() throws Exception {
        return (EditHandler) field(frame, "layoutEditHandler");
    }

    // ------------------------------------------------------------------ 마우스

    private void mouse(int id, Location at, int modsEx, int button, int clicks) {
        Rectangle r = canvas.hcsToScreen(new Rectangle(at.getX(), at.getY(), 0, 0));
        Point p = new Point(r.x, r.y);
        MouseEvent e = new MouseEvent(canvas, id, System.currentTimeMillis(), modsEx, p.x, p.y, clicks, false,
                button);
        canvas.dispatchEvent(e);
    }

    /** 첫 점에서 누르고 다음 점들로 끌고 마지막 점에서 놓는다. */
    private void drag(List<Location> path, boolean shiftPress, boolean shiftDrag) throws Exception {
        int press = InputEvent.BUTTON1_DOWN_MASK | (shiftPress ? InputEvent.SHIFT_DOWN_MASK : 0);
        int dragMods = InputEvent.BUTTON1_DOWN_MASK | (shiftDrag ? InputEvent.SHIFT_DOWN_MASK : 0);
        int release = shiftDrag ? InputEvent.SHIFT_DOWN_MASK : 0;
        Location first = path.get(0);
        Location last = path.get(path.size() - 1);
        edt(() -> mouse(MouseEvent.MOUSE_MOVED, first, 0, MouseEvent.NOBUTTON, 0));
        edt(() -> mouse(MouseEvent.MOUSE_PRESSED, first, press, MouseEvent.BUTTON1, 1));
        for (int k = 1; k < path.size(); k++) {
            Location p = path.get(k);
            edt(() -> mouse(MouseEvent.MOUSE_DRAGGED, p, dragMods, MouseEvent.NOBUTTON, 0));
        }
        edt(() -> mouse(MouseEvent.MOUSE_RELEASED, last, release, MouseEvent.BUTTON1, 1));
        flush();
    }

    /**
     * 고른 것 안의 누를 점. 부품은 격자에서 (5, 5) 떨어진 점(Edit Tool이 선 긋기로 알아듣지 않는 점) 가운데 부품 가운데에
     * 가까운 것, 선만 골랐으면 고른 선 안쪽의 격자점.
     */
    private Location pressPoint(Intent i) {
        Selection sel = proj.getSelection();
        Graphics g = canvas.getGraphics();
        List<Component> order = new ArrayList<>(sel.getComponents());
        order.sort((a, b) -> Boolean.compare(a instanceof Wire, b instanceof Wire));
        for (Component c : order) {
            if (c instanceof Wire) {
                Wire w = (Wire) c;
                Location a = w.getEnd0();
                Location b = w.getEnd1();
                int len = w.getLength();
                for (int off = len / 2 / 10 * 10; off >= 10; off -= 10) {
                    Location p = w.isVertical() ? Location.create(a.getX(), a.getY() + off)
                            : Location.create(a.getX() + off, a.getY());
                    if (!w.endsAt(p) && !sel.getComponentsContaining(p, g).isEmpty()) {
                        return p;
                    }
                }
                continue;
            }
            Bounds bd = c.getBounds(g);
            int cx = bd.getX() + bd.getWidth() / 2;
            int cy = bd.getY() + bd.getHeight() / 2;
            Location best = null;
            long bestD = Long.MAX_VALUE;
            for (int x = bd.getX() / 10 * 10 - 5; x <= bd.getX() + bd.getWidth() + 5; x += 10) {
                for (int y = bd.getY() / 10 * 10 - 5; y <= bd.getY() + bd.getHeight() + 5; y += 10) {
                    Location p = Location.create(x, y);
                    long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                    if (d < bestD && c.contains(p, g) && sel.getComponentsContaining(p, g).contains(c)) {
                        best = p;
                        bestD = d;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        throw i.error("no point to press inside the selection " + sel.getComponents());
    }

    /** Edit Tool이 이 점에서 누름을 선 긋기로 알아듣는가(원조 EditTool.isWiringPoint와 같은 판단). */
    private boolean wiringPoint(Location p) {
        int sx = Canvas.snapXToGrid(p.getX());
        int sy = Canvas.snapYToGrid(p.getY());
        int dx = p.getX() - sx;
        int dy = p.getY() - sy;
        if (dx * dx + dy * dy >= 36) {
            return false;
        }
        Location snap = Location.create(sx, sy);
        if (!canvas.getCircuit().getComponents(snap).isEmpty()) {
            return true;
        }
        for (Wire w : canvas.getCircuit().getWires()) {
            if (w.contains(snap)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 가리키기

    private Circuit circuit(String name, Intent i) {
        Circuit c = proj.getLogisimFile().getCircuit(name);
        if (c == null) {
            throw i.error("no circuit " + name);
        }
        return c;
    }

    private List<Component> resolveAll(Intent i, List<String> refs) {
        List<Component> out = new ArrayList<>();
        for (String r : refs) {
            Component c = resolve(i, r);
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * 가리키는 말: 기호(앞 의도의 {@code as}), {@code label:글}, {@code at:x,y}(또는 {@code at:x,y/부품 이름}),
     * {@code wire:x,y}(그 점을 지나는 선 하나), {@code wire:x0,y0,x1,y1}(두 끝이 그 점인 선).
     */
    private Component resolve(Intent i, String ref) {
        Circuit c = canvas.getCircuit();
        if (ref.startsWith("label:")) {
            String want = ref.substring(6);
            return unique(i, ref, c.getNonWires(), comp -> {
                AttributeSet as = comp.getAttributeSet();
                return as.containsAttribute(StdAttr.LABEL) && want.equals(as.getValue(StdAttr.LABEL));
            });
        }
        if (ref.startsWith("at:")) {
            String body = ref.substring(3);
            String kind = null;
            int slash = body.indexOf('/');
            if (slash >= 0) {
                kind = body.substring(slash + 1);
                body = body.substring(0, slash);
            }
            int[] xy = numbers(i, ref, body, 2);
            Location p = Location.create(xy[0], xy[1]);
            String k = kind;
            return unique(i, ref, c.getNonWires(), comp -> comp.getLocation().equals(p)
                    && (k == null || comp.getFactory().getName().equals(k)));
        }
        if (ref.startsWith("wire:")) {
            int[] n = numbers(i, ref, ref.substring(5), -1);
            if (n.length == 2) {
                Location p = Location.create(n[0], n[1]);
                return unique(i, ref, c.getWires(), w -> ((Wire) w).contains(p));
            }
            if (n.length == 4) {
                Location a = Location.create(n[0], n[1]);
                Location b = Location.create(n[2], n[3]);
                return unique(i, ref, c.getWires(), w -> ((Wire) w).endsAt(a) && ((Wire) w).endsAt(b));
            }
            throw i.error("wire:x,y or wire:x0,y0,x1,y1: " + ref);
        }
        Component s = symbols.get(ref);
        if (s == null) {
            throw i.error("unknown symbol " + ref);
        }
        Component now = follow(s);
        if (!c.contains(now)) {
            throw i.error("symbol " + ref + " is not in circuit " + c.getName() + " (removed or replaced)");
        }
        return now;
    }

    private static int[] numbers(Intent i, String ref, String body, int n) {
        String[] parts = body.split(",");
        if (n >= 0 && parts.length != n) {
            throw i.error("bad reference " + ref);
        }
        int[] out = new int[parts.length];
        for (int k = 0; k < parts.length; k++) {
            try {
                out[k] = Integer.parseInt(parts[k].trim());
            } catch (NumberFormatException e) {
                throw i.error("bad reference " + ref);
            }
        }
        return out;
    }

    private static Component unique(Intent i, String ref, Collection<? extends Component> in,
            java.util.function.Predicate<Component> p) {
        List<Component> hits = new ArrayList<>();
        for (Component c : in) {
            if (p.test(c)) {
                hits.add(c);
            }
        }
        if (hits.size() != 1) {
            throw i.error(ref + " matches " + hits.size() + " components " + hits);
        }
        return hits.get(0);
    }

    private Component follow(Component c) {
        Set<Component> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Component now = c;
        while (!inFile(now) && replaced.containsKey(now) && seen.add(now)) {
            now = replaced.get(now);
        }
        return now;
    }

    private boolean inFile(Component c) {
        for (Circuit k : proj.getLogisimFile().getCircuits()) {
            if (k.contains(c)) {
                return true;
            }
        }
        return false;
    }

    private void followSymbols() {
        for (Map.Entry<String, Component> e : symbols.entrySet()) {
            e.setValue(follow(e.getValue()));
        }
    }

    private void watchCircuits() {
        for (Circuit c : proj.getLogisimFile().getCircuits()) {
            if (watched.add(c)) {
                c.addCircuitListener(tracker);
            }
        }
    }

    /**
     * 기호가 부품을 따라가게: 원조 ReplacementMap의 바꿔치기(옮기기·되돌리기)와, 한 변경에서 지워지고 더해진 같은
     * 종류·같은 속성의 부품 한 쌍(정렬·같은 간격처럼 지우고 더하는 편집).
     */
    private void track(CircuitEvent e) {
        if (e.getAction() != CircuitEvent.TRANSACTION_DONE) {
            return;
        }
        CircuitTransactionResult r = e.getResult();
        ReplacementMap rm = r == null ? null : r.getReplacementMap(e.getCircuit());
        if (rm == null) {
            return;
        }
        Set<Component> used = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Component> removedOnly = new ArrayList<>();
        for (Component old : rm.getRemovals()) {
            if (old instanceof Wire) {
                continue;
            }
            Collection<Component> news = rm.get(old);
            List<Component> same = new ArrayList<>();
            for (Component n : news == null ? Collections.<Component>emptyList() : news) {
                if (!(n instanceof Wire) && n.getFactory() == old.getFactory()) {
                    same.add(n);
                }
            }
            if (same.size() == 1) {
                replaced.put(old, same.get(0));
                used.add(same.get(0));
            } else if (news == null || news.isEmpty()) {
                removedOnly.add(old);
            }
        }
        List<Component> addedOnly = new ArrayList<>();
        for (Component n : rm.getAdditions()) {
            if (!(n instanceof Wire) && !used.contains(n) && !rm.getRemovals().contains(n)) {
                addedOnly.add(n);
            }
        }
        for (Component old : removedOnly) {
            List<Component> hits = new ArrayList<>();
            for (Component n : addedOnly) {
                if (n.getFactory() == old.getFactory() && sameAttrs(old, n)) {
                    hits.add(n);
                }
            }
            if (hits.size() == 1) {
                List<Component> back = new ArrayList<>();
                for (Component o : removedOnly) {
                    if (o.getFactory() == old.getFactory() && sameAttrs(o, hits.get(0))) {
                        back.add(o);
                    }
                }
                if (back.size() == 1) {
                    replaced.put(old, hits.get(0));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean sameAttrs(Component a, Component b) {
        AttributeSet x = a.getAttributeSet();
        AttributeSet y = b.getAttributeSet();
        if (!x.getAttributes().equals(y.getAttributes())) {
            return false;
        }
        for (Attribute<?> at : x.getAttributes()) {
            Attribute<Object> o = (Attribute<Object>) at;
            Object u = x.getValue(o);
            Object v = y.getValue(o);
            if (u == null ? v != null : v == null || !o.toStandardString(u).equals(o.toStandardString(v))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 도우미

    private static List<Component> nonWires(Collection<Component> comps) {
        List<Component> out = new ArrayList<>();
        for (Component c : comps) {
            if (!(c instanceof Wire)) {
                out.add(c);
            }
        }
        return out;
    }

    private static Set<Component> identity(Collection<Component> c) {
        Set<Component> s = Collections.newSetFromMap(new IdentityHashMap<>());
        s.addAll(c);
        return s;
    }

    private static String text(Object v) {
        return v instanceof Double && ((Double) v) == Math.rint((Double) v) ? Long.toString(((Double) v).longValue())
                : String.valueOf(v);
    }

    /** 라이브러리 도구들의 도구 속성을 처음 값으로(다음에 읽을 때 부품 팩토리의 기본 속성으로 새로 만든다). */
    static void resetTools(Library lib) throws Exception {
        for (Tool t : lib.getTools()) {
            if (t instanceof AddTool) {
                Object attrs = field(t, "attrs");
                setField(attrs, "baseAttrs", null);
                setField(t, "bounds", null);
            }
        }
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Class<?> k = target.getClass();
        while (k != null) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                k = k.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static Object field(Object target, String name) throws Exception {
        Class<?> k = target.getClass();
        while (k != null) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                k = k.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(10);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void edt(Dialogs.ThrowingRunnable r) throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                err.set(t);
            }
        });
        rethrow(err.get());
    }

    private static <T> T edtGet(Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        edt(() -> out.set(c.call()));
        return out.get();
    }

    private static void rethrow(Throwable t) throws Exception {
        if (t == null) {
            return;
        }
        if (t instanceof Exception) {
            throw (Exception) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw new RuntimeException(t);
    }
}
