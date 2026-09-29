/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.proj.Action;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import kr.ac.hallym.hcs.app.edit.RedoStack;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * Undo History(E-05, N-21, D-162): v1 {@code UndoHistory}의 목록과 옮기기에서 창만 뺐다. 목록은 원조 되돌리기
 * 기록(원조 {@code Project}의 {@code undoLog}, 오래된 것부터, 반사로 읽기만)과 포크의 다시 실행 기록({@link RedoStack#names}, 바로 다음 것부터)을
 * 읽기만 한다. 옮기기는 원조 {@code undoAction}과 {@code RedoStack.redo}를 여러 번 부를 뿐이다({@link Intents#undo},
 * {@link Intents#redo}와 같은 길).
 */
public final class HistoryIntents {
    /** 한 번에 옮길 수 있는 가장 큰 수(원조 되돌리기 기록은 MAX_UNDO_SIZE = 64). */
    static final int MAX_MOVES = 10_000;

    private HistoryIntents() {
    }

    /**
     * model.history = {fileId, rows:[{kind, name?, moves}]}: 맨 위 "start"(누르면 모두 되돌림, moves = -되돌릴 수),
     * "undo" 줄(오래된 것부터, 누르면 그 동작을 마친 상태까지: moves ≤ 0), "now"(0), "redo" 줄(바로 다음 것부터, moves
     * > 0). name은 원조 동작 이름(Action.getName, 영어).
     */
    public static JsonObject rows(Doc d) {
        JsonObject o = new JsonObject();
        o.addProperty("fileId", d.id());
        JsonArray rows = new JsonArray();
        List<Action> undo = undoLog(d.project());
        int n = undo.size();
        rows.add(row("start", null, -n));
        for (int i = 0; i < n; i++) {
            rows.add(row("undo", undo.get(i).getName(), -(n - 1 - i)));
        }
        rows.add(row("now", null, 0));
        List<String> redo = RedoStack.of(d.project()).names();
        for (int i = 0; i < redo.size(); i++) {
            rows.add(row("redo", redo.get(i), i + 1));
        }
        o.add("rows", rows);
        return o;
    }

    /**
     * 원조 되돌리기 기록의 동작들(오래된 것부터). 원조 {@code Project}에는 읽는 길이 {@code getLastAction}뿐이라
     * 개인 필드 {@code undoLog}와 {@code ActionData.action}을 반사로 읽기만 한다(원조 파일을 고치지 않는다, D-163 4).
     */
    static List<Action> undoLog(com.cburch.logisim.proj.Project proj) {
        try {
            java.lang.reflect.Field log = com.cburch.logisim.proj.Project.class.getDeclaredField("undoLog");
            log.setAccessible(true);
            List<Action> out = new java.util.ArrayList<>();
            for (Object data : (java.util.List<?>) log.get(proj)) {
                java.lang.reflect.Field action = data.getClass().getDeclaredField("action");
                action.setAccessible(true);
                out.add((Action) action.get(data));
            }
            return out;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the original undo log cannot be read", e);
        }
    }

    private static JsonObject row(String kind, String name, int moves) {
        JsonObject r = new JsonObject();
        r.addProperty("kind", kind);
        if (name != null) {
            r.addProperty("name", name);
        }
        r.addProperty("moves", moves);
        return r;
    }

    /**
     * edit.history {moves, circuitId?}: 음수면 그만큼 되돌리고 양수면 그만큼 다시 실행한다(v1 UndoHistory.go). 기록보다
     * 많으면 있는 만큼만. 한 줄을 누른 것이 의도 하나다(저널에 한 줄, 되살리기가 같은 수만큼 되풀이한다). 바뀐 것이
     * 없으면 outcome "nothing".
     */
    public static Intents.Result go(Doc d, int moves, Circuit c) throws RpcError {
        if (moves < -MAX_MOVES || moves > MAX_MOVES) {
            throw RpcError.params("moves must be between -" + MAX_MOVES + " and " + MAX_MOVES);
        }
        boolean changed = false;
        for (int i = 0; i < Math.abs(moves); i++) {
            Intents.Result r = moves < 0 ? Intents.undo(d, c) : Intents.redo(d, c);
            if (!r.changed) {
                break;
            }
            changed = true;
        }
        return changed ? new Intents.Result(true, null, null) : Intents.Result.unchanged("nothing");
    }
}
