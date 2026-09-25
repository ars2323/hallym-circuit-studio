/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.model.Netlist;

/**
 * 기록 엔진(C-01, PLAN.md 5.4). 한 최상위 회로의 시뮬레이션을 스텝(원조 엔진 틱 하나)마다 기록한다. 서브회로
 * 인스턴스 안까지 모든 넷의 값을 <b>바뀐 것만</b> 적는다(어떤 신호가 필요할지 미리 알 수 없다). 한 사이클은 스텝 두
 * 개(클럭 상승과 하강)다.
 * <ul>
 * <li><b>체크포인트:</b> 스텝 0, {@link #CHECKPOINT_EVERY} 스텝마다, 사용자가 입력을 바꾼 스텝마다 회로 상태 전체를
 * 복제해 둔다({@code CircuitState.cloneState}, MIPS 부품의 메모리 내용도 복제된다). 지난 스텝의 회로 상태는 가장
 * 가까운 앞 체크포인트에서 원조 엔진으로 다시 틱해 만든다({@link #reconstruct}). 그래서 "기록 재생 = 실제 재실행"이다.
 * 최근 {@link #DENSE_WINDOW} 스텝 밖의 주기 체크포인트는 {@link #SPARSE_EVERY} 스텝에 하나만 남긴다(입력을 바꾼
 * 스텝의 체크포인트는 남긴다: 그 앞에서 다시 돌리면 바꾼 입력이 빠진다).</li>
 * <li><b>값 공유:</b> 같은 값(예: 반복문의 같은 PC, 같은 명령어)은 한 객체를 함께 쓴다.</li>
 * <li><b>보관 상한:</b> {@link #maxSteps}를 넘으면 오래된 스텝부터 버린다(체크포인트 경계에서).</li>
 * <li>회로 모델만 입력으로 받는다. GUI 없이 테스트한다. 쓰기(캡처)는 시뮬레이터 스레드, 읽기는 GUI에서 하므로 모든
 * 공개 메서드가 이 객체로 동기화된다.</li>
 * </ul>
 */
public final class Recording {
    /** 체크포인트 간격(스텝). */
    public static final int CHECKPOINT_EVERY = 64;
    /** 이 스텝 수보다 오래된 주기 체크포인트는 성기게 남긴다. */
    public static final int DENSE_WINDOW = 2048;
    /** 오래된 주기 체크포인트 간격(스텝). */
    public static final int SPARSE_EVERY = 512;
    /** 기본 보관 상한(스텝, 50,000사이클). docs/PERFORMANCE.md의 측정으로 정했다. */
    public static final int DEFAULT_MAX_STEPS = 100_000;
    /** 값 공유 표의 크기 상한. 넘으면 비우고 다시 모은다. */
    static final int CANON_LIMIT = 1 << 16;

    /** 한 회로의 넷마다 대표 자리 하나와, 넷 위 모든 자리 → 넷 번호. */
    static final class Probe {
        final Location[] at;
        final Map<Location, Integer> index;
        final Component[] subcircuits;

        Probe(Circuit c) {
            Netlist nl = Netlist.of(c);
            List<Location> reps = new ArrayList<>();
            index = new HashMap<>();
            for (Netlist.Net n : nl.nets()) {
                Location rep = null;
                if (!n.ports().isEmpty()) {
                    rep = n.ports().get(0).location();
                } else if (!n.wires().isEmpty()) {
                    rep = n.wires().get(0).getEnd0();
                }
                if (rep == null) {
                    continue;
                }
                int i = reps.size();
                reps.add(rep);
                for (Netlist.PortRef p : n.ports()) {
                    index.putIfAbsent(p.location(), i);
                }
                for (Wire w : n.wires()) {
                    index.putIfAbsent(w.getEnd0(), i);
                    index.putIfAbsent(w.getEnd1(), i);
                }
            }
            at = reps.toArray(new Location[0]);
            List<Component> subs = new ArrayList<>();
            for (Component comp : c.getNonWires()) {
                if (comp.getFactory() instanceof SubcircuitFactory) {
                    subs.add(comp);
                }
            }
            subcircuits = subs.toArray(new Component[0]);
        }
    }

    /** 넷 하나의 값 변화: 스텝 오름차순. */
    static final class Track {
        int[] steps = new int[2];
        Value[] values = new Value[2];
        int n;

        void put(int step, Value v) {
            if (n > 0 && steps[n - 1] == step) {
                // 같은 스텝의 다시 캡처(입력 바꿈): 덮어쓰고, 앞 값과 같아지면 변화를 지운다
                if (n > 1 && values[n - 2].equals(v)) {
                    n--;
                } else {
                    values[n - 1] = v;
                }
                return;
            }
            if (n > 0 && values[n - 1].equals(v)) {
                return;
            }
            if (n == steps.length) {
                steps = java.util.Arrays.copyOf(steps, n * 2);
                values = java.util.Arrays.copyOf(values, n * 2);
            }
            steps[n] = step;
            values[n] = v;
            n++;
        }

        /** step보다 앞 마지막 값. 없으면 null. */
        Value before(int step) {
            for (int i = n - 1; i >= 0; i--) {
                if (steps[i] < step) {
                    return values[i];
                }
            }
            return null;
        }

        /** step 때의 값(그 스텝 이하 마지막 변화). 기록 앞이면 null. */
        Value at(int step) {
            int lo = 0;
            int hi = n - 1;
            int found = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (steps[mid] <= step) {
                    found = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return found < 0 ? null : values[found];
        }

        /** step 이하 변화 스텝 목록(사이클 표가 쓴다). */
        int changesBetween(int from, int to, List<Integer> out) {
            int c = 0;
            for (int i = 0; i < n; i++) {
                if (steps[i] >= from && steps[i] <= to) {
                    out.add(steps[i]);
                    c++;
                }
            }
            return c;
        }

        void truncateAfter(int step) {
            while (n > 0 && steps[n - 1] > step) {
                values[--n] = null;
            }
        }

        /** step 앞 변화를 지우되 step 때의 값은 남긴다. */
        void trimBefore(int step) {
            int keep = 0;
            while (keep + 1 < n && steps[keep + 1] <= step) {
                keep++;
            }
            if (keep == 0) {
                return;
            }
            System.arraycopy(steps, keep, steps, 0, n - keep);
            System.arraycopy(values, keep, values, 0, n - keep);
            for (int i = n - keep; i < n; i++) {
                values[i] = null;
            }
            n -= keep;
        }
    }

    /** 회로 상태 하나(최상위 또는 서브회로 인스턴스)의 기록. */
    static final class Node {
        Circuit circuit;
        Probe probe;
        Track[] tracks = new Track[0];
        final Map<Component, Node> children = new LinkedHashMap<>();
    }

    private final Circuit circuit;
    private final int maxSteps;
    private final Map<Circuit, Probe> probes = new HashMap<>();
    private final TreeMap<Integer, CircuitState> checkpoints = new TreeMap<>();
    /** 넷 하나라도 E(오류)가 되거나 정해져 있다가 X가 된 스텝(C-04 Run Until, D-03). */
    private final java.util.TreeSet<Integer> problems = new java.util.TreeSet<>();
    /** 입력을 바꾼 스텝의 체크포인트(성기게 할 때도 남긴다). */
    private final java.util.Set<Integer> pinned = new java.util.HashSet<>();
    private final Map<Value, Value> canon = new HashMap<>();
    private final int checkpointEvery;
    private int thinnedUpTo;
    private Node root = new Node();
    private int first;
    private int last = -1;
    /** 보고 있는 스텝(C-03). 보통 last이고, 지난 스텝을 보면 그보다 앞이다. */
    private int cursor = -1;
    /** 지난 스텝을 보는 동안 떼어 둔 지금 상태. 지금으로 돌아오면 이것을 다시 쓴다. */
    private CircuitState live;

    public Recording(Circuit circuit) {
        this(circuit, DEFAULT_MAX_STEPS);
    }

    public Recording(Circuit circuit, int maxSteps) {
        this(circuit, maxSteps, CHECKPOINT_EVERY);
    }

    /** 테스트·측정: 체크포인트 간격을 바꾼다. */
    Recording(Circuit circuit, int maxSteps, int checkpointEvery) {
        this.circuit = circuit;
        this.checkpointEvery = checkpointEvery;
        this.maxSteps = Math.max(2 * CHECKPOINT_EVERY, maxSteps);
    }

    public Circuit circuit() {
        return circuit;
    }

    /** 남아 있는 가장 오래된 스텝. */
    public synchronized int first() {
        return first;
    }

    /** 가장 최근에 캡처한 스텝. 아직 없으면 -1. */
    public synchronized int last() {
        return last;
    }

    /** 보고 있는 스텝. 기록이 없으면 -1. */
    public synchronized int cursor() {
        return cursor;
    }

    /** 지난 스텝을 보고 있는가(그 뒤 기록이 있다). */
    public synchronized boolean isViewingPast() {
        return cursor >= 0 && cursor < last;
    }

    /** Recorder: 보는 스텝을 옮긴다(범위 안으로). */
    synchronized int moveCursor(int step) {
        cursor = Math.max(first, Math.min(last, step));
        return cursor;
    }

    synchronized CircuitState live() {
        return live;
    }

    synchronized void setLive(CircuitState s) {
        live = s;
    }

    public synchronized boolean isEmpty() {
        return last < 0;
    }

    public int maxSteps() {
        return maxSteps;
    }

    /** 기록을 비우고 step부터 새로 시작한다(리셋, 회로 편집). */
    public synchronized void restart(CircuitState state, int step) {
        root = new Node();
        probes.clear();
        checkpoints.clear();
        pinned.clear();
        problems.clear();
        canon.clear();
        thinnedUpTo = step;
        first = step;
        last = -1;
        live = null;
        capture(state, step, true);
    }

    /**
     * state(최상위 회로 상태)의 지금 값을 step으로 적는다. 같은 스텝을 다시 적으면(입력 바꿈) 덮어쓴다. step이
     * 마지막보다 앞이면 그 뒤 기록을 먼저 버린다. checkpoint면 상태를 복제해 둔다.
     */
    public synchronized void capture(CircuitState state, int step, boolean checkpoint) {
        if (last >= 0 && step < last) {
            truncateAfter(step);
        }
        captureNode(root, state, step);
        last = Math.max(last, step);
        cursor = step;
        Integer prev = checkpoints.isEmpty() ? null : checkpoints.lastKey();
        if (checkpoint || prev == null || step - prev >= checkpointEvery) {
            checkpoints.put(step, state.cloneState());
            if (checkpoint) {
                pinned.add(step);
            }
            thin();
        }
        if (last - first > maxSteps + maxSteps / 4) {
            trimTo(last - maxSteps);
            cursor = Math.max(cursor, first);
        }
    }

    /** 최근 창 밖의 주기 체크포인트를 SPARSE_EVERY 칸마다 하나로 줄인다(맨 앞과 입력 바꿈은 남긴다). */
    private void thin() {
        int upTo = last - DENSE_WINDOW;
        if (upTo <= thinnedUpTo) {
            return;
        }
        int keptBucket = Integer.MIN_VALUE;
        Integer before = checkpoints.lowerKey(thinnedUpTo);
        if (before != null) {
            keptBucket = Math.floorDiv(before, SPARSE_EVERY);
        }
        java.util.Iterator<Integer> it = checkpoints.subMap(thinnedUpTo, true, upTo, false).keySet().iterator();
        while (it.hasNext()) {
            int k = it.next();
            int bucket = Math.floorDiv(k, SPARSE_EVERY);
            if (k == checkpoints.firstKey() || pinned.contains(k) || bucket != keptBucket) {
                keptBucket = bucket;
                continue;
            }
            it.remove();
        }
        thinnedUpTo = upTo;
    }

    private Value canonical(Value v) {
        Value c = canon.get(v);
        if (c != null) {
            return c;
        }
        if (canon.size() >= CANON_LIMIT) {
            canon.clear();
        }
        canon.put(v, v);
        return v;
    }

    private Probe probe(Circuit c) {
        Probe p = probes.get(c);
        if (p == null) {
            p = new Probe(c);
            probes.put(c, p);
        }
        return p;
    }

    private void captureNode(Node node, CircuitState s, int step) {
        Circuit c = s.getCircuit();
        if (node.circuit != c || node.probe == null) {
            node.circuit = c;
            node.probe = probe(c);
            node.tracks = new Track[node.probe.at.length];
            for (int i = 0; i < node.tracks.length; i++) {
                node.tracks[i] = new Track();
            }
            node.children.clear();
        }
        Location[] at = node.probe.at;
        for (int i = 0; i < at.length; i++) {
            Value v = canonical(s.getValue(at[i]));
            Track t = node.tracks[i];
            t.put(step, v);
            if (isProblem(t.before(step), v)) {
                problems.add(step);
            }
        }
        for (Component comp : node.probe.subcircuits) {
            Object d = s.getData(comp);
            if (d instanceof CircuitState) {
                Node child = node.children.get(comp);
                if (child == null) {
                    child = new Node();
                    node.children.put(comp, child);
                }
                captureNode(child, (CircuitState) d, step);
            }
        }
    }

    /** 넷 값이 before에서 v로 바뀐 것이 E·X 발생인가: E가 되었거나, 모든 비트가 정해져 있다가 X 비트가 생겼다. */
    static boolean isProblem(Value before, Value v) {
        if (v == null || before != null && before.equals(v)) {
            return false;
        }
        if (v.isErrorValue()) {
            return true;
        }
        return !v.isFullyDefined() && before != null && before.isFullyDefined();
    }

    /** from~to 사이에서 E·X가 생긴 스텝들. */
    public synchronized java.util.SortedSet<Integer> problemSteps(int from, int to) {
        return new java.util.TreeSet<>(problems.subSet(from, true, to, true));
    }

    /** 테스트·진단: state와 step 기록이 처음 다른 넷(경로와 자리). 같으면 null. */
    public synchronized String firstDifference(CircuitState state, int step) {
        return firstDifference(root, state, step, "");
    }

    private String firstDifference(Node node, CircuitState s, int step, String path) {
        if (node.probe == null || node.circuit != s.getCircuit()) {
            return path + " (structure)";
        }
        for (int i = 0; i < node.probe.at.length; i++) {
            Value v = node.tracks[i].at(step);
            Value now = s.getValue(node.probe.at[i]);
            if (v == null || !v.equals(now)) {
                return path + " " + node.probe.at[i] + ": recorded " + v + ", now " + now;
            }
        }
        for (Map.Entry<Component, Node> e : node.children.entrySet()) {
            Object d = s.getData(e.getKey());
            if (!(d instanceof CircuitState)) {
                return path + "/" + e.getKey() + " (no state)";
            }
            String f = firstDifference(e.getValue(), (CircuitState) d, step, path + "/" + e.getKey());
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    /** state의 값이 step 기록과 하나라도 다른가(값이 바뀌지 않은 전파 알림을 걸러낸다). */
    public synchronized boolean differs(CircuitState state, int step) {
        return differs(root, state, step);
    }

    private boolean differs(Node node, CircuitState s, int step) {
        if (node.probe == null || node.circuit != s.getCircuit()) {
            return true;
        }
        Location[] at = node.probe.at;
        for (int i = 0; i < at.length; i++) {
            Value v = node.tracks[i].at(step);
            if (v == null || !v.equals(s.getValue(at[i]))) {
                return true;
            }
        }
        for (Map.Entry<Component, Node> e : node.children.entrySet()) {
            Object d = s.getData(e.getKey());
            if (!(d instanceof CircuitState) || differs(e.getValue(), (CircuitState) d, step)) {
                return true;
            }
        }
        return false;
    }

    /** step 뒤 기록을 버린다(지난 스텝에서 입력·회로를 바꿨을 때). */
    public synchronized void truncateAfter(int step) {
        if (last <= step) {
            return;
        }
        truncateNode(root, step);
        checkpoints.tailMap(step, false).clear();
        pinned.removeIf(k -> k > step);
        problems.tailSet(step, false).clear();
        thinnedUpTo = Math.min(thinnedUpTo, step);
        last = step;
        cursor = Math.min(cursor, step);
        live = null; // 지금이 바뀌었다: 떼어 둔 옛 지금은 버린다
    }

    private static void truncateNode(Node node, int step) {
        for (Track t : node.tracks) {
            t.truncateAfter(step);
        }
        for (Node c : node.children.values()) {
            truncateNode(c, step);
        }
    }

    /** 보관 상한: newFirst 앞(그 앞 가장 가까운 체크포인트까지는 남긴다)을 버린다. */
    private void trimTo(int newFirst) {
        Integer cp = checkpoints.floorKey(newFirst);
        if (cp == null || cp <= first) {
            return;
        }
        checkpoints.headMap(cp, false).clear();
        pinned.removeIf(k -> k < cp);
        problems.headSet(cp, false).clear();
        trimNode(root, cp);
        first = cp;
    }

    private static void trimNode(Node node, int step) {
        for (Track t : node.tracks) {
            t.trimBefore(step);
        }
        for (Node c : node.children.values()) {
            trimNode(c, step);
        }
    }

    private Node node(List<Component> path) {
        Node n = root;
        for (Component c : path) {
            n = n.children.get(c);
            if (n == null) {
                return null;
            }
        }
        return n;
    }

    /**
     * path(최상위에서 서브회로 인스턴스들을 따라간 경로, 최상위면 빈 목록)의 자리 at이 속한 넷의 step 때 값. 기록에
     * 없으면(범위 밖, 넷이 아닌 자리) null.
     */
    public synchronized Value value(List<Component> path, Location at, int step) {
        if (step < first || step > last) {
            return null;
        }
        Node n = node(path);
        if (n == null || n.probe == null) {
            return null;
        }
        Integer i = n.probe.index.get(at);
        return i == null ? null : n.tracks[i].at(step);
    }

    /** 최상위 회로의 자리 at의 값. */
    public Value value(Location at, int step) {
        return value(Collections.<Component>emptyList(), at, step);
    }

    /** 그 넷의 값이 바뀐 스텝들(from~to). 사이클 표의 파형이 쓴다. */
    public synchronized List<Integer> changes(List<Component> path, Location at, int from, int to) {
        List<Integer> out = new ArrayList<>();
        Node n = node(path);
        Integer i = n == null || n.probe == null ? null : n.probe.index.get(at);
        if (i != null) {
            n.tracks[i].changesBetween(from, to, out);
        }
        return out;
    }

    /** 그 인스턴스에서 기록하는 넷의 대표 자리들(테스트: 복원한 상태와 넷마다 비교한다). */
    synchronized List<Location> points(List<Component> path) {
        Node n = node(path);
        return n == null || n.probe == null ? Collections.<Location>emptyList()
                : java.util.Arrays.asList(n.probe.at);
    }

    /** 기록된 인스턴스 경로들(최상위 = 빈 목록). */
    public synchronized List<List<Component>> paths() {
        List<List<Component>> out = new ArrayList<>();
        collect(root, new ArrayList<Component>(), out);
        return out;
    }

    private static void collect(Node n, List<Component> path, List<List<Component>> out) {
        out.add(new ArrayList<>(path));
        for (Map.Entry<Component, Node> e : n.children.entrySet()) {
            path.add(e.getKey());
            collect(e.getValue(), path, out);
            path.remove(path.size() - 1);
        }
    }

    /** 재실행 스레드 이름 앞부분. lib-mips(MemoryRegistry)가 이 이름으로 재실행을 알아보고 등록을 따로 둔다. */
    public static final String REPLAY_THREAD_PREFIX = "hcs-replay";
    private static final java.util.concurrent.atomic.AtomicInteger REPLAYS = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * step 때의 회로 상태를 새로 만든다: 가장 가까운 앞 체크포인트를 복제하고 원조 엔진으로 틱·전파를 되풀이한다.
     * 기록 범위 밖이면 null.
     * <p>복제본은 실제 상태와 같은 프로젝트를 가리키므로, 프로젝트 전체에 무엇을 두는 부품(MIPS 메모리 등록, Console의
     * 클럭 멈춤)이 실제 시뮬레이션을 건드리지 않게 이름이 {@link #REPLAY_THREAD_PREFIX}로 시작하는 새 스레드에서
     * 돌린다. 틱하기 전에 모든 부품을 한 번 다시 전파해(값은 그대로) 메모리 부품이 재실행 쪽에 등록되게 한다.
     */
    public CircuitState reconstruct(int step) {
        CircuitState base;
        int from;
        synchronized (this) {
            if (step < first || step > last) {
                return null;
            }
            Map.Entry<Integer, CircuitState> cp = checkpoints.floorEntry(step);
            if (cp == null) {
                return null;
            }
            base = cp.getValue().cloneState();
            from = cp.getKey();
        }
        final CircuitState state = base;
        final int start = from;
        Throwable[] failed = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                prime(state);
                Propagator p = state.getPropagator();
                p.propagate();
                for (int s = start; s < step; s++) {
                    p.tick();
                    p.propagate();
                }
            } catch (Throwable e) {
                failed[0] = e;
            }
        }, REPLAY_THREAD_PREFIX + "-" + REPLAYS.incrementAndGet());
        t.setDaemon(true);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        if (failed[0] != null) {
            throw new IllegalStateException("replay to step " + step + " failed", failed[0]);
        }
        return base;
    }

    /**
     * 상태 나무의 모든 부품을 다시 전파할 것으로 표시한다(전파는 부르는 쪽이). 값은 바뀌지 않고, 스스로 등록하는
     * 부품(MIPS 메모리)이 이 상태로 다시 등록한다. 지난 상태를 실제 시뮬레이션에 바꿔 끼운 뒤에도 쓴다(C-03).
     */
    public static void prime(CircuitState state) {
        state.markComponentsDirty(state.getCircuit().getNonWires());
        for (CircuitState sub : state.getSubstates()) {
            prime(sub);
        }
    }

    public synchronized int checkpointCount() {
        return checkpoints.size();
    }

    /** 기록된 넷 변화 수(모든 인스턴스). 성능 측정에 쓴다. */
    public synchronized long changeCount() {
        return count(root);
    }

    private static long count(Node n) {
        long c = 0;
        for (Track t : n.tracks) {
            c += t.n;
        }
        for (Node k : n.children.values()) {
            c += count(k);
        }
        return c;
    }

    /** 기록하는 넷 수(모든 인스턴스). */
    public synchronized int netCount() {
        return nets(root);
    }

    private static int nets(Node n) {
        int c = n.tracks.length;
        for (Node k : n.children.values()) {
            c += nets(k);
        }
        return c;
    }
}
