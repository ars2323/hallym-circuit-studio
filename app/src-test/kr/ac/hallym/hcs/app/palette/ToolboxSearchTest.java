/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;

import kr.ac.hallym.hcs.regress.CircuitBuilder;

/** 검토 반영 1: 부품 트리 위 검색창은 팔레트와 같은 검색 모델로 부품·서브회로만 거르고, 고르면 원조 도구가 된다. */
class ToolboxSearchTest {
    @TempDir
    Path tmp;

    @Test
    void filtersComponentsAndSubcircuitsWithThePaletteModel() throws Exception {
        LogisimFile file = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit alu = new Circuit("alu");
        file.addCircuit(alu);

        List<Palette.Item> mux = ToolboxSearch.results("mux 32", file);
        assertEquals("Multiplexer", mux.get(0).name);
        assertEquals("32", mux.get(0).attrs.get("width"));
        Tool t = ToolboxSearch.toolFor(file, mux.get(0));
        assertTrue(t instanceof AddTool && ((AddTool) t).getFactory().getName().equals("Multiplexer"));

        assertTrue(ToolboxSearch.results("리셋", file).isEmpty(), "commands stay in the Ctrl+K palette");
        for (Palette.Item it : ToolboxSearch.results("re", file)) {
            assertFalse(it.kind == Palette.Kind.COMMAND);
        }

        List<Palette.Item> sub = ToolboxSearch.results("alu", file);
        Palette.Item s = sub.stream().filter(i -> i.kind == Palette.Kind.SUBCIRCUIT).findFirst().get();
        Tool st = ToolboxSearch.toolFor(file, s);
        assertTrue(st instanceof AddTool && ((AddTool) st).getFactory() == alu.getSubcircuitFactory());
    }
}
