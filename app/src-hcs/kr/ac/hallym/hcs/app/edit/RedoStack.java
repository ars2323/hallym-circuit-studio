/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.edit;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;

import kr.ac.hallym.hcs.app.Messages;

/**
 * 다시 실행(redo). 원조 2.7.1에는 되돌리기만 있다. 엔진과 원조 되돌리기 기록({@code Project}의 undoLog)은 그대로 두고,
 * 프로젝트 사건만 듣는다. 되돌린 동작(UNDO_COMPLETE)을 쌓았다가 다시 실행하면 원조 {@link Project#doAction}으로
 * 다시 적용해 원조 되돌리기 기록에도 다시 오른다. 되돌린 뒤 새로 고치면(수정인 Action의 ACTION_COMPLETE) 쌓인
 * 기록을 비운다. 파일이 바뀌어도 비운다. 선택만 바꾸는 동작(수정 아님)은 비우지 않는다.
 */
public final class RedoStack implements ProjectListener {
    /** 원조 되돌리기 기록의 최대 길이와 같게. */
    static final int MAX = 64;

    private static final Map<Project, RedoStack> ALL = new WeakHashMap<>();

    /** 다시 적용할 동작과 그때의 시뮬레이션 상태(원조 되돌리기처럼 그 회로로 돌아간다). */
    private static final class Entry {
        final Action action;
        final CircuitState state;

        Entry(Action action, CircuitState state) {
            this.action = action;
            this.state = state;
        }
    }

    /**
     * 다시 실행한 동작. 앞 동작에 합쳐지지 않게(원조 shouldAppendTo) 감싼다. 되돌리면 다시 이 스택에 쌓인다.
     */
    static final class Redone extends Action {
        final Action inner;

        Redone(Action inner) {
            this.inner = inner;
        }

        @Override
        public boolean isModification() {
            return inner.isModification();
        }

        @Override
        public String getName() {
            return inner.getName();
        }

        @Override
        public void doIt(Project proj) {
            inner.doIt(proj);
        }

        @Override
        public void undo(Project proj) {
            inner.undo(proj);
        }
    }

    private final WeakReference<Project> project;
    private final Deque<Entry> stack = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean redoing;

    private RedoStack(Project proj) {
        this.project = new WeakReference<>(proj);
        proj.addProjectListener(this); // 원조 목록은 약한 참조: ALL이 붙들어 둔다
    }

    /** 프로젝트의 다시 실행 기록(처음 부르면 만든다). */
    public static synchronized RedoStack of(Project proj) {
        return ALL.computeIfAbsent(proj, RedoStack::new);
    }

    @Override
    public void projectChanged(ProjectEvent e) {
        switch (e.getAction()) {
        case ProjectEvent.UNDO_COMPLETE:
            if (e.getData() instanceof Action) {
                Action a = (Action) e.getData();
                stack.push(new Entry(a instanceof Redone ? ((Redone) a).inner : a, project.get() == null ? null
                        : project.get().getCircuitState()));
                while (stack.size() > MAX) {
                    stack.removeLast();
                }
                fire();
            }
            break;
        case ProjectEvent.ACTION_COMPLETE:
            if (!redoing && e.getData() instanceof Action && ((Action) e.getData()).isModification()
                    && !stack.isEmpty()) {
                stack.clear();
                fire();
            }
            break;
        case ProjectEvent.ACTION_SET_FILE:
            if (!stack.isEmpty()) {
                stack.clear();
                fire();
            }
            break;
        default:
            break;
        }
    }

    public boolean canRedo() {
        return !stack.isEmpty();
    }

    /** 다시 실행할 동작의 이름(원조 Action 이름). 없으면 null. */
    public String nextName() {
        return stack.isEmpty() ? null : stack.peek().action.getName();
    }

    /** 가장 최근에 되돌린 동작을 다시 적용한다. */
    public void redo() {
        Project proj = project.get();
        if (proj == null || stack.isEmpty()) {
            return;
        }
        Entry en = stack.pop();
        if (en.state != null && en.state != proj.getCircuitState()) {
            proj.setCircuitState(en.state);
        }
        redoing = true;
        try {
            proj.doAction(new Redone(en.action));
        } finally {
            redoing = false;
        }
        fire();
    }

    /** 기록이 바뀔 때 부른다(메뉴·도구 모음 단추 갱신). */
    public void addListener(Runnable r) {
        listeners.add(r);
    }

    private void fire() {
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }

    /** 편집 메뉴의 "다시 실행" 항목(Ctrl+Y). */
    public static JMenuItem menuItem(Project proj) {
        JMenuItem item = new JMenuItem();
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu));
        if (proj == null) {
            item.setText(Messages.get("redo.none"));
            item.setEnabled(false);
            return item;
        }
        RedoStack r = of(proj);
        Runnable update = () -> {
            String name = r.nextName();
            item.setText(name == null ? Messages.get("redo.none") : Messages.get("redo.item", name));
            item.setEnabled(name != null);
        };
        r.addListener(update);
        update.run();
        item.addActionListener(e -> r.redo());
        return item;
    }

    /** 창에 Ctrl+Shift+Z를 단다(Ctrl+Y는 메뉴 항목의 단축키). */
    public static void installKeys(JComponent root, Project proj) {
        int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | KeyEvent.SHIFT_DOWN_MASK), "hcsRedo");
        root.getActionMap().put("hcsRedo", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                of(proj).redo();
            }
        });
    }
}
