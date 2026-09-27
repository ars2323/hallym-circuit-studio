/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.parity;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Window;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

/**
 * 모달 대화상자에 사람처럼 답한다(N-01). 동작은 {@link SwingUtilities#invokeLater}로 GUI 스레드에서 돌리고, 테스트
 * 스레드가 새로 뜬 모달 창을 찾아 차례대로 준비한 답(칸 채우기, 단추 누르기)을 GUI 스레드에서 실행한다. 모달 창은 GUI
 * 스레드의 이벤트를 계속 돌리므로 답이 그 안에서 실행된다. 준비하지 않은 창이 뜨면 그 글을 모아 실패로 알린다.
 */
final class Dialogs {
    /** 창 하나에 대한 답. GUI 스레드에서 부른다. */
    interface Answer {
        void answer(Dialog d) throws Exception;
    }

    private static final class Step {
        final String what;
        final Answer answer;
        final boolean optional;

        Step(String what, Answer answer, boolean optional) {
            this.what = what;
            this.answer = answer;
            this.optional = optional;
        }
    }

    static final long TIMEOUT_MS = 60_000;

    private final Deque<Step> steps = new ArrayDeque<>();
    /** 답한 창의 제목(실행 기록용). */
    private final List<String> log;

    Dialogs(List<String> log) {
        this.log = log;
    }

    Dialogs expect(String what, Answer a) {
        steps.add(new Step(what, a, false));
        return this;
    }

    /** 뜰 수도 있는 창(예: 끊어질 연결 확인). 뜨지 않으면 넘어간다. */
    Dialogs maybe(String what, Answer a) {
        steps.add(new Step(what, a, true));
        return this;
    }

    /** action을 GUI 스레드에서 돌리며 창에 답한다. action이 끝나면 돌아온다. */
    void run(ThrowingRunnable action) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.set(true);
            }
        });
        Set<Window> handled = Collections.newSetFromMap(new IdentityHashMap<>());
        List<String> unexpected = new ArrayList<>();
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (!done.get()) {
            if (System.currentTimeMillis() > deadline) {
                closeAll();
                throw new AssertionError("timed out; unanswered dialogs: " + unexpected);
            }
            handled.removeIf(w -> !w.isShowing());
            Dialog d = showingModal(handled);
            if (d == null) {
                Thread.sleep(15);
                continue;
            }
            handled.add(d);
            Thread.sleep(30); // 창이 다 그려지고 초점을 받을 틈
            // 선택 창이 안 떴는데 다음 창이 떴으면 선택 창은 건너뛴다
            while (!steps.isEmpty() && steps.peek().optional && !matches(steps.peek(), d)) {
                steps.poll();
            }
            Step s = steps.poll();
            if (s == null) {
                unexpected.add(describe(d));
                SwingUtilities.invokeAndWait(() -> close(d));
                continue;
            }
            AtomicReference<Throwable> err = new AtomicReference<>();
            log.add(d.getTitle());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    s.answer.answer(d);
                } catch (Throwable t) {
                    err.set(t);
                    close(d);
                }
            });
            if (err.get() != null) {
                throw new AssertionError("answering '" + s.what + "' in " + describe(d), err.get());
            }
        }
        SwingUtilities.invokeAndWait(() -> { });
        if (failure.get() != null) {
            Throwable t = failure.get();
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw new AssertionError(t);
        }
        if (!unexpected.isEmpty()) {
            throw new AssertionError("unexpected dialogs: " + unexpected);
        }
        for (Step s : steps) {
            if (!s.optional) {
                throw new AssertionError("expected dialog did not appear: " + s.what);
            }
        }
    }

    /** 선택 창은 이름(제목·글 일부)이 맞을 때만 그 창에 쓴다. 이름이 없으면 늘 맞다. */
    private static boolean matches(Step s, Dialog d) {
        return !s.optional || describe(d).contains(s.what);
    }

    private static Dialog showingModal(Set<Window> handled) {
        for (Window w : Window.getWindows()) {
            if (w instanceof Dialog && w.isShowing() && ((Dialog) w).isModal() && !handled.contains(w)) {
                return (Dialog) w;
            }
        }
        return null;
    }

    private static void closeAll() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof Dialog && w.isShowing() && ((Dialog) w).isModal()) {
                    close((Dialog) w);
                }
            }
        });
    }

    /** 취소 쪽으로 닫는다: JOptionPane이면 값 없이, 아니면 창을 치운다. */
    static void close(Dialog d) {
        JOptionPane pane = first(d, JOptionPane.class);
        if (pane != null) {
            pane.setValue(JOptionPane.CLOSED_OPTION);
        }
        d.setVisible(false);
        d.dispose();
    }

    /** 제목과 보이는 글(오류 설명용). */
    static String describe(Dialog d) {
        StringBuilder b = new StringBuilder("[").append(d.getTitle()).append("]");
        for (JLabel l : all(d, JLabel.class)) {
            if (l.getText() != null && !l.getText().trim().isEmpty()) {
                b.append(' ').append(l.getText().trim());
            }
        }
        for (JTextComponent t : all(d, JTextComponent.class)) {
            if (!t.isEditable() && t.getText() != null) {
                b.append(' ').append(t.getText().trim());
            }
        }
        JOptionPane pane = first(d, JOptionPane.class);
        if (pane != null && pane.getMessage() instanceof String) {
            b.append(' ').append(pane.getMessage());
        }
        return b.toString();
    }

    static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> out = new ArrayList<>();
        collect(root, type, out);
        return out;
    }

    private static <T extends Component> void collect(Container c, Class<T> type, List<T> out) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) {
                out.add(type.cast(child));
            }
            if (child instanceof Container) {
                collect((Container) child, type, out);
            }
        }
    }

    static <T extends Component> T first(Container root, Class<T> type) {
        List<T> l = all(root, type);
        return l.isEmpty() ? null : l.get(0);
    }

    /** 글자가 text인 단추를 누른다. */
    static void click(Container root, String text) {
        for (AbstractButton b : all(root, AbstractButton.class)) {
            if (text.equals(b.getText())) {
                if (!b.isEnabled()) {
                    throw new IllegalStateException("button '" + text + "' is disabled");
                }
                b.doClick(0);
                return;
            }
        }
        List<String> names = new ArrayList<>();
        for (AbstractButton b : all(root, AbstractButton.class)) {
            names.add(b.getText());
        }
        throw new IllegalStateException("no button '" + text + "' among " + names);
    }

    /** JOptionPane의 OK 단추 글자(현재 로캘). */
    static String ok() {
        return UIManager.getString("OptionPane.okButtonText");
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
