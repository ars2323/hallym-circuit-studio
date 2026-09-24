/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 선택한 순서(#105 "고른 순서대로 합치기"). 원조 Selection은 순서를 모르므로, 선택이 바뀔 때마다 빠진 것은 지우고
 * 새로 들어온 것을 뒤에 붙인다. Shift+클릭으로 하나씩 고르면 고른 순서가 된다. 사각형 선택은 원조가 부품마다
 * 선택 이벤트를 따로 보내므로, 같은 입력(마우스 한 번, batch)으로 둘 이상이 들어오면 순서를 모르는 것으로 본다.
 *
 * @param <T> 선택되는 것(부품)
 */
public final class SelectionOrder<T> {
    private final List<T> order = new ArrayList<>();
    /** 한 번에 여럿이 들어와(사각형 선택 등) 순서를 알 수 없는 것. */
    private final List<T> unordered = new ArrayList<>();

    private Object lastBatch;
    private final List<T> addedInLastBatch = new ArrayList<>();

    /** 지금 선택된 것들로 순서를 갱신한다(입력 구분 없음: 한 번에 둘 이상 들어오면 순서를 모름). */
    public void update(Collection<? extends T> now) {
        update(now, new Object());
    }

    /**
     * batch는 선택을 바꾼 입력(예: 지금 처리 중인 AWT 이벤트). 같은 batch로 들어온 것이 둘 이상이면 그들의 순서는
     * 학생이 정한 것이 아니다.
     */
    public synchronized void update(Collection<? extends T> now, Object batch) {
        order.removeIf(t -> !contains(now, t));
        unordered.removeIf(t -> !contains(now, t));
        addedInLastBatch.removeIf(t -> !contains(now, t));
        List<T> added = new ArrayList<>();
        for (T t : now) {
            if (!containsIdentity(order, t)) {
                added.add(t);
            }
        }
        if (added.isEmpty()) {
            return;
        }
        order.addAll(added);
        if (batch == null || batch != lastBatch) {
            lastBatch = batch;
            addedInLastBatch.clear();
        }
        addedInLastBatch.addAll(added);
        if (addedInLastBatch.size() > 1) {
            for (T t : addedInLastBatch) {
                if (!containsIdentity(unordered, t)) {
                    unordered.add(t);
                }
            }
        }
    }

    /** 지금 선택의 순서를 아는가(모두 하나씩 골랐는가). */
    public synchronized boolean known() {
        return unordered.isEmpty();
    }

    public synchronized List<T> order() {
        return Collections.unmodifiableList(new ArrayList<>(order));
    }

    private static boolean contains(Collection<?> c, Object t) {
        for (Object o : c) {
            if (o == t) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIdentity(List<?> c, Object t) {
        return contains(c, t);
    }
}
