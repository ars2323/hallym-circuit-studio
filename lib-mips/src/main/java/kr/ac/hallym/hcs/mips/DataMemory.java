/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.tools.MenuExtender;

/**
 * Data Memory와 Stack(PLAN.md 6.2). 입력 {@code Addr}, {@code WriteData}, {@code MemWrite}, {@code MemRead},
 * {@code clk}, 출력 {@code ReadData}. 모두 32비트이고 제어 입력은 1비트다.
 *
 * <ul>
 *   <li>Data Memory는 {@code base}(0x10010000)부터 높은 주소 쪽으로, Stack은 {@code top}(0x7FFFFFFC)부터
 *       낮은 주소 쪽으로 자란다. 한계는 {@code size}(기본 1MB)다.</li>
 *   <li>읽기는 조합이다. {@code MemRead}가 1이고 주소가 영역 안이면 그 워드를 낸다.</li>
 *   <li>쓰기는 {@code clk} 상승 에지에 {@code MemWrite}가 1이고 주소가 영역 안일 때다.</li>
 *   <li>영역 밖이거나 {@code MemRead}가 1이 아니면 출력을 구동하지 않는다. 쓰기도 하지 않는다.</li>
 *   <li>떠 있는 제어 입력은 1로 취급하지 않는다(원조 RAM과 다름).</li>
 *   <li>워드 접근만 한다. 주소의 하위 2비트는 쓰지 않는다.</li>
 *   <li>실행 중 쓴 값은 저장하지 않는다. 리셋하면 {@code contents}(.s의 .data)로 돌아간다.</li>
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

    /** 한 시뮬레이션에서 이 부품의 내용. */
    static final class State implements InstanceData, Cloneable, MemoryRegistry.View {
        SparseMemory memory;
        WordImage image;
        Value lastClock = Value.UNKNOWN;
        long[] region = {0, 0};
        boolean growsDown;
        /** 클럭 상승 에지에 읽거나 쓴 가장 낮은 주소와 마지막 주소(Stack 깊이). -1: 없음. */
        long lowest = -1;
        long last = -1;
        Problem problem;
        long problemAddr;

        State(WordImage image) {
            this.image = image;
            this.memory = image.newMemory();
        }

        @Override
        public State clone() {
            try {
                State s = (State) super.clone();
                s.memory = memory.copy();
                s.region = region.clone();
                return s;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public boolean contains(int addr) {
            return MemoryFactory.contains(region, addr);
        }

        @Override
        public long[] region() {
            return region;
        }

        @Override
        public boolean growsDown() {
            return growsDown;
        }

        @Override
        public boolean isDefined(int addr) {
            return memory.isDefined(addr);
        }

        @Override
        public int readByte(int addr) {
            return memory.readByte(addr);
        }

        /** Stack의 지금 깊이(바이트): 영역 맨 위에서 마지막 접근 주소까지. 접근이 없으면 0. */
        long depth() {
            return last < 0 ? 0 : region[1] - last;
        }

        long maxDepth() {
            return lowest < 0 ? 0 : region[1] - lowest;
        }

        void accessed(int addr) {
            long a = addr & 0xfffffffcL;
            last = a;
            if (lowest < 0 || a < lowest) {
                lowest = a;
            }
        }
    }

    DataMemory() {
        this("Data Memory", Text.of("Data Memory", "데이터 메모리"), "Data Memory", false, 0x10010000, 0x00100000);
    }

    DataMemory(String name, Text displayName, String title, boolean growsDown, int start, int defaultSize) {
        super(name, displayName, Text.of(title, title), growsDown, start, defaultSize);
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
        clk.setToolTip(Text.of("clk", "clk"));
        Port readData = new Port(0, 0, Port.OUTPUT, W32);
        readData.setToolTip(Text.of("ReadData: word at Addr", "ReadData: Addr의 워드"));
        setPorts(new Port[] {addr, writeData, memWrite, memRead, clk, readData});
    }

    static State state(InstanceState s) {
        WordImage image = s.getAttributeValue(CONTENTS);
        State st = (State) s.getData();
        if (st == null || !st.image.equals(image)) {
            st = new State(image); // 새 시뮬레이션이거나 .s를 다시 불러옴
            s.setData(st);
        }
        st.region = region(s.getAttributeSet());
        st.growsDown = s.getAttributeSet().containsAttribute(TOP);
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
            if (other != st && overlaps(st.region, other.region())) {
                st.problem = Problem.OVERLAP;
                st.problemAddr = Math.max(st.region[0], other.region()[0]);
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

    /** a가 한계 바로 아래(한계 폭만큼)에 있는 Stack. 없으면 null. */
    static MemoryRegistry.View stackBelow(InstanceState s, int a) {
        long addr = a & 0xffffffffL;
        for (MemoryRegistry.View v : MemoryRegistry.all(s.getProject())) {
            long[] r = v.region();
            if (v.growsDown() && addr < r[0] && addr >= r[0] - (r[1] - r[0])) {
                return v;
            }
        }
        return null;
    }

    static boolean overlaps(long[] a, long[] b) {
        return a[0] < b[1] && b[0] < a[1];
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
                String limit = bytes(stack == null ? 0 : stack.region()[1] - stack.region()[0]);
                return Text.of("Stack use exceeds its limit (" + limit + ")",
                        "스택 사용량이 한계(" + limit + ")를 넘었습니다").get();
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

    /** 우클릭 메뉴 ".s 프로그램 불러오기"(PLAN.md 6.3). Stack은 .data를 받지 않는다. */
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
        String region = region(painter);
        State st = painter.getShowState() ? (State) painter.getData() : null;
        if (st == null) {
            return new String[] {region};
        }
        List<String> lines = new ArrayList<String>();
        lines.add(region);
        if (st.growsDown) {
            lines.add(Text.of("depth " + st.depth() + " B, max " + st.maxDepth() + " B",
                    "깊이 " + st.depth() + " B, 최대 " + st.maxDepth() + " B").get());
        }
        Value addr = painter.getPort(ADDR);
        if (addr.isFullyDefined() && st.contains(addr.toIntValue())) {
            int a = addr.toIntValue() & ~3;
            String word = st.memory.isDefined(a) ? WordImage.hex(st.memory.read(a)) : "xxxxxxxx";
            lines.add(WordImage.hex(a) + ": " + word);
        }
        return lines.toArray(new String[0]);
    }

    @Override
    String status(InstancePainter painter) {
        State st = painter.getShowState() ? (State) painter.getData() : null;
        return st == null ? null : describe(st, painter);
    }

    @Override
    void drawPorts(InstancePainter painter) {
        painter.drawPort(ADDR, "Addr", Direction.EAST);
        painter.drawPort(WRITE_DATA, "WriteData", Direction.EAST);
        painter.drawPort(MEM_WRITE, "MemWrite", Direction.SOUTH);
        painter.drawPort(MEM_READ, "MemRead", Direction.SOUTH);
        painter.drawClock(CLK, Direction.NORTH);
        painter.drawPort(READ_DATA, "ReadData", Direction.WEST);
    }
}
