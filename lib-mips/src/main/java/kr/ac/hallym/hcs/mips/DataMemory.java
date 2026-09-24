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
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.tools.MenuExtender;

/**
 * Data Memory와 Stack(PLAN.md 6.2). 입력 {@code Addr}, {@code WriteData}, {@code MemWrite}, {@code MemRead},
 * {@code clk}, 출력 {@code ReadData}. 모두 32비트이고 제어 입력은 1비트다.
 *
 * <ul>
 *   <li>읽기는 조합이다. {@code MemRead}가 1이고 주소가 영역 안이면 그 워드를 낸다.</li>
 *   <li>쓰기는 {@code clk} 상승 에지에 {@code MemWrite}가 1이고 주소가 영역 안일 때다.</li>
 *   <li>영역 밖이거나 {@code MemRead}가 1이 아니면 출력을 구동하지 않는다. Data Memory와 Stack의
 *       {@code ReadData}를 한 선에 이을 수 있다.</li>
 *   <li>떠 있는 제어 입력은 1로 취급하지 않는다(원조 RAM과 다름). 부품에 상태로 표시한다.</li>
 *   <li>워드 접근만 한다. 주소의 하위 2비트는 쓰지 않고, 0이 아니면 표시한다.</li>
 *   <li>실행 중 쓴 값은 저장하지 않는다. 리셋하면 {@code contents}(.s의 .data)로 돌아간다.</li>
 * </ul>
 */
class DataMemory extends MemoryFactory {
    static final int ADDR = 0;
    static final int WRITE_DATA = 1;
    static final int MEM_WRITE = 2;
    static final int MEM_READ = 3;
    static final int CLK = 4;
    static final int READ_DATA = 5;

    /** 한 시뮬레이션에서 이 부품의 내용. */
    static final class State implements InstanceData, Cloneable, MemoryRegistry.View {
        SparseMemory memory;
        WordImage image;
        Value lastClock = Value.UNKNOWN;
        long base;
        long size;

        State(WordImage image) {
            this.image = image;
            this.memory = image.newMemory();
        }

        @Override
        public State clone() {
            try {
                State s = (State) super.clone();
                s.memory = memory.copy();
                return s;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public boolean contains(int addr) {
            long a = addr & 0xffffffffL;
            return a >= base && a - base < size;
        }

        @Override
        public boolean isDefined(int addr) {
            return memory.isDefined(addr);
        }

        @Override
        public int readByte(int addr) {
            return memory.readByte(addr);
        }
    }

    DataMemory() {
        this("Data Memory", Text.of("Data Memory", "데이터 메모리"), "Data Memory", 0x10010000, 0x00100000);
    }

    DataMemory(String name, Text displayName, String title, int defaultBase, int defaultSize) {
        super(name, displayName, Text.of(title, title), defaultBase, defaultSize);
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
        st.base = s.getAttributeValue(BASE) & 0xffffffffL;
        st.size = s.getAttributeValue(SIZE) & 0xffffffffL;
        MemoryRegistry.touch(s.getProject(), st);
        return st;
    }

    @Override
    public void propagate(InstanceState s) {
        State st = state(s);
        Value clk = s.getPort(CLK);
        boolean rising = Value.FALSE.equals(st.lastClock) && Value.TRUE.equals(clk);
        st.lastClock = clk;

        Value addr = s.getPort(ADDR);
        boolean inRegion = addr.isFullyDefined() && st.contains(addr.toIntValue());
        if (rising && inRegion && Value.TRUE.equals(s.getPort(MEM_WRITE))) {
            Value data = s.getPort(WRITE_DATA);
            if (data.isFullyDefined()) {
                st.memory.write(addr.toIntValue(), data.toIntValue());
            } else {
                st.memory.writeUndefined(addr.toIntValue()); // X가 메모리에 기록됨(4단계 진단)
            }
        }

        Value out = MemoryFactory.floating();
        if (inRegion && Value.TRUE.equals(s.getPort(MEM_READ))) {
            int a = addr.toIntValue();
            out = st.memory.isDefined(a) ? word(st.memory.read(a)) : MemoryFactory.floating();
        }
        s.setPort(READ_DATA, out, DELAY);
    }

    /** 우클릭 메뉴 ".s 프로그램 불러오기"(PLAN.md 6.3). */
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
        Value addr = painter.getShowState() ? painter.getPort(ADDR) : Value.UNKNOWN;
        if (st == null || !addr.isFullyDefined() || !st.contains(addr.toIntValue())) {
            return new String[] {region};
        }
        int a = addr.toIntValue() & ~3;
        String word = st.memory.isDefined(a) ? WordImage.hex(st.memory.read(a)) : "xxxxxxxx";
        return new String[] {region, WordImage.hex(a) + ": " + word};
    }

    @Override
    String status(InstancePainter painter) {
        if (!painter.getShowState()) {
            return null;
        }
        List<String> problems = new ArrayList<String>();
        if (!painter.getPort(MEM_WRITE).isFullyDefined()) {
            problems.add("MemWrite");
        }
        if (!painter.getPort(MEM_READ).isFullyDefined()) {
            problems.add("MemRead");
        }
        if (!problems.isEmpty()) {
            return String.join(", ", problems) + Text.of(" floating", " 떠 있음").get();
        }
        Value addr = painter.getPort(ADDR);
        boolean used = Value.TRUE.equals(painter.getPort(MEM_WRITE)) || Value.TRUE.equals(painter.getPort(MEM_READ));
        if (used && addr.isFullyDefined() && (addr.toIntValue() & 3) != 0) {
            return Text.of("Addr not word-aligned", "Addr가 워드 정렬 안 됨").get();
        }
        return null;
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
