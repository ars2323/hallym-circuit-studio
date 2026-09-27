/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.Arrays;
import java.util.List;

import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * MIPS 부품 라이브러리(트랙 A). 원조 Logisim 2.7.1에서 JAR 라이브러리로 불러 쓴다.
 *
 * <p>.circ에는 {@code <lib desc="jar#<경로>#kr.ac.hallym.hcs.mips.MipsLibrary">}로 저장되므로
 * 이 클래스의 이름과 패키지, 부품 이름은 바꾸지 않는다(PLAN.md 6.2, 6.9).
 *
 * <p>D-140: Data Memory 하나가 데이터와 스택 영역을 함께 맡는다. 옛 Stack은 목록 맨 끝에 "Stack (old circuits)"로
 * 남는다. 원조 2.7.1은 JAR 라이브러리를 LoadedLibrary로 감싸고 부품을 이 목록에서만 찾으므로(Library.getTool),
 * 목록에서 빼면 Stack이 든 옛 .circ를 열 수 없다.
 */
public class MipsLibrary extends Library {
    private final List<Tool> tools = Arrays.<Tool>asList(
            new AddTool(new InstructionMemory()),
            new PlacementTool(new DataMemory()),
            new AddTool(new Console()),
            new AddTool(new RadixProbe()),
            new AddTool(new StackMemory())); // 옛 회로용(D-140)

    public MipsLibrary() {
    }

    @Override
    public String getDisplayName() {
        return "Hallym MIPS";
    }

    @Override
    public List<? extends Tool> getTools() {
        return tools;
    }
}
