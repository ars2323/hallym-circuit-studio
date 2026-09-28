/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.edit;

import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;

import kr.ac.hallym.hcs.app.groups.SignalGroups;
import kr.ac.hallym.hcs.app.labels.TunnelColors;
import kr.ac.hallym.hcs.app.memo.AreaMemos;
import kr.ac.hallym.hcs.engine.doc.Doc;
import kr.ac.hallym.hcs.engine.rpc.RpcError;

/**
 * 학생이 회로에 두는 표시 정보의 편집 의도(N-15, D-151): 신호 그룹({@code edit.signalGroup}, v1 E-04)과 영역
 * 메모({@code edit.areaMemo}, v1 E-08). 둘 다 .circ 확장 정보(hcs:ext)만 바꾸고 원조 회로 부분은 그대로다. v1 우클릭
 * 항목과 같은 동작({@link SignalGroups#action}, {@link AreaMemos#action})을 {@code Project.doAction}에 한 번 넘기므로
 * 되돌리기 한 단계이고, 편집 동등성 골든(N-01, tests/parity 13·16·18)의 Swing 경로와 같은 코드다.
 */
public final class ExtIntents {
    /** 메모 상자의 가장 작은 폭·높이(v1 메모 창의 칸 최솟값). */
    static final int MIN_SIZE = 20;
    /** 메모 자리의 한계(v1 메모 창의 칸). */
    static final int LIMIT = 100_000;

    private ExtIntents() {
    }

    // ---- edit.signalGroup: 선 우클릭 Signal Group ▸ Control/Data/Address/None ----

    /** 선 wire의 넷에 그룹(null이면 None: 학생이 정한 그룹을 뗀다). 같은 그룹이면 바뀐 것이 없다. */
    public static Intents.Result signalGroup(Doc d, Circuit c, Component wire, String group) throws RpcError {
        Intents.editable(d, c);
        if (!(wire instanceof Wire)) {
            throw RpcError.params("'wire' must be a wire id");
        }
        SignalGroups.Group g = group == null ? null : parse(group);
        if (SignalGroups.assignedTo(d.file(), c, (Wire) wire) == g) {
            return Intents.Result.unchanged("same");
        }
        d.show(c);
        d.project().doAction(SignalGroups.action(d.file(), c, (Wire) wire, g));
        return new Intents.Result(true, null, null);
    }

    static SignalGroups.Group parse(String group) throws RpcError {
        for (SignalGroups.Group g : SignalGroups.Group.values()) {
            if (g.key().equals(group)) {
                return g;
            }
        }
        throw RpcError.params("group must be control, data or address");
    }

    // ---- edit.areaMemo: 빈 곳·메모 안 우클릭의 Add/Edit/Fit/Delete Area Memo ----

    /** edit.areaMemo의 인자. text·color·bounds는 없으면 null. */
    public static final class Memo {
        public Location at;
        public List<Component> around = List.of();
        public String text;
        public Integer color;
        public int[] bounds;
        public boolean delete;
    }

    /**
     * at을 감싸는 메모(겹치면 가장 작은 것)가 있으면 그 메모를 고치거나(글·색·자리, around가 있으면 그 둘레에 맞춤 =
     * Fit Area Memo to Selection) 지우고(delete), 없으면 새 메모를 더한다(Add Area Memo…: around의 둘레, 없으면 at에
     * 기본 크기; 색은 메모 수로 돌아가며). 글은 앞뒤 공백을 뺀다(v1 메모 창).
     */
    public static Intents.Result areaMemo(Doc d, Circuit c, Memo m) throws RpcError {
        Intents.editable(d, c);
        if (m.color != null && (m.color < 0 || m.color >= TunnelColors.PALETTE.length)) {
            throw RpcError.params("color must be 0.." + (TunnelColors.PALETTE.length - 1));
        }
        Bounds given = null;
        if (m.bounds != null) {
            int[] b = m.bounds;
            if (b.length != 4 || b[2] < MIN_SIZE || b[3] < MIN_SIZE || Math.abs(b[0]) > LIMIT || Math.abs(b[1]) > LIMIT
                    || b[2] > LIMIT || b[3] > LIMIT) {
                throw RpcError.params("bounds must be [x, y, w, h] with w and h from " + MIN_SIZE);
            }
            given = Bounds.create(b[0], b[1], b[2], b[3]);
        }
        String text = m.text == null ? null : m.text.trim();
        AreaMemos.Memo here = AreaMemos.at(d.file(), c, m.at);
        if (m.delete) {
            if (here == null) {
                return Intents.Result.unchanged("noMemo");
            }
            d.show(c);
            d.project().doAction(AreaMemos.action(d.file(), c, here, null));
            return new Intents.Result(true, "deleted", null);
        }
        if (here != null) {
            Bounds b = given != null ? given : !m.around.isEmpty() ? AreaMemos.around(m.around, m.at) : here.bounds;
            AreaMemos.Memo after = new AreaMemos.Memo(b, m.color != null ? m.color : here.color,
                    text != null ? text : here.text);
            if (after.equals(here)) {
                return Intents.Result.unchanged("same");
            }
            d.show(c);
            d.project().doAction(AreaMemos.action(d.file(), c, here, after));
            return new Intents.Result(true, "edited", null);
        }
        Bounds b = given != null ? given : AreaMemos.around(m.around, m.at);
        int color = m.color != null ? m.color : AreaMemos.of(d.file(), c).size() % TunnelColors.PALETTE.length;
        d.show(c);
        d.project().doAction(AreaMemos.action(d.file(), c, null, new AreaMemos.Memo(b, color, text == null ? "" : text)));
        return new Intents.Result(true, "added", null);
    }
}
