/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.regress;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Simulator;

/**
 * 동시성 테스트가 원조 Logisim 2.7.1 자체의 편집·전파 경합을 가려내게 한다(D-143). 원조 CircuitState의 회로
 * 청취자(편집 스레드: 부품을 넣고 빼고 바꿀 때)와 원조 전파(시뮬레이터 스레드)가 dirtyComponents·dirtyPoints(원조
 * SmallSet, 스레드 안전하지 않음)를 함께 고친다. 둘이 겹치면 전파 쪽에서는 원조 Simulator가 예외를 잡아 System.err에
 * 찍고 시뮬레이션을 끈다(스레드는 살아 있다). 편집 쪽에서는 그 편집이 청취자 안의 예외로 끝난다. 원조 패키지는 고치지
 * 않으므로(CLAUDE.md 2절) 테스트는 이것을 우리 실패로 세지 않는다. 다만 예외가 원조 안에서 났고 우리 코드를 지나지
 * 않았는지는 확인한다. 원조와 엔진은 그 예외를 System.err에만 찍으므로 사본을 모은다.
 */
public final class LogisimRace implements AutoCloseable {
    private final PrintStream before;
    private final Copy copy;
    /** 지난번 {@link #stoppedByLogisim}이 본 원조 전파 예외 수. */
    private int tracesSeen;

    private LogisimRace(PrintStream before) {
        this.before = before;
        this.copy = new Copy(before);
    }

    /** 지금부터 System.err의 사본을 모은다. 닫으면 되돌린다. System.err를 붙잡는 것(엔진 서버의 로그)보다 먼저 부른다. */
    public static LogisimRace watch() {
        LogisimRace r = new LogisimRace(System.err);
        try {
            System.setErr(new PrintStream(r.copy, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    @Override
    public void close() {
        System.setErr(before);
    }

    /**
     * sim이 꺼졌으면 true. 그때는 원조 전파가 예외를 잡아 끈 것이어야 한다: 발진이 아니고, 지난번 확인 뒤로 원조 전파가
     * 새 예외를 찍었고, 찍힌 예외가 우리 코드를 지나지 않았다(아니면 AssertionError). 원조의 {@code
     * isExceptionEncountered}는 보지 않는다: 예외가 난 전파 동안 다른 스레드가 전파를 요청해 두었으면(편집 등) 원조는
     * 꺼진 뒤에도 한 번 더 전파하며 그 표시를 지운다. 다시 켜는 것은 부르는 쪽이 한다(학생이 Simulation Enabled를
     * 다시 켜듯).
     */
    public boolean stoppedByLogisim(Simulator sim) {
        if (sim.isRunning()) {
            return false;
        }
        if (sim.isOscillating()) {
            throw new AssertionError("the simulation stopped because the circuit oscillates");
        }
        List<String> caught = propagatorTraces();
        if (caught.size() <= tracesSeen) {
            throw new AssertionError("the simulation stopped but Logisim's propagator printed no new exception");
        }
        tracesSeen = caught.size();
        for (String t : caught) {
            if (t.contains("kr.ac.hallym.")) {
                throw new AssertionError("Logisim's propagator caught an exception from our code:\n" + t);
            }
        }
        return true;
    }

    /**
     * 엔진이 method 요청의 실패로 찍은 마지막 예외가 원조 CircuitState 청취자 안에서 났으면 true: 던진 곳부터 첫 우리
     * 프레임 앞까지가 원조·JDK 프레임뿐이고 그 가운데 청취자가 있다.
     */
    public boolean editFailedInLogisimListener(String method) {
        String text = copy.text();
        String log = "[hcs-engine] error: " + method + " failed: ";
        int at = text.lastIndexOf(log);
        if (at < 0) {
            return false;
        }
        String[] lines = text.substring(at).split("\r?\n");
        // lines[0]은 엔진 로그 "… failed: <예외>", 그 뒤 printStackTrace의 머리 "<예외>"와 프레임(다른 스레드의 출력이
        // 사이에 끼어도 찾게 머리로 찾는다)
        String header = lines[0].substring(log.length());
        int i = 1;
        while (i < lines.length && !lines[i].equals(header)) {
            i++;
        }
        boolean listener = false;
        int frames = 0;
        for (i++; i < lines.length && lines[i].startsWith("\tat "); i++) {
            String frame = frameClass(lines[i]);
            if (frame.startsWith("kr.ac.hallym.")) {
                break;
            }
            if (!frame.startsWith("com.cburch.logisim.") && !frame.startsWith("java.") && !frame.startsWith("jdk.")) {
                return false;
            }
            frames++;
            listener |= frame.startsWith("com.cburch.logisim.circuit.CircuitState$MyCircuitListener.circuitChanged");
        }
        return frames > 0 && listener;
    }

    /** 원조 Simulator의 전파 스레드가 잡아 찍은 예외들(스택 전부). */
    public List<String> propagatorTraces() {
        List<String> traces = new ArrayList<String>();
        StringBuilder cur = null;
        for (String line : copy.text().split("\r?\n")) {
            boolean frame = line.startsWith("\t") || line.startsWith("Caused by:");
            if (!frame) {
                if (cur != null && cur.indexOf("Simulator$PropagationManager.run") >= 0) {
                    traces.add(cur.toString());
                }
                cur = new StringBuilder();
            }
            if (cur != null) {
                cur.append(line).append('\n');
            }
        }
        if (cur != null && cur.indexOf("Simulator$PropagationManager.run") >= 0) {
            traces.add(cur.toString());
        }
        return traces;
    }

    /** "\tat java.base/java.util.X.y(X.java:1)" → "java.util.X.y(X.java:1)"(모듈·클래스 로더 이름을 뗀다). */
    static String frameClass(String line) {
        String s = line.substring("\tat ".length());
        int paren = s.indexOf('(');
        int slash = s.lastIndexOf('/', paren < 0 ? s.length() : paren);
        return slash < 0 ? s : s.substring(slash + 1);
    }

    /** System.err를 그대로 두고 사본을 모은다. */
    private static final class Copy extends OutputStream {
        private final PrintStream out;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Copy(PrintStream out) {
            this.out = out;
        }

        @Override
        public synchronized void write(int b) {
            out.write(b);
            bytes.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            out.write(b, off, len);
            bytes.write(b, off, len);
        }

        @Override
        public void flush() {
            out.flush();
        }

        synchronized String text() {
            try {
                return bytes.toString("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
