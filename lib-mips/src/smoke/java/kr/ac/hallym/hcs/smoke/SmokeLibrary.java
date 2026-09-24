/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.smoke;

import java.util.Collections;
import java.util.List;

import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;

/** JAR 라이브러리 방식을 확인하는 최소 라이브러리. 배포하지 않는다(docs/jar-library.md). */
public class SmokeLibrary extends Library {
    private final List<Tool> tools =
            Collections.<Tool>singletonList(new AddTool(new Incrementer()));

    public SmokeLibrary() {
    }

    @Override
    public String getDisplayName() {
        return "HCS Smoke";
    }

    @Override
    public List<? extends Tool> getTools() {
        return tools;
    }
}
