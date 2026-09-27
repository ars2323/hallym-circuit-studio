/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.doc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.LocaleManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.edit.RedoStack;
import kr.ac.hallym.hcs.engine.model.Ids;
import kr.ac.hallym.hcs.engine.model.ModelJson;
import kr.ac.hallym.hcs.engine.model.ModelTracker;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 열린 파일 하나(fileId). Swing 앱의 창 하나에 해당한다: 원조 {@link Project}(되돌리기 기록, 시뮬레이터)와
 * 다시 실행 기록({@link RedoStack}), 식별자, 모델 추적을 든다.
 *
 * <p>Swing을 쓰는 곳은 {@link #canvas()} 하나로 가둔다(D-134): 원조 선택({@code Selection})과 Poke 도구의
 * 사건({@code ComponentUserEvent})이 {@link Canvas}를 요구해서, 화면에 붙지 않는 Canvas를 하나 만들고 그리기
 * 스레드는 바로 멈춘다. 선택이 필요한 옮기기와 Poke만 이것을 쓴다(N-27에서 걷어 낸다).
 */
public final class Doc {
    private final String id;
    private final EngineLoader loader;
    private final LogisimFile file;
    private final Project proj;
    private final Ids ids = new Ids();
    private final ModelTracker tracker;
    private boolean readOnly;
    private Canvas canvas;
    /**
     * Swing 앱의 Canvas가 프로젝트 사건마다 하는 전파 요청(Canvas.completeAction)과 같다. 원조 목록은 약한 참조라
     * 필드로 붙들어 둔다.
     */
    private final ProjectListener propagate = e -> {
        int act = e.getAction();
        if (act != ProjectEvent.ACTION_SELECTION && act != ProjectEvent.ACTION_START
                && act != ProjectEvent.UNDO_START) {
            e.getProject().getSimulator().requestPropagate();
        }
    };

    Doc(String id, EngineLoader loader, LogisimFile file, boolean readOnly) {
        this(id, loader, file, readOnly, null);
    }

    /**
     * restoreCircuits: 다시 시작한 엔진이 되살리는 파일이면 {회로 이름: 앞 엔진의 회로 id}(D-142). 모델 기준을 잡기
     * 전에 붙여서 서브회로 인스턴스의 {@code subcircuit}도 처음부터 그 id다. 이름이 없는 회로는 새 id를 받는다.
     */
    Doc(String id, EngineLoader loader, LogisimFile file, boolean readOnly, Map<String, String> restoreCircuits) {
        this.id = id;
        this.loader = loader;
        this.file = file;
        this.readOnly = readOnly;
        this.proj = new Project(file);
        RedoStack.of(proj); // 되돌리기 사건을 처음부터 듣는다
        proj.addProjectListener(propagate);
        proj.getSimulator().requestPropagate(); // 연 회로의 첫 전파(Swing은 창이 뜨며 한다)
        if (restoreCircuits != null) {
            for (Circuit c : file.getCircuits()) {
                String old = restoreCircuits.get(c.getName());
                if (old != null) {
                    ids.adopt(c, old);
                }
            }
        }
        this.tracker = new ModelTracker(new ModelJson(ids, file), file);
        tracker.baseline();
    }

    public String id() {
        return id;
    }

    public EngineLoader loader() {
        return loader;
    }

    public LogisimFile file() {
        return file;
    }

    public Project project() {
        return proj;
    }

    public Ids ids() {
        return ids;
    }

    public ModelTracker tracker() {
        return tracker;
    }

    public ModelJson json() {
        return tracker.json();
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    void setReadOnly(boolean value) {
        readOnly = value;
    }

    public boolean isDirty() {
        return proj.isFileDirty();
    }

    /**
     * 이 파일의 회로 또는 파일이 쓰는 .circ 라이브러리의 회로(읽기 전용: 편집은 오류 3). 없으면 오류 1.
     */
    public Circuit circuit(String circuitId) throws RpcError {
        Circuit c = ids.circuit(circuitId);
        if (c == null || !(file.contains(c) || inLibraries(file, c, new java.util.HashSet<>()))) {
            throw RpcError.notFound("circuit", circuitId);
        }
        return c;
    }

    /** c가 lib(와 그 안의 라이브러리)의 서브회로 도구인가. 팩토리를 새로 불러오지 않는다. */
    private static boolean inLibraries(Library lib, Circuit c, java.util.Set<Library> seen) {
        for (Library l : lib.getLibraries()) {
            if (!seen.add(l)) {
                continue;
            }
            for (Tool t : l.getTools()) {
                if (t instanceof AddTool && ((AddTool) t).getFactory(false) instanceof SubcircuitFactory
                        && ((SubcircuitFactory) ((AddTool) t).getFactory(false)).getSubcircuit() == c) {
                    return true;
                }
            }
            if (inLibraries(l, c, seen)) {
                return true;
            }
        }
        return false;
    }

    /** 회로 c 안의 부품·선. 없으면 오류 1. */
    public Component component(Circuit c, String componentId) throws RpcError {
        Component x = ids.component(componentId);
        if (x == null || !c.contains(x)) {
            throw RpcError.notFound("component", componentId);
        }
        return x;
    }

    public List<Component> components(Circuit c, List<String> componentIds) throws RpcError {
        List<Component> ret = new ArrayList<>();
        for (String cid : componentIds) {
            Component x = component(c, cid);
            if (!ret.contains(x)) {
                ret.add(x);
            }
        }
        return ret;
    }

    /** 편집 전: 원조처럼 편집하는 회로를 지금 회로로 둔다(Swing에서 그 회로를 보고 있어야 편집할 수 있는 것과 같다). */
    public void show(Circuit c) {
        if (proj.getCurrentCircuit() != c) {
            proj.setCurrentCircuit(c);
        }
    }

    /** 화면에 붙지 않은 Canvas(선택·Poke 사건용, 그리기 스레드 멈춤). 처음 부를 때 만든다. */
    public Canvas canvas() {
        if (canvas == null) {
            canvas = new Canvas(proj);
            canvas.closeCanvas();
        }
        return canvas;
    }

    /** 회로 목록 [{circuitId, name}]. */
    public JsonArray circuitRefs() {
        JsonArray a = new JsonArray();
        for (Circuit c : file.getCircuits()) {
            JsonObject o = new JsonObject();
            o.addProperty("circuitId", ids.of(c));
            o.addProperty("name", c.getName());
            a.add(o);
        }
        return a;
    }

    public String mainId() {
        Circuit main = file.getMainCircuit();
        return main == null ? null : ids.of(main);
    }

    void close() {
        proj.getSimulator().shutDown();
        if (canvas != null) {
            LocaleManager.removeLocaleListener(canvas);
        }
    }
}
