/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.diag;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Kinds;
import kr.ac.hallym.hcs.app.model.Names;
import kr.ac.hallym.hcs.app.model.OriginTrace;
import kr.ac.hallym.hcs.app.record.Recording;

/**
 * MIPS 부품의 값 의존 검사(D-04, #41): 영역 밖 주소, 워드 정렬, 스택 한계, Console syscall 문제를 처음 생긴 사이클에
 * Messages로 말한다. 문구는 부품 몸체의 빨간 글자와 같다(lib-mips의 공개 접근자 problemText·statusText·addressStatus를
 * 이름으로 부른다: lib-mips는 따로 불리는 JAR라 포크가 컴파일 때 모른다). 떠 있는 MemWrite·MemRead는 X 쓰기 감지(D-03)와
 * 정적 검사가 말하므로 여기서는 뺀다. 영역 겹침은 정적 검사(MEMORY_OVERLAP)가 말한다.
 */
public final class MipsCheck {
    private MipsCheck() {
    }

    /**
     * 지금 상태(맨 위 root, 기록의 마지막 스텝 step)의 MIPS 부품 문제. seen은 이미 말한 열쇠 → 처음 말한 스텝. 원인을
     * 찾을 수 있으면(Console V0·A0가 정해지지 않음) 에지 직전 기록값으로 출처를 붙인다.
     */
    public static List<Diagnostic> check(Circuit top, CircuitState root, Recording rec, int step,
            Map<String, Integer> seen) {
        List<Diagnostic> out = new ArrayList<>();
        for (List<Component> path : rec.paths()) {
            CircuitState s = InstancePaths.stateFor(root, path);
            if (s == null) {
                continue;
            }
            Circuit c = s.getCircuit();
            for (Component x : c.getNonWires()) {
                String f = Kinds.of(x).factory();
                String kind;
                String text;
                int cause = -1;
                switch (f) {
                case "Data Memory":
                case "Stack":
                    kind = call(s.getData(x), "problemName");
                    if (kind == null || kind.equals("CONTROL_FLOATING") || kind.equals("OVERLAP")) {
                        continue;
                    }
                    text = call(s.getData(x), "problemText");
                    break;
                case "Instruction Memory":
                    text = addressStatus(x, s.getValue(x.getEnd(0).getLocation()));
                    kind = "IMEM_UNALIGNED";
                    break;
                case "Console":
                    text = call(s.getData(x), "statusText");
                    kind = "CONSOLE|" + text;
                    Value v0 = rec.value(path, x.getEnd(1).getLocation(), Math.max(rec.first(), step - 1));
                    Value a0 = rec.value(path, x.getEnd(2).getLocation(), Math.max(rec.first(), step - 1));
                    cause = v0 != null && !v0.isFullyDefined() ? 1 : a0 != null && !a0.isFullyDefined() ? 2 : -1;
                    break;
                default:
                    continue;
                }
                if (text == null) {
                    continue;
                }
                String key = "M|" + kind + "|" + System.identityHashCode(x) + ":" + path;
                if (seen.putIfAbsent(key, step) != null) {
                    continue;
                }
                String where = Names.path(InstancePaths.describe(top, path), Names.name(c, x));
                String because = "";
                List<Component> comps = new ArrayList<>(Collections.singletonList(x));
                List<Wire> wires = new ArrayList<>();
                if (cause >= 0) {
                    OriginTrace t = new OriginTrace(top, rec.originValues());
                    OriginTrace.Origin o = t.find(t.node(path, c, x.getEnd(cause).getLocation()), step - 1);
                    if (o != null) {
                        if (seen.putIfAbsent(OriginText.key(o), step) != null) {
                            continue; // 같은 원인을 이미 말했다
                        }
                        because = Messages.get("diag.causePrefix", OriginText.cause(top, o));
                    }
                }
                out.add(new Diagnostic(Diagnostic.Kind.MIPS_STATUS, c, path, step, comps, wires,
                        x.getLocation(), DynamicCheck.cycleOf(step), where, text, because));
            }
        }
        return out;
    }

    /** 이름으로 부르는 문자열 접근자(없거나 실패하면 null). */
    static String call(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            Object r = m.invoke(target);
            return r == null ? null : r.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Instruction Memory의 주소 입력 문제(lib-mips InstructionMemory.addressStatus). */
    static String addressStatus(Component imem, Value addr) {
        try {
            Method m = imem.getFactory().getClass().getMethod("addressStatus", Value.class);
            m.setAccessible(true);
            Object r = m.invoke(null, addr);
            return r == null ? null : r.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
