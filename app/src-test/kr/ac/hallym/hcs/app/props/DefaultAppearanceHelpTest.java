/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.props;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;

import kr.ac.hallym.hcs.app.labels.HoverInfo;
import kr.ac.hallym.hcs.regress.CircuitBuilder;

/**
 * S-08: 기본 모양 서브회로는 빠른 속성 창에 Auto Appearance 단추, 마우스 오버 정보에 포트 이름 목록. 진단 메시지는
 * 만들지 않는다(원칙 9). 사용자 모양이 되면 단추는 없다.
 */
class DefaultAppearanceHelpTest {
    @TempDir
    Path tmp;

    @Test
    void plainSubcircuitsOfferAutoAppearanceAndListTheirPorts() throws Exception {
        LogisimFile f = CircuitBuilder.newFile(new Loader(null), tmp.toFile());
        Circuit blk = new Circuit("blk");
        f.addCircuit(blk);
        CircuitBuilder sb = new CircuitBuilder(f, blk);
        sb.add("Wiring", "Pin", 100, 100, "label", "a");
        sb.add("Wiring", "Pin", 100, 140, "label", "b");
        sb.add("Wiring", "Pin", 300, 100, "facing", "west", "output", "true", "label", "y");
        sb.commit();
        CircuitBuilder mb = new CircuitBuilder(f, f.getMainCircuit());
        Component inst = mb.addSubcircuit(blk, 400, 300);
        Component and = mb.add("Gates", "AND Gate", 600, 300, "inputs", "2");
        mb.commit();
        Project proj = new Project(f);
        proj.getSimulator().setIsRunning(false);

        assertSame(blk, QuickBar.defaultAppearance(inst, proj), "a plain subcircuit: offer the button");
        assertNull(QuickBar.defaultAppearance(and, proj), "not a subcircuit");

        com.cburch.logisim.data.Bounds b = inst.getBounds();
        List<String> lines = HoverInfo.lines(proj.getCircuitState(),
                Location.create(b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2));
        String ports = kr.ac.hallym.hcs.app.Messages.get("hover.ports", "a, b", "y");
        assertTrue(lines.contains(ports), lines.toString());

        // 사용자 모양이 되면 단추도 포트 목록도 없다(상자에 이름이 보인다)
        proj.doAction(kr.ac.hallym.hcs.app.appear.AutoAppearance.action(blk,
                kr.ac.hallym.hcs.app.appear.AutoAppearance.build(blk)));
        assertNull(QuickBar.defaultAppearance(inst, proj), "a custom appearance: no button");
        Component now = f.getMainCircuit().getNonWires().stream()
                .filter(c -> c.getFactory() instanceof com.cburch.logisim.circuit.SubcircuitFactory).findFirst().get();
        com.cburch.logisim.data.Bounds nb = now.getBounds();
        List<String> after = HoverInfo.lines(proj.getCircuitState(),
                Location.create(nb.getX() + nb.getWidth() / 2, nb.getY() + nb.getHeight() / 2));
        assertTrue(!after.contains(ports), after.toString());
    }
}
