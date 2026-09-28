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
    /**
     * 복구 파일의 내용으로 열었다(N-19, D-152): 디스크의 파일과 다르므로 저장하기 전까지는 되돌리기를 모두 되돌려도
     * 저장하지 않은 편집이 있다.
     */
    private boolean recovered;
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
        return recovered || proj.isFileDirty();
    }

    /** 복구 파일의 내용으로 열었고 아직 저장하지 않았는가. */
    public boolean isRecovered() {
        return recovered;
    }

    void setRecovered(boolean value) {
        recovered = value;
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

    /**
     * 편집 전(편집 의도에서만, Engine.edit: SimGate 안, 알림·되살리기 저널이 따른다): 원조처럼 편집하는 회로를 지금
     * 회로로 둔다(Swing에서 그 회로를 보고 있어야 편집할 수 있는 것과 같다). 선택이 다른 회로의 것이면 원조
     * {@code Project.setCircuitState}가 창의 선택에 하는 것처럼 떠 있는 것을 그 회로(선택이 생긴 회로)에 내려놓고
     * 선택을 비운다(N-08, D-146: 창이 없는 엔진에서는 원조가 이 일을 하지 않는다). 시뮬레이션이 보는 회로를 바꾼
     * 뒤({@link #view})라도 붙여 넣은 부품은 붙여 넣은 회로에 내려앉는다.
     */
    public void show(Circuit c) {
        Circuit cur = proj.getCurrentCircuit();
        boolean holding = canvas != null && !canvas.getSelection().isEmpty();
        Circuit home = holding && selectionHome != null ? selectionHome : cur;
        if (home != c && holding) {
            boolean floating = !canvas.getSelection().getFloatingComponents().isEmpty();
            if (cur != home) {
                proj.setCurrentCircuit(home); // 원조 Drop은 지금 회로에 내려놓는다
            }
            proj.doAction(com.cburch.logisim.gui.main.SelectionActions.dropAll(canvas.getSelection()));
            if (floating) {
                drops++;
            }
        }
        if (proj.getCurrentCircuit() != c) {
            proj.setCurrentCircuit(c);
        }
    }

    /**
     * 시뮬레이션이 보는 회로(sim.watch·sim.poke·sim.pinValue): 지금 회로만 바꾸고 모델은 바꾸지 않는다. 떠 있는
     * 선택은 그대로 두고, 다음 편집({@link #show})이 선택이 생긴 회로에 내려놓는다(화면은 회로를 바꾸기 전에
     * {@code edit.select}로 비워 내려놓는다: 편집 의도라 알림·저널이 따른다).
     */
    public void view(Circuit c) {
        if (proj.getCurrentCircuit() != c) {
            proj.setCurrentCircuit(c);
        }
    }

    /** 선택이 속한 회로: 고른 것이 있으면 그것이 생긴 회로, 없으면 지금 회로(edit.selection의 circuitId). */
    public Circuit selectionCircuit() {
        boolean holding = canvas != null && !canvas.getSelection().isEmpty();
        return holding && selectionHome != null ? selectionHome : proj.getCurrentCircuit();
    }

    /** 떠 있는 것을 내려놓은 수({@link #show}): 편집 의도가 바뀐 것이 없다고 답해도 모델이 바뀌었음을 안다. */
    public int drops() {
        return drops;
    }

    private int drops;
    /** 선택이 생긴 회로(비면 null): 원조 창의 선택은 그 창이 보던 회로의 것이다. */
    private Circuit selectionHome;

    /**
     * 편집 대상(N-08, D-146): 원조 창의 Canvas 선택과 같은 객체다. 고른 것(회로에 있음)과 떠 있는 것(붙여넣거나 복제해
     * 아직 내려앉지 않음)을 들고, 원조 Selection의 청취자가 옮기기·되돌리기의 바꿔치기를 따라간다.
     */
    public com.cburch.logisim.gui.main.Selection selection() {
        return canvas().getSelection();
    }

    /** 선택이 바뀌면 비우는 부품별 키 설정기(원조 SelectTool.keyHandlers, 여러 자리 숫자의 상태를 든다). */
    public Map<Component, com.cburch.logisim.tools.key.KeyConfigurator> keyHandlers;
    /** 부품 놓기 도구의 키 설정기(원조 AddTool.keyHandler, 도구마다). */
    public final Map<Tool, com.cburch.logisim.tools.key.KeyConfigurator> toolKeyHandlers = new java.util.IdentityHashMap<>();
    /** 모양 편집(N-11, D-153): 회로마다 화면 없는 원조 모양 편집 화면. 이 문서와 함께 사라진다. */
    public final Map<Circuit, Object> appearanceSessions = new java.util.IdentityHashMap<>();

    /** 화면에 붙지 않은 Canvas(선택·Poke 사건용, 그리기 스레드 멈춤). 처음 부를 때 만든다. */
    public Canvas canvas() {
        if (canvas == null) {
            canvas = new HiddenCanvas(proj);
            canvas.closeCanvas();
            canvas.getSelection().addListener(e -> {
                keyHandlers = null;
                if (canvas.getSelection().isEmpty()) {
                    selectionHome = null;
                } else if (selectionHome == null) {
                    selectionHome = proj.getCurrentCircuit();
                }
            });
            tracker.alsoLive(() -> new ArrayList<>(canvas.getSelection().getFloatingComponents()));
        }
        return canvas;
    }

    /**
     * 창이 없는 Canvas는 크기가 없다: 크기 재기를 하지 않는다(D-143). 원조 Canvas는 지금 회로의 무효화 사건마다
     * 회로 경계를 다시 잰다(completeAction → computeSize → Circuit.getBounds가 부품 집합을 훑는다). 클럭 틱의 무효화
     * 사건은 원조 시뮬레이터 스레드에서 오므로, 엔진 스레드가 부품을 넣고 빼는 동안 재면
     * ConcurrentModificationException으로 시뮬레이터 스레드가 끝난다. 전파 요청(completeAction의 나머지)은 그대로다.
     */
    private static final class HiddenCanvas extends Canvas {
        HiddenCanvas(Project proj) {
            super(proj);
        }

        @Override
        public void computeSize(boolean immediate) {
            // 창이 없다: 잴 크기도, 알릴 스크롤 창도 없다
        }

        /** 글자 칸(TextEditable.getTextCaret)이 글꼴을 재는 그림판: 화면에 붙지 않은 Canvas는 null을 준다. */
        @Override
        public java.awt.Graphics getGraphics() {
            return new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();
        }
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
