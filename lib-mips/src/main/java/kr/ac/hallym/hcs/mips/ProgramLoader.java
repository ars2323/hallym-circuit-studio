/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.instance.StdAttr;

/**
 * 어셈블한 프로그램을 어느 메모리에 어떤 속성으로 넣을지 정한다(PLAN.md 6.3). GUI와 떨어져 있어 테스트할 수 있다.
 * .text는 Instruction Memory에, .data는 Data Memory(Stack 제외)에 넣는다. 주소는 SPIM 그대로다.
 */
final class ProgramLoader {
    private ProgramLoader() {
    }

    /** 회로 안의 메모리 부품 하나. */
    static final class Target {
        final Circuit circuit;
        final Component component;

        Target(Circuit circuit, Component component) {
            this.circuit = circuit;
            this.component = component;
        }

        long base() {
            return component.getAttributeSet().getValue(MemoryFactory.BASE) & 0xffffffffL;
        }

        long size() {
            return component.getAttributeSet().getValue(MemoryFactory.SIZE) & 0xffffffffL;
        }

        boolean contains(long addr) {
            return addr >= base() && addr - base() < size();
        }

        /** 목록에 보일 이름. 예: {@code datapath › IMem (00400000-004fffff)}. */
        String describe() {
            String label = component.getAttributeSet().getValue(StdAttr.LABEL);
            String name = label == null || label.isEmpty() ? component.getFactory().getDisplayName() : label;
            long last = Math.min(base() + size(), 0x100000000L) - 1;
            return circuit.getName() + " › " + name + " (" + WordImage.hex(base()) + "-" + WordImage.hex(last) + ")";
        }

        @Override
        public String toString() {
            return describe();
        }
    }

    static final class Change {
        final Target target;
        final Attribute<?> attr;
        final Object value;

        Change(Target target, Attribute<?> attr, Object value) {
            this.target = target;
            this.attr = attr;
            this.value = value;
        }
    }

    static final class Plan {
        final List<Change> changes = new ArrayList<Change>();
        final List<String> notes = new ArrayList<String>();
    }

    static boolean isText(ComponentFactory f) {
        return f instanceof InstructionMemory;
    }

    static boolean isData(ComponentFactory f) {
        return f instanceof DataMemory && !(f instanceof StackMemory);
    }

    /** 파일의 모든 회로에서 .text(또는 .data)를 받을 부품. */
    static List<Target> find(List<Circuit> circuits, boolean text) {
        List<Target> out = new ArrayList<Target>();
        for (Circuit c : circuits) {
            for (Component comp : c.getNonWires()) {
                if (text ? isText(comp.getFactory()) : isData(comp.getFactory())) {
                    out.add(new Target(c, comp));
                }
            }
        }
        return out;
    }

    static Plan plan(AssembledProgram p, Target text, Target data, String source) {
        Plan plan = new Plan();
        if (text != null) {
            plan.changes.add(new Change(text, MemoryFactory.CONTENTS, p.textImage()));
            plan.changes.add(new Change(text, MemoryFactory.SOURCE, source));
            int outside = 0;
            for (AssembledProgram.Word w : p.text) {
                if (!text.contains(w.addr)) {
                    outside += 1;
                }
            }
            plan.notes.add(p.text.size() + Text.of(" words of .text → ", " 워드 .text → ").get() + text.describe());
            if (outside > 0) {
                plan.notes.add(outside + Text.of(" .text words lie outside that memory's region",
                        " 워드가 그 메모리 영역 밖에 있음").get());
            }
        } else if (!p.text.isEmpty()) {
            plan.notes.add(Text.of("No Instruction Memory for .text", ".text를 넣을 Instruction Memory가 없음").get());
        }
        if (data != null) {
            plan.changes.add(new Change(data, MemoryFactory.CONTENTS, p.dataImage()));
            plan.changes.add(new Change(data, MemoryFactory.SOURCE, source));
            int outside = 0;
            for (Long addr : p.data.keySet()) {
                if (!data.contains(addr)) {
                    outside += 1;
                }
            }
            plan.notes.add(p.data.size() + Text.of(" words of .data → ", " 워드 .data → ").get() + data.describe());
            if (outside > 0) {
                plan.notes.add(outside + Text.of(" .data words lie outside that memory's region",
                        " 워드가 그 메모리 영역 밖에 있음").get());
            }
        } else if (!p.data.isEmpty()) {
            plan.notes.add(Text.of("No Data Memory for .data", ".data를 넣을 Data Memory가 없음").get());
        }
        for (AssembledProgram.Message w : p.warnings) {
            plan.notes.add(Text.of("hcs-asm: ", "hcs-asm: ").get() + w);
        }
        plan.notes.add(Text.of("Instructions used: ", "쓰는 명령어: ").get() + String.join(", ", p.usedInstructions()));
        return plan;
    }

    /** .circ 폴더 기준 상대 경로(PLAN.md 6.8). 구분자는 '/'. 기준이 없거나 다른 드라이브면 절대 경로. */
    static String relativeSource(File circFile, File source) {
        File abs = source.getAbsoluteFile();
        if (circFile == null || circFile.getAbsoluteFile().getParentFile() == null) {
            return abs.getPath();
        }
        try {
            java.net.URI base = circFile.getAbsoluteFile().getParentFile().toURI();
            java.net.URI rel = base.relativize(abs.toURI());
            if (!rel.isAbsolute()) {
                return new File(rel.getPath()).getPath().replace(File.separatorChar, '/');
            }
        } catch (IllegalArgumentException e) {
            // 다른 루트
        }
        return abs.getPath();
    }
}
