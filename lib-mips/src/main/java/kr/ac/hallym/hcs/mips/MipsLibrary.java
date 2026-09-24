/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.util.Collections;
import java.util.List;

import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/**
 * MIPS 부품 라이브러리(트랙 A). 원조 Logisim 2.7.1에서 JAR 라이브러리로 불러 쓴다.
 *
 * <p>.circ에는 {@code <lib desc="jar#<경로>#kr.ac.hallym.hcs.mips.MipsLibrary">}로 저장되므로
 * 이 클래스의 이름과 패키지는 바꾸지 않는다. 부품은 1단계에서 추가한다(PLAN.md 6.2, 6.9).
 */
public class MipsLibrary extends Library {
    private final List<Tool> tools = Collections.emptyList();

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
