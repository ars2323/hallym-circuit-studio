/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.MenuExtender;

/**
 * Data Memory(데이터+스택, PLAN.md 6.2, D-140)와 옛 Stack. 입력 {@code Addr}, {@code WriteData}, {@code MemWrite},
 * {@code MemRead}, {@code clk}, 출력 {@code ReadData}. 모두 32비트이고 제어 입력은 1비트다.
 *
 * <ul>
 *   <li>새로 놓는 Data Memory는 실제 MIPS처럼 데이터 메모리 하나가 두 영역을 맡는다(QtSpim 9.1.24 배치): 데이터
 *       {@code 0x10000000}~{@code 0x100FFFFF}(DATA_BOT, DATA_LIMIT 1MB, 위로 자람)와 스택
 *       {@code 0x7FFC0000}~{@code 0x7FFFFFFF}(STACK_TOP 바로 아래, STACK_LIMIT 256KB, 아래로 자람). 두 영역은 한
 *       메모리다.</li>
 *   <li>속성이 적히지 않은 옛 Data Memory는 v1 그대로 {@code base}(0x10010000)부터 1MB 데이터 영역 하나다. 새로 놓는
 *       값과 저장 기준값이 다른 것은 {@link #getDefaultAttributeValue}에 적었다.</li>
 *   <li>옛 Stack({@link StackMemory})은 {@code top}(0x7FFFFFFC)부터 낮은 주소 쪽으로 {@code size}(기본 1MB)만큼
 *       자란다. 옛 파일을 위해 남긴다.</li>
 *   <li>읽기는 조합이다. {@code MemRead}가 1이고 주소가 영역 안이면 그 워드를 낸다.</li>
 *   <li>쓰기는 {@code clk} 상승 에지에 {@code MemWrite}가 1이고 주소가 영역 안일 때다.</li>
 *   <li>영역 밖이거나 {@code MemRead}가 1이 아니면 출력을 구동하지 않는다. 쓰기도 하지 않는다.</li>
 *   <li>떠 있는 제어 입력은 1로 취급하지 않는다(원조 RAM과 다름).</li>
 *   <li>워드 접근만 한다. 주소의 하위 2비트는 쓰지 않는다.</li>
 *   <li>실행 중 쓴 값은 저장하지 않는다. 리셋하면 {@code contents}(실행 이미지의 .data)로 돌아간다.</li>
 * </ul>
 *
 * <p>동작하지 않는 경우만 알린다(PLAN.md 1장 설계 원칙): 떠 있는 제어 입력, 정렬 안 된 주소, 어느 메모리 영역에도
 * 없는 주소, 스택 한계 초과, 영역 겹침. 부품 안에 빨간 글자로 보이고, {@link State#problem}으로 진단이 읽는다.
 */
class DataMemory extends MemoryFactory {
    static final int ADDR = 0;
    static final int WRITE_DATA = 1;
    static final int MEM_WRITE = 2;
    static final int MEM_READ = 3;
    static final int CLK = 4;
    static final int READ_DATA = 5;

    /** 동작하지 않는 이유. 사실만 말한다. */
    enum Problem {
        CONTROL_FLOATING, UNALIGNED, NOT_IN_ANY_REGION, STACK_LIMIT, OVERLAP
    }

    /** SPIM(QtSpim)이 시작할 때 두는 $sp. D-010에 따라 기계어와 함께 그대로 따른다. */
    static final long SPIM_INITIAL_SP = 0x7FFFEFFCL;

    // 새로 놓는 Data Memory의 두 영역(SPIM 9.1.24 CPU/mem.h·spim.h, docs/mips-components.md, D-140)
    /** DATA_BOT(mem.h). */
    static final int NEW_BASE = 0x10000000;
    /** DATA_LIMIT 1MB(spim.h). */
    static final int NEW_SIZE = 0x00100000;
    /** STACK_TOP 0x80000000(mem.h) 바로 아래 워드. */
    static final int NEW_STACK_TOP = 0x7FFFFFFC;
    /** STACK_LIMIT 256KB(spim.h). */
    static final int NEW_STACK_SIZE = 0x00040000;

    // 저장 기준값: .circ에 속성이 없을 때 읽는 값이자 저장 때 생략하는 값. v1 Data Memory 그대로다(D-140).
    static final int SAVED_BASE = 0x10010000;
    static final int SAVED_STACK_SIZE = 0;

    /** 한 시뮬레이션에서 이 부품의 내용. */
    static final class State implements InstanceData, Cloneable, MemoryRegistry.View {
        SparseMemory memory;
        WordImage image;
        Value lastClock = Value.UNKNOWN;
        /** 데이터 영역(위로 자람). 옛 Stack 부품은 null. */
        long[] data;
        /** 스택 영역(아래로 자람). 스택 영역이 없는 옛 Data Memory는 null. */
        long[] stack;
        /** 클럭 상승 에지에 읽거나 쓴 스택 영역의 가장 낮은 주소(사용한 영역). -1: 없음. */
        long lowest = -1;
        /** 스택 영역의 가장 높은 접근 주소(깊이 기준을 정할 때). -1: 없음. */
        long highest = -1;
        Problem problem;
        long problemAddr;
        /** 몸체에 빨갛게 보이는 글자(propagate 때 만든다). 문제가 없으면 null. */
        String problemText;

        State(WordImage image) {
            this.image = image;
            this.memory = image.newMemory();
        }

        @Override
        public State clone() {
            try {
                State s = (State) super.clone();
                s.memory = memory.copy();
                s.data = data == null ? null : data.clone();
                s.stack = stack == null ? null : stack.clone();
                return s;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public boolean contains(int addr) {
            return MemoryFactory.contains(data, addr) || MemoryFactory.contains(stack, addr);
        }

        @Override
        public long[][] regions() {
            if (data != null && stack != null) {
                return new long[][] {data, stack};
            }
            return data != null ? new long[][] {data} : stack != null ? new long[][] {stack} : new long[0][];
        }

        @Override
        public long[] stackRegion() {
            return stack;
        }

        /** 데이터 영역 {낮은 주소, 높은 주소(제외)}. 옛 Stack 부품은 null. */
        public long[] dataRegion() {
            return data;
        }

        /** 주 영역: 데이터 영역, 없으면(옛 Stack) 스택 영역. 포크의 메모리 패널(C-06)이 읽는다. */
        public long[] region() {
            return data != null ? data : stack;
        }

        /** 옛 Stack 부품(스택 영역만 있음)이면 true. 합친 Data Memory의 스택은 {@link #stackRegion()}으로 본다. */
        public boolean growsDown() {
            return data == null && stack != null;
        }

        @Override
        public boolean isDefined(int addr) {
            return memory.isDefined(addr);
        }

        @Override
        public int readByte(int addr) {
            return memory.readByte(addr);
        }

        // ---- 포크의 메모리 패널(C-06)과 v2 엔진의 Memory 표가 읽는 값. lib-mips는 JAR 라이브러리로 따로 불려서
        // 이 메서드들을 이름으로(반사) 부른다. 읽기만 한다.

        /** addr 워드(하위 2비트 무시). 쓴 적 없으면 0. */
        public int readWord(int addr) {
            return memory.read(addr);
        }

        /** 쓴 적 있는 4KB 페이지 시작 주소들(오름차순). 초기 내용(.data)도 여기 든다. */
        public long[] pageAddresses() {
            return memory.pageAddresses();
        }

        /** 클럭 상승 에지에 읽거나 쓴 스택 영역의 가장 낮은 워드 주소. 없으면 -1. */
        public long lowestAccess() {
            return lowest;
        }

        /** 데이터 영역에서 초기 내용(.data)이나 쓰기로 값을 가진 워드 수(몸체의 "data N words"). 데이터 영역이 없으면 0. */
        public long dataWords() {
            return data == null ? 0 : memory.heldWords(data[0], data[1]);
        }

        /** 스택 최대 깊이(바이트, 최고 수위) = {@link #usedBytes()}. */
        public long stackPeak() {
            return usedBytes();
        }

        /** 진단(D-04): 지금 문제의 종류 이름(CONTROL_FLOATING, UNALIGNED, NOT_IN_ANY_REGION, STACK_LIMIT, OVERLAP). 없으면 null. */
        public String problemName() {
            return problem == null ? null : problem.name();
        }

        /** 진단(D-04): 몸체의 빨간 글자와 같은 문구. 없으면 null. */
        public String problemText() {
            return problemText;
        }

        /** 스택 깊이를 재는 기준 주소(실행 이미지의 $sp, SPIM 시작 $sp 또는 영역 맨 위, {@link #base()}). */
        public long depthBase() {
            return base();
        }

        /**
         * 깊이를 재는 기준(#134, D-126). 실행 이미지가 {@code reg $sp}를 적었으면(불러오기가 스택 영역을 가진 부품의
         * 내용에 기억한다) 그 값이, 없으면 SPIM이 프로그램을 시작할 때 두는 {@link #SPIM_INITIAL_SP}(그 위 4KB는 시작
         * 코드 몫)가 시작 $sp다. 접근이 모두 그 아래이고 스택 영역이 그 주소를 품으면 거기서 잰다. 그 밖(학생이 영역 맨
         * 위부터 쓰는 경우)은 영역 맨 위에서 잰다. 스택 영역이 없으면 0.
         */
        long base() {
            if (stack == null) {
                return 0;
            }
            long sp = image.initialSp() != null ? image.initialSp() : SPIM_INITIAL_SP;
            boolean fromSp = highest >= 0 && highest < sp && stack[0] <= sp
                    && stack[1] > sp + 4; // 영역이 $sp 위까지 있어 그 사이가 비어 있을 때만
            return fromSp ? sp : stack[1];
        }

        /**
         * 스택의 사용한 영역(바이트, 최고 수위): 기준에서 가장 낮은 접근 주소까지. 접근이 없으면 0.
         * 지금 깊이는 보이지 않는다. 부품은 $sp를 모르고, 마지막 접근 주소는 $sp가 아니다(검토 2차 B). 지금 $sp는
         * 레지스터 패널이 "레지스터 파일로 표시"한 서브회로의 $29로 보인다(PLAN.md 5장).
         */
        long usedBytes() {
            return lowest < 0 ? 0 : base() - lowest;
        }

        /** 클럭 상승 에지의 접근. 스택 영역 안의 접근만 깊이에 센다(옛 Stack은 모든 접근이 스택 영역이다). */
        void accessed(int addr) {
            if (!MemoryFactory.contains(stack, addr)) {
                return;
            }
            long a = addr & 0xfffffffcL;
            if (a > highest) {
                highest = a;
            }
            if (lowest < 0 || a < lowest) {
                lowest = a;
            }
        }
    }

    /** 새로 놓는 값과 저장 기준값이 다른가(합친 Data Memory). 옛 Stack은 false. */
    private final boolean merged;

    /** 합친 Data Memory(데이터+스택, D-140). */
    DataMemory() {
        this("Data Memory", Text.name("Data Memory"), "Data Memory",
                new Attribute<?>[] {BASE, SIZE, STACK_TOP, STACK_SIZE, CONTENTS, SOURCE, StdAttr.LABEL,
                    StdAttr.LABEL_FONT},
                new Object[] {NEW_BASE, NEW_SIZE, NEW_STACK_TOP, NEW_STACK_SIZE, WordImage.EMPTY, "", "",
                    StdAttr.DEFAULT_LABEL_FONT},
                true);
    }

    DataMemory(String name, Text displayName, String title, Attribute<?>[] attrs, Object[] defaults, boolean merged) {
        super(name, displayName, Text.name(title), attrs, defaults);
        this.merged = merged;
        setOffsetBounds(Bounds.create(-240, -60, 240, 120));
        Port addr = new Port(-240, -40, Port.INPUT, W32);
        addr.setToolTip(Text.of("Addr: byte address", "Addr: 바이트 주소"));
        Port writeData = new Port(-240, 0, Port.INPUT, W32);
        writeData.setToolTip(Text.of("WriteData: word to store", "WriteData: 쓸 워드"));
        Port memWrite = new Port(-140, 60, Port.INPUT, 1);
        memWrite.setToolTip(Text.of("MemWrite: store on the rising clock edge", "MemWrite: 클럭 상승 에지에 쓰기"));
        Port memRead = new Port(-70, 60, Port.INPUT, 1);
        memRead.setToolTip(Text.of("MemRead: drive ReadData", "MemRead: ReadData 출력"));
        Port clk = new Port(-200, 60, Port.INPUT, 1);
        clk.setToolTip(Text.name("clk"));
        Port readData = new Port(0, 0, Port.OUTPUT, W32);
        readData.setToolTip(Text.of("ReadData: word at Addr", "ReadData: Addr의 워드"));
        setPorts(new Port[] {addr, writeData, memWrite, memRead, clk, readData});
    }

    /**
     * 저장 기준값(D-140). 원조 2.7.1은 이 값과 같은 속성을 .circ에 적지 않고, 적히지 않은 속성을 이 값으로 읽는다
     * (XmlWriter·XmlReader). 합친 Data Memory는 이 값을 v1 Data Memory 그대로(base 0x10010000, stacksize 0 = 스택 영역
     * 없음) 두고, 새로 놓는 값({@link #createAttributeSet()}: base 0x10000000, stacksize 0x40000)만 바꿨다. 그래서
     * <ul>
     *   <li>속성이 적히지 않은 옛 Data Memory는 전과 같은 데이터 영역 하나로 열린다(옛 Stack과 겹치지 않는다).</li>
     *   <li>새로 놓은 Data Memory는 {@code base}와 {@code stacksize}가 .circ에 적혀, 원조 2.7.1 + 이 jar에서 열고
     *       저장해도 같은 두 영역이다.</li>
     * </ul>
     * 원조의 Multiplexer·Splitter가 파일 판에 따라 기본값을 달리하는 것과 같은 방법이다(판 대신 "속성이 적혔는가").
     */
    @Override
    public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
        if (merged && attr == BASE) {
            return SAVED_BASE;
        }
        if (merged && attr == STACK_SIZE) {
            return SAVED_STACK_SIZE;
        }
        return super.getDefaultAttributeValue(attr, ver);
    }

    static State state(InstanceState s) {
        WordImage image = s.getAttributeValue(CONTENTS);
        State st = (State) s.getData();
        if (st == null || !st.image.equals(image)) {
            st = new State(image); // 새 시뮬레이션이거나 프로그램을 다시 불러옴
            s.setData(st);
        }
        st.data = dataRegion(s.getAttributeSet());
        st.stack = stackRegion(s.getAttributeSet());
        MemoryRegistry.touch(s.getProject(), s.getInstance(), st);
        return st;
    }

    @Override
    public void propagate(InstanceState s) {
        State st = state(s);
        Value clk = s.getPort(CLK);
        boolean rising = Value.FALSE.equals(st.lastClock) && Value.TRUE.equals(clk);
        st.lastClock = clk;

        Value addr = s.getPort(ADDR);
        Value write = s.getPort(MEM_WRITE);
        Value read = s.getPort(MEM_READ);
        boolean inRegion = addr.isFullyDefined() && st.contains(addr.toIntValue());
        boolean used = Value.TRUE.equals(write) || Value.TRUE.equals(read);
        if (rising && used && inRegion) {
            st.accessed(addr.toIntValue()); // 에지 순간의 값만 센다(전파 중 잠깐 나타나는 주소 제외)
        }
        if (rising && inRegion && Value.TRUE.equals(write)) {
            Value data = s.getPort(WRITE_DATA);
            if (data.isFullyDefined()) {
                st.memory.write(addr.toIntValue(), data.toIntValue());
            } else {
                st.memory.writeUndefined(addr.toIntValue()); // X가 메모리에 기록됨(4단계 진단)
            }
        }

        Value out = MemoryFactory.floating();
        if (inRegion && Value.TRUE.equals(read)) {
            int a = addr.toIntValue();
            out = st.memory.isDefined(a) ? word(st.memory.read(a)) : MemoryFactory.floating();
        }
        s.setPort(READ_DATA, out, DELAY);
        diagnose(s, st, addr, write, read, used, inRegion);
        st.problemText = describe(st, s);
    }

    /** 동작하지 않는 경우를 하나 고른다. 앞의 것이 먼저다. */
    private static void diagnose(InstanceState s, State st, Value addr, Value write, Value read,
            boolean used, boolean inRegion) {
        st.problem = null;
        if (!write.isFullyDefined() || !read.isFullyDefined()) {
            st.problem = Problem.CONTROL_FLOATING;
            return;
        }
        for (MemoryRegistry.View other : MemoryRegistry.all(s.getProject())) {
            long[] at = other == st ? null : overlap(st.regions(), other.regions());
            if (at != null) {
                st.problem = Problem.OVERLAP;
                st.problemAddr = at[0];
                return;
            }
        }
        if (!used || !addr.isFullyDefined()) {
            return;
        }
        int a = addr.toIntValue();
        if (inRegion && (a & 3) != 0) {
            st.problem = Problem.UNALIGNED;
            st.problemAddr = a & 0xffffffffL;
        } else if (!inRegion && MemoryRegistry.find(s.getProject(), a) == null) {
            st.problemAddr = a & 0xffffffffL;
            st.problem = stackBelow(s, a) != null ? Problem.STACK_LIMIT : Problem.NOT_IN_ANY_REGION;
        }
    }

    /** a가 스택 영역의 한계 바로 아래(한계 폭만큼)에 있는 부품. 없으면 null. */
    static MemoryRegistry.View stackBelow(InstanceState s, int a) {
        long addr = a & 0xffffffffL;
        for (MemoryRegistry.View v : MemoryRegistry.all(s.getProject())) {
            long[] r = v.stackRegion();
            if (r != null && addr < r[0] && addr >= r[0] - (r[1] - r[0])) {
                return v;
            }
        }
        return null;
    }

    static boolean overlaps(long[] a, long[] b) {
        return a[0] < b[1] && b[0] < a[1];
    }

    /**
     * 두 부품의 영역이 처음 겹치는 곳 {낮은 주소, 높은 주소(제외)}. 겹치지 않으면 null. 한 부품 안의 두 영역은 한 메모리라
     * 서로 겹쳐도 보지 않는다.
     */
    static long[] overlap(long[][] mine, long[][] theirs) {
        for (long[] a : mine) {
            for (long[] b : theirs) {
                if (overlaps(a, b)) {
                    return new long[] {Math.max(a[0], b[0]), Math.min(a[1], b[1])};
                }
            }
        }
        return null;
    }

    /** 부품 안에 보일 문구(사실만, 원인·해결책 추측 없음). */
    static String describe(State st, InstanceState s) {
        if (st.problem == null) {
            return null;
        }
        String at = WordImage.hex(st.problemAddr);
        switch (st.problem) {
            case CONTROL_FLOATING: {
                List<String> names = new ArrayList<String>();
                if (!s.getPort(MEM_WRITE).isFullyDefined()) {
                    names.add("MemWrite");
                }
                if (!s.getPort(MEM_READ).isFullyDefined()) {
                    names.add("MemRead");
                }
                return String.join(", ", names) + Text.of(" floating", " 떠 있음").get();
            }
            case UNALIGNED:
                return Text.of("Addr not word-aligned", "Addr가 워드 정렬 안 됨").get();
            case NOT_IN_ANY_REGION:
                return Text.of(at + " is in no memory region", at + "는 어느 메모리 영역에도 없음").get();
            case STACK_LIMIT: {
                MemoryRegistry.View stack = stackBelow(s, (int) st.problemAddr);
                String limit = bytes(stack == null ? 0 : stack.stackRegion()[1] - stack.stackRegion()[0]);
                return Text.of("Stack use exceeds its limit (" + limit + ")",
                        "Stack 사용량이 한계(" + limit + ")를 넘었습니다").get();
            }
            case OVERLAP:
                return Text.of("Memory regions overlap at " + at, "메모리 영역이 " + at + "에서 겹침").get();
            default:
                return null;
        }
    }

    static String bytes(long n) {
        if (n >= 1 << 20 && n % (1 << 20) == 0) {
            return (n >> 20) + "MB";
        }
        if (n >= 1 << 10 && n % (1 << 10) == 0) {
            return (n >> 10) + "KB";
        }
        return n + "B";
    }

    /** 우클릭 메뉴 "Load Program..."(PLAN.md 6.3). 옛 Stack은 .data를 받지 않는다. */
    @Override
    protected Object getInstanceFeature(Instance instance, Object key) {
        if (key == MenuExtender.class && !(this instanceof StackMemory)) {
            return new LoadProgramMenu(instance);
        }
        return super.getInstanceFeature(instance, key);
    }

    @Override
    String iconText() {
        return "DM";
    }

    @Override
    String[] bodyLines(InstancePainter painter) {
        long[] data = dataRegion(painter.getAttributeSet());
        long[] stack = stackRegion(painter.getAttributeSet());
        State st = painter.getShowState() ? (State) painter.getData() : null;
        if (data != null && stack != null) {
            return mergedLines(painter, data, stack, st);
        }
        String region = region(painter);
        if (st == null) {
            return new String[] {region};
        }
        List<String> lines = new ArrayList<String>();
        lines.add(region);
        if (st.growsDown()) {
            lines.add(usageLine(st));
        }
        String word = wordLine(painter, st);
        if (word != null) {
            lines.add(word);
        }
        return lines.toArray(new String[0]);
    }

    /**
     * 합친 Data Memory의 몸체(D-140): 두 영역의 범위, 지금 주소의 워드, 쓰임(데이터 워드 수, 스택 최대 깊이). 줄 자리는
     * 고정이다: 시뮬레이션 중 줄이 오르내리지 않고, 넷째 줄 자리(왼쪽 WriteData·오른쪽 ReadData 이름 높이)는 비운다.
     */
    static String[] mergedLines(InstancePainter painter, long[] data, long[] stack, State st) {
        String dataLine = "data  " + range(data);
        String stackLine = "stack " + range(stack);
        if (st == null) {
            return new String[] {dataLine, stackLine};
        }
        String word = wordLine(painter, st);
        return new String[] {dataLine, stackLine, word == null ? "" : word, "", mergedUsageLine(st)};
    }

    /** 합친 Data Memory의 쓰임 줄: {@code data 3 words, stack peak 56 B}. */
    static String mergedUsageLine(State st) {
        return Text.name("data " + Text.count(st.dataWords(), "word") + ", stack peak " + st.stackPeak() + " B").get();
    }

    /** 지금 주소의 워드 줄. 주소가 영역 밖이거나 정해지지 않았으면 null. */
    static String wordLine(InstancePainter painter, State st) {
        Value addr = painter.getPort(ADDR);
        if (addr.isFullyDefined() && st.contains(addr.toIntValue())) {
            int a = addr.toIntValue() & ~3;
            String word = st.memory.isDefined(a) ? WordImage.hex(st.memory.read(a)) : "xxxxxxxx";
            return WordImage.hex(a) + ": " + word;
        }
        return null;
    }

    /** Stack 몸체의 사용량 줄: 사용한 영역(최고 수위)만. 지금 깊이는 적지 않는다(검토 2차 B). */
    static String usageLine(State st) {
        return Text.name("used " + st.usedBytes() + " B (peak)").get();
    }

    @Override
    String status(InstancePainter painter) {
        State st = painter.getShowState() ? (State) painter.getData() : null;
        return st == null ? null : describe(st, painter);
    }

    @Override
    void drawPorts(InstancePainter painter) {
        drawPortInside(painter, ADDR, "Addr");
        drawPortInside(painter, WRITE_DATA, "WriteData");
        drawPortInside(painter, MEM_WRITE, "MemWrite");
        drawPortInside(painter, MEM_READ, "MemRead");
        painter.drawClock(CLK, Direction.NORTH);
        drawPortInside(painter, READ_DATA, "ReadData");
    }
}
