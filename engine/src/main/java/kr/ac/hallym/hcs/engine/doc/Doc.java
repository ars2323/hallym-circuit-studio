/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.doc;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.proj.Project;
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

    Doc(String id, EngineLoader loader, LogisimFile file, boolean readOnly) {
        this.id = id;
        this.loader = loader;
        this.file = file;
        this.readOnly = readOnly;
        this.proj = new Project(file);
        RedoStack.of(proj); // 되돌리기 사건을 처음부터 듣는다
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

    /** 이 파일의 회로. 없으면 오류 1. */
    public Circuit circuit(String circuitId) throws RpcError {
        Circuit c = ids.circuit(circuitId);
        if (c == null || !file.contains(c)) {
            throw RpcError.notFound("circuit", circuitId);
        }
        return c;
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
