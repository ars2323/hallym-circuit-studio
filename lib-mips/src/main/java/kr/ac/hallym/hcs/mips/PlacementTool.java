/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import com.cburch.logisim.LogisimVersion;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;

/**
 * 새로 놓는 값과 저장 기준값이 다른 부품(합친 Data Memory, D-140)의 도구. 원조 2.7.1은 도구의 속성 중 도구 기본값과 다른
 * 것을 .circ의 {@code <lib>} 아래 {@code <tool>}로 적는다(XmlWriter.fromLibrary·fromTool). 원조 {@link AddTool}은
 * 부품의 저장 기준값을 도구 기본값으로 쓰므로, 그대로 두면 Data Memory를 한 번 놓기만 해도 새로 놓는 값(두 영역)이
 * {@code <tool>}로 적힌다. 이 도구는 도구 기본값을 새로 놓는 값으로 알려, 학생이 도구 속성을 바꾸지 않았으면 아무것도
 * 적지 않는다(다른 원조 도구와 같다). 부품 자체의 저장은 부품의 저장 기준값을 따른다.
 */
final class PlacementTool extends AddTool {
    private final ComponentFactory factory;
    private AttributeSet placement;

    PlacementTool(ComponentFactory factory) {
        super(factory);
        this.factory = factory;
    }

    @Override
    public Object getDefaultAttributeValue(Attribute<?> attr, LogisimVersion ver) {
        if (placement == null) {
            placement = factory.createAttributeSet();
        }
        return placement.getValue(attr);
    }

    /** 도구 모음·마우스 매핑에 넣는 복사본도 같은 도구 기본값을 쓴다(원조 복사는 {@link AddTool}을 만든다). */
    @Override
    public Tool cloneTool() {
        PlacementTool t = new PlacementTool(factory);
        AttributeSets.copy(getAttributeSet(), t.getAttributeSet());
        return t;
    }
}
