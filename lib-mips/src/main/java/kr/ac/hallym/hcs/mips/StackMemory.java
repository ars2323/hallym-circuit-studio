/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.instance.StdAttr;

/**
 * 옛 Stack(PLAN.md 6.2, D-018). Data Memory와 포트·동작이 같고, 맨 위 워드 {@code top}(0x7FFFFFFC)에서 낮은 주소 쪽으로
 * 자란다. 기본 한계는 1MB라 영역은 {@code 0x7FF00000}~{@code 0x7FFFFFFF}이고, SPIM의 {@code $sp} 초기값
 * {@code 0x7FFFEFFC}가 이 안에 있다. 한계 바로 아래 주소에 접근하면 "스택 사용량이 한계를 넘었습니다"를 알린다.
 *
 * <p>D-140(사용자 결정)부터 새 회로는 스택 영역을 함께 맡는 Data Memory 하나를 쓴다. 이 부품은 옛 .circ가 전과 똑같이
 * 열리고 동작하도록 남긴다: 저장 이름·속성·기본값·동작을 바꾸지 않는다. 원조 2.7.1은 JAR 라이브러리의 부품을 도구
 * 목록({@link MipsLibrary#getTools()})에서만 찾으므로 목록에서 뺄 수는 없고, 목록 맨 끝에 "Stack (old circuits)"로
 * 둔다. v2 화면의 부품 목록은 엔진이 이 부품을 빼고 보인다(Kinds 등록표).
 */
final class StackMemory extends DataMemory {
    StackMemory() {
        super("Stack", Text.name("Stack (old circuits)"), "Stack",
                new Attribute<?>[] {TOP, SIZE, CONTENTS, SOURCE, StdAttr.LABEL, StdAttr.LABEL_FONT},
                new Object[] {0x7FFFFFFC, 0x00100000, WordImage.EMPTY, "", "", StdAttr.DEFAULT_LABEL_FONT},
                false);
        setDefaultToolTip(Text.of("Stack for old circuits. New circuits use one Data Memory, which also holds the"
                + " stack region.", "예전 회로용 Stack입니다. 새 회로는 스택 영역을 함께 맡는 Data Memory 하나를 씁니다."));
    }

    @Override
    String iconText() {
        return "SP";
    }
}
