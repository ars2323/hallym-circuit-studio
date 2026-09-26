/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LogisimFile;

import kr.ac.hallym.hcs.app.model.InstancePaths;
import kr.ac.hallym.hcs.app.model.Names;

/**
 * 레지스터 패널과 메모리 패널의 모델(C-05, C-06, PLAN.md 5.1). 레지스터 값은 기록에서 그 사이클 값을, 메모리
 * 내용은 지금 보고 있는 회로 상태(지난 사이클이면 다시 만든 상태)에서 읽는다. 값은 보이기만 하고 판단하지 않는다.
 * GUI 없이 테스트한다.
 */
public final class MachineState {
    /** SPIM(QtSpim)이 프로그램 시작 때 두는 $sp. 스택 깊이는 여기서 뺀 값이다(PLAN.md 5.1, D-050). */
    public static final long SPIM_INITIAL_SP = 0x7FFFEFFCL;

    /** Hallym MIPS 레지스터 창과 같은 역할 묶음(이름 → 번호). */
    public static final String[][] GROUPS = {
        {"Return values", "2", "3"},
        {"Arguments", "4", "5", "6", "7"},
        {"Temporaries", "8", "9", "10", "11", "12", "13", "14", "15", "24", "25"},
        {"Saved", "16", "17", "18", "19", "20", "21", "22", "23"},
        {"Pointers", "28", "29", "30", "31"},
        {"Reserved", "0", "1", "26", "27"},
    };

    /** 레지스터 한 줄. */
    public static final class Reg {
        /** $t0처럼 번호가 있으면 그 번호, 표시 없이 나열한 레지스터는 -1. */
        public final int number;
        public final String name;
        /** 그 사이클 값. 대응할 부품이 없으면 null. */
        public final Value value;
        /** 앞 사이클과 다르다. */
        public final boolean changed;
        /** 역할 이름으로 보일 때(PC로 판별한 레지스터) 원래 이름. 없으면 null. */
        public final String alias;

        Reg(int number, String name, Value value, boolean changed) {
            this(number, name, value, changed, null);
        }

        Reg(int number, String name, Value value, boolean changed, String alias) {
            this.number = number;
            this.name = name;
            this.value = value;
            this.changed = changed;
            this.alias = alias;
        }
    }

    private final CycleModel model;
    private final LogisimFile file;

    public MachineState(CycleModel model, LogisimFile file) {
        this.model = model;
        this.file = file;
    }

    public CycleModel model() {
        return model;
    }

    /** 레지스터 파일로 표시한 회로가 있고 최상위에서 쓰이는가. */
    public boolean hasRegisterFile() {
        Circuit rf = RegisterFile.marked(file);
        return rf != null && RegisterFile.pathTo(root(), rf) != null;
    }

    private Circuit root() {
        return model.recording().circuit();
    }

    /**
     * 사이클 c의 레지스터들. 레지스터 파일을 표시했으면 $0~$31(번호 순), 아니면 모든 레지스터 부품을 인스턴스
     * 경로별로(라벨이 $5, t0 같으면 번호도).
     */
    public List<Reg> registers(int cycle) {
        List<Reg> out = new ArrayList<>();
        int step = CycleModel.stepOf(cycle);
        int prev = CycleModel.stepOf(cycle - 1);
        boolean hasPrev = cycle > model.firstCycle();
        Circuit rf = RegisterFile.marked(file);
        List<Component> path = rf == null ? null : RegisterFile.pathTo(root(), rf);
        if (path != null) {
            Map<Integer, Component> map = RegisterFile.mapping(file, rf);
            List<Component> inside = new ArrayList<>(path);
            for (int n = 0; n < 32; n++) {
                Component r = map.get(n);
                Value v = r == null ? null : model.recording().value(inside, r.getEnd(0).getLocation(), step);
                Value p = r == null || !hasPrev ? null
                        : model.recording().value(inside, r.getEnd(0).getLocation(), prev);
                out.add(new Reg(n, MipsText.REG[n], v, p != null && v != null && !p.equals(v)));
            }
            return out;
        }
        // 상태 표시줄과 같은 PC 판별(D-103; 터널이면 그 넷을 내는 레지스터): 최상위의 그 레지스터는 "PC"로 보이고
        // 원래 이름은 곁에 흐리게(X-04)
        Component pc = kr.ac.hallym.hcs.app.sim.StatusModel.pcRegister(file, root());
        for (RegisterFile.Found f : RegisterFile.all(root())) {
            Value v = model.recording().value(f.path, f.register.getEnd(0).getLocation(), step);
            Value p = hasPrev ? model.recording().value(f.path, f.register.getEnd(0).getLocation(), prev) : null;
            boolean isPc = pc != null && f.path.isEmpty() && f.register == pc;
            out.add(new Reg(RegisterFile.numberOf(Names.label(f.register)), isPc ? "PC" : f.name, v,
                    p != null && v != null && !p.equals(v), isPc && !f.name.equalsIgnoreCase("PC") ? f.name : null));
        }
        return out;
    }

    /** 나열한 레지스터에 번호가 하나도 없다(라벨이 $n·Rn 꼴이 아니다): "표시하지 않아 모두 나열" 안내를 보일 때(X-04). */
    public static boolean unmapped(List<Reg> regs) {
        for (Reg r : regs) {
            if (r.number >= 0) {
                return false;
            }
        }
        return true;
    }

    /** 사이클 c의 $sp($29). 레지스터 파일 표시나 라벨로 찾지 못하면 null. */
    public Value sp(int cycle) {
        for (Reg r : registers(cycle)) {
            if (r.number == 29) {
                return r.value;
            }
        }
        return null;
    }

    /** 깊이를 재는 $sp 범위: SPIM 시작 $sp 아래 Stack 기본 한계(1MB). 그 밖(아직 0인 $sp 등)은 깊이가 없다. */
    static final long STACK_LIMIT = 0x00100000L;

    /**
     * 스택 깊이(바이트): SPIM 시작 $sp에서 지금 $sp를 뺀 값. $sp가 없거나 정해지지 않았거나, 시작 $sp 아래 1MB 밖이면
     * (프로그램이 아직 $sp를 두지 않았으면) -1.
     */
    public static long depth(Value sp) {
        if (sp == null || !sp.isFullyDefined()) {
            return -1;
        }
        long v = sp.toIntValue() & 0xffffffffL;
        return v <= SPIM_INITIAL_SP && SPIM_INITIAL_SP - v <= STACK_LIMIT ? SPIM_INITIAL_SP - v : -1;
    }

    // ---- 메모리(C-06) ----

    /** 메모리 한 줄: 워드 하나, 또는 건너뛴 0 구간. */
    public static final class Word {
        public final long addr;
        public final int value;
        public final boolean defined;
        /** 이 주소의 .data 라벨. 없으면 null. */
        public final String label;
        /** 건너뛴 0 워드 수(이 줄이 구간 표시이면 1 이상). */
        public final int skipped;
        /** $sp가 가리키는 워드. */
        public final boolean sp;

        Word(long addr, int value, boolean defined, String label, int skipped, boolean sp) {
            this.addr = addr;
            this.value = value;
            this.defined = defined;
            this.label = label;
            this.skipped = skipped;
            this.sp = sp;
        }
    }

    /** 메모리 부품 하나의 내용. */
    public static final class Memory {
        public final String name;
        public final boolean stack;
        public final List<Word> words;
        /** Stack: 지금 깊이(바이트, $sp 기준, 모르면 -1)와 최고 수위(바이트, 접근이 없으면 0). */
        public final long depth;
        public final long peak;

        Memory(String name, boolean stack, List<Word> words, long depth, long peak) {
            this.name = name;
            this.stack = stack;
            this.words = words;
            this.depth = depth;
            this.peak = peak;
        }
    }

    static final int MAX_WORDS = 4096;

    /**
     * 지금 회로 상태(rootState, 보고 있는 사이클)의 Data Memory·Stack 내용. Data는 낮은 주소부터(.data 라벨과 함께, 0
     * 구간은 한 줄로 접는다), Stack은 높은 주소가 위이고 $sp 화살표·깊이·최고 수위가 있다.
     */
    public List<Memory> memories(CircuitState rootState, int cycle) {
        List<Memory> out = new ArrayList<>();
        Value sp = sp(cycle);
        long spAddr = sp != null && sp.isFullyDefined() ? sp.toIntValue() & 0xffffffffL : -1;
        collect(root(), rootState, new ArrayList<Component>(), out, spAddr);
        return out;
    }

    private void collect(Circuit c, CircuitState s, List<Component> path, List<Memory> out, long spAddr) {
        if (s == null) {
            return;
        }
        for (Component x : c.getNonWires()) {
            String f = x.getFactory().getName();
            if (f.equals("Data Memory") || f.equals("Stack")) {
                MipsMemory m = MipsMemory.of(s.getData(x));
                if (m != null) {
                    String label = Names.label(x);
                    String name = label != null ? label : f;
                    if (!path.isEmpty()) {
                        name = InstancePaths.describe(root(), path).substring(root().getName().length()
                                + Names.SEP.length()) + Names.SEP + name;
                    }
                    out.add(m.growsDown() ? stack(name, m, spAddr) : data(name, m));
                }
            } else if (x.getFactory() instanceof SubcircuitFactory) {
                Object d = s.getData(x);
                path.add(x);
                collect(((SubcircuitFactory) x.getFactory()).getSubcircuit(),
                        d instanceof CircuitState ? (CircuitState) d : null, path, out, spAddr);
                path.remove(path.size() - 1);
            }
        }
    }

    private Memory data(String name, MipsMemory m) {
        long[] region = m.region();
        Map<Integer, String> labels = model.source().labels();
        List<Word> words = new ArrayList<>();
        int zeros = 0;
        long zeroFrom = -1;
        for (long page : m.pageAddresses()) {
            for (long a = Math.max(page, region[0]); a < Math.min(page + 4096, region[1]); a += 4) {
                int v = m.readWord((int) a);
                boolean def = m.isDefined((int) a);
                String label = labels.get((int) a);
                if (v == 0 && def && label == null) {
                    if (zeros == 0) {
                        zeroFrom = a;
                    }
                    zeros++;
                    continue;
                }
                if (zeros > 0) {
                    words.add(new Word(zeroFrom, 0, true, null, zeros, false));
                    zeros = 0;
                }
                words.add(new Word(a, v, def, label, 0, false));
                if (words.size() >= MAX_WORDS) {
                    return new Memory(name, false, words, -1, 0);
                }
            }
        }
        return new Memory(name, false, words, -1, 0);
    }

    private Memory stack(String name, MipsMemory m, long spAddr) {
        long base = m.depthBase();
        long low = m.lowestAccess();
        List<Word> words = new ArrayList<>();
        long peak = low < 0 ? 0 : base - low;
        long bottom = low < 0 ? base : low;
        if (spAddr >= 0 && spAddr < bottom && spAddr <= base && base - spAddr <= 4L * MAX_WORDS) {
            bottom = spAddr & ~3L;
        }
        for (long a = base - 4; a >= bottom && words.size() < MAX_WORDS; a -= 4) {
            words.add(new Word(a, m.readWord((int) a), m.isDefined((int) a), null, 0, a == spAddr));
        }
        if (spAddr == base) {
            words.add(0, new Word(base, m.readWord((int) base), m.isDefined((int) base), null, 0, true));
        }
        long depth = spAddr >= 0 && spAddr <= base && base - spAddr <= STACK_LIMIT ? base - spAddr : -1;
        return new Memory(name, true, Collections.unmodifiableList(words), depth, peak);
    }
}
