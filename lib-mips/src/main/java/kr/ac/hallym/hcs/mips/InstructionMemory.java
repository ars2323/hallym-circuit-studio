/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.tools.MenuExtender;

/**
 * Instruction Memory(PLAN.md 6.2). 입력 {@code Addr}(32), 출력 {@code Instr}(32). 클럭 없는 읽기 전용이다.
 * 내용은 {@code contents} 속성(.s의 .text)이고 .circ에 저장된다.
 */
final class InstructionMemory extends MemoryFactory {
    static final int ADDR = 0;
    static final int INSTR = 1;

    InstructionMemory() {
        super("Instruction Memory", Text.of("Instruction Memory", "명령어 메모리"),
                Text.of("Instruction Memory", "Instruction Memory"), false, 0x00400000, 0x00100000);
        setOffsetBounds(Bounds.create(-200, -40, 200, 80));
        Port addr = new Port(-200, 0, Port.INPUT, W32);
        addr.setToolTip(Text.of("Addr: byte address (PC)", "Addr: 바이트 주소(PC)"));
        Port instr = new Port(0, 0, Port.OUTPUT, W32);
        instr.setToolTip(Text.of("Instr: instruction word", "Instr: 명령어 워드"));
        setPorts(new Port[] {addr, instr});
    }

    @Override
    public void propagate(InstanceState state) {
        Value addr = state.getPort(ADDR);
        Value out = MemoryFactory.floating();
        if (addr.isFullyDefined() && contains(state, addr.toIntValue())) {
            WordImage image = state.getAttributeValue(CONTENTS);
            out = word(image.read(addr.toIntValue()));
        }
        state.setPort(INSTR, out, DELAY);
    }

    /** 우클릭 메뉴 ".s 프로그램 불러오기"(PLAN.md 6.3). */
    @Override
    protected Object getInstanceFeature(Instance instance, Object key) {
        if (key == MenuExtender.class && true) {
            return new LoadProgramMenu(instance);
        }
        return super.getInstanceFeature(instance, key);
    }

    @Override
    String iconText() {
        return "IM";
    }

    @Override
    String[] bodyLines(InstancePainter painter) {
        String region = region(painter);
        WordImage image = painter.getAttributeValue(CONTENTS);
        String size = image.isEmpty() ? Text.of("no program", "프로그램 없음").get()
                : image.size() + Text.of(" words", " 워드").get();
        if (!painter.getShowState()) {
            return new String[] {region, size};
        }
        Value addr = painter.getPort(ADDR);
        if (!addr.isFullyDefined()) {
            return new String[] {region, size, "Addr " + addr.toHexString()};
        }
        int a = addr.toIntValue() & ~3;
        return new String[] {region, size,
                WordImage.hex(a) + ": " + WordImage.hex(image.read(a))};
    }

    @Override
    String status(InstancePainter painter) {
        if (!painter.getShowState()) {
            return null;
        }
        Value addr = painter.getPort(ADDR);
        if (addr.isFullyDefined() && (addr.toIntValue() & 3) != 0) {
            return Text.of("Addr not word-aligned", "Addr가 워드 정렬 안 됨").get();
        }
        return null;
    }

    @Override
    void drawPorts(InstancePainter painter) {
        painter.drawPort(ADDR, "Addr", Direction.EAST);
        painter.drawPort(INSTR, "Instr", Direction.WEST);
    }
}
