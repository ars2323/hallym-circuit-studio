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
 */
public final class Ids {
    private static final AtomicLong NEXT_COMPONENT = new AtomicLong();
    private static final AtomicLong NEXT_WIRE = new AtomicLong();
    private static final AtomicLong NEXT_CIRCUIT = new AtomicLong();
    private static final AtomicLong NEXT_FILE = new AtomicLong();

    private final Map<Object, String> ids = new IdentityHashMap<>();
    private final Map<String, Object> objects = new HashMap<>();

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
