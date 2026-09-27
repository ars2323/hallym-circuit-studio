/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.engine.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;

/**
 * 안정된 식별자(docs/engine-api.md 3절, D-134). Logisim 객체 자체(동일성, {@link IdentityHashMap})에 id를 붙인다.
 * 번호는 엔진 전체에서 하나씩 늘어나 파일이 달라도 겹치지 않는다. 회로에서 사라진 부품·선의 id는
 * {@link #retain}이 지운다: 되돌리기로 같은 객체가 돌아오면 새 id를 받는다(화면은 옛 id를 이미 지웠다).
 *
 * <p>엔진이 죽었다가 다시 시작하면(N-04, D-142, docs/engine-api.md 7절) 화면은 앞 엔진의 파일·회로 id로 탭을 들고
 * 있다. 새 엔진은 {@code engine.hello}의 {@code idFloor}로 번호를 그보다 크게 시작하고({@link #floor}), 되살리는
 * 파일은 앞의 파일 id({@link #restoredFileId})와 회로 id({@link #adopt})를 다시 쓴다. 부품·선 id는 되살리지 않는다
 * (화면이 모델을 다시 받는다). 앞 엔진의 부품 id가 새 엔진의 다른 부품을 가리키는 일은 floor가 막는다.
 */
public final class Ids {
    private static final AtomicLong NEXT_COMPONENT = new AtomicLong();
    private static final AtomicLong NEXT_WIRE = new AtomicLong();
    private static final AtomicLong NEXT_CIRCUIT = new AtomicLong();
    private static final AtomicLong NEXT_FILE = new AtomicLong();

    private final Map<Object, String> ids = new IdentityHashMap<>();
    private final Map<String, Object> objects = new HashMap<>();

    /**
     * 새 번호가 모두 n보다 크게 한다(다시 시작한 엔진, {@code engine.hello}의 {@code idFloor}). 이미 더 크면 그대로다.
     */
    public static void floor(long n) {
        for (AtomicLong next : new AtomicLong[] {NEXT_COMPONENT, NEXT_WIRE, NEXT_CIRCUIT, NEXT_FILE}) {
            next.accumulateAndGet(n, Math::max);
        }
    }

    /** 되살리는 파일이 다시 쓰는 앞 엔진의 파일 id. 형식("f" + 양의 정수)이 아니면 null. */
    public static String restoredFileId(String id) {
        long n = number(id, 'f');
        if (n <= 0) {
            return null;
        }
        NEXT_FILE.accumulateAndGet(n, Math::max);
        return id;
    }

    /** "k17" 꼴 id의 번호(kind 글자가 다르거나 형식이 아니면 -1). */
    static long number(String id, char kind) {
        if (id == null || id.length() < 2 || id.length() > 19 || id.charAt(0) != kind) {
            return -1;
        }
        for (int i = 1; i < id.length(); i++) {
            if (id.charAt(i) < '0' || id.charAt(i) > '9') {
                return -1;
            }
        }
        return Long.parseLong(id.substring(1));
    }

    /** 새 파일 id("f1", "f2", …). */
    public static String nextFileId() {
        return "f" + NEXT_FILE.incrementAndGet();
    }

    /** 부품("k…") 또는 선("w…")의 id. 처음 보면 새로 준다. */
    public String of(Component c) {
        String id = ids.get(c);
        if (id == null) {
            id = c instanceof Wire ? "w" + NEXT_WIRE.incrementAndGet() : "k" + NEXT_COMPONENT.incrementAndGet();
            put(c, id);
        }
        return id;
    }

    /** 회로 id("c…"). 회로 이름이 바뀌어도 그대로다. */
    public String of(Circuit c) {
        String id = ids.get(c);
        if (id == null) {
            id = "c" + NEXT_CIRCUIT.incrementAndGet();
            put(c, id);
        }
        return id;
    }

    /**
     * 되살리는 파일의 회로에 앞 엔진이 준 id를 붙인다. 아직 id가 없는 회로에만, 이 표에서 쓰지 않은 "c…"만 붙인다.
     * 붙였으면 true.
     */
    public boolean adopt(Circuit c, String id) {
        long n = number(id, 'c');
        if (n <= 0 || ids.containsKey(c) || objects.containsKey(id)) {
            return false;
        }
        NEXT_CIRCUIT.accumulateAndGet(n, Math::max);
        put(c, id);
        return true;
    }

    /** 이미 준 id(없으면 null). */
    public String peek(Object o) {
        return ids.get(o);
    }

    public Object object(String id) {
        return id == null ? null : objects.get(id);
    }

    /** 부품·선 id의 객체. 없거나 다른 종류면 null. */
    public Component component(String id) {
        Object o = object(id);
        return o instanceof Component ? (Component) o : null;
    }

    public Circuit circuit(String id) {
        Object o = object(id);
        return o instanceof Circuit ? (Circuit) o : null;
    }

    /** live에 없는 부품·선의 id를 지운다(회로 id는 둔다). */
    public void retain(Collection<? extends Component> live) {
        Set<Object> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        keep.addAll(live);
        ids.entrySet().removeIf(e -> {
            if (e.getKey() instanceof Component && !keep.contains(e.getKey())) {
                objects.remove(e.getValue());
                return true;
            }
            return false;
        });
    }

    /** 지금 들고 있는 id 수(테스트). */
    public int size() {
        return ids.size();
    }

    private void put(Object o, String id) {
        ids.put(o, id);
        objects.put(id, o);
    }
}
