/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.mips;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.tools.SetAttributeAction;

/**
 * Instruction Memory·Data Memory의 우클릭 메뉴 ".s 프로그램 불러오기"(PLAN.md 6.3). .s를 hcs-asm으로
 * 어셈블해 .text와 .data를 두 메모리의 {@code contents}에 넣는다. 같은 부품이 둘 이상일 때만 어느 쪽인지 묻는다.
 * 속성 변경은 되돌리기 한 번으로 취소된다.
 */
final class LoadProgramMenu implements MenuExtender, ActionListener {
    private final Instance instance;
    private Project proj;
    private JMenuItem load;
    private JMenuItem reload;

    LoadProgramMenu(Instance instance) {
        this.instance = instance;
    }

    @Override
    public void configureMenu(JPopupMenu menu, Project proj) {
        this.proj = proj;
        menu.addSeparator();
        load = new JMenuItem(Text.name("Load .s...").get());
        load.addActionListener(this);
        menu.add(load);
        String source = instance.getAttributeValue(MemoryFactory.SOURCE);
        if (source != null && !source.isEmpty()) {
            reload = new JMenuItem(Text.name("Reload ").get() + new File(source).getName());
            reload.addActionListener(this);
            menu.add(reload);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File circ = proj.getLogisimFile().getLoader().getMainFile();
        if (e.getSource() == reload) {
            File f = new File(instance.getAttributeValue(MemoryFactory.SOURCE));
            if (!f.isAbsolute() && circ != null) {
                f = new File(circ.getAbsoluteFile().getParentFile(), f.getPath());
            }
            load(f);
            return;
        }
        JFileChooser chooser = new JFileChooser(circ == null ? null : circ.getAbsoluteFile().getParentFile());
        chooser.setFileFilter(new FileNameExtensionFilter(Text.name("MIPS Assembly (*.s, *.asm)").get(), "s", "asm"));
        if (chooser.showOpenDialog(proj.getFrame()) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile());
        }
    }

    private void load(File source) {
        File exe = HcsAsm.locate();
        if (exe == null) {
            error(Text.of("Cannot find hcs-asm. Put " + HcsAsm.executableName() + " in the same folder as hcs-mips.jar.",
                    "hcs-asm을 찾을 수 없습니다. hcs-mips.jar와 같은 폴더에 " + HcsAsm.executableName()
                            + "을 두세요.").get());
            return;
        }
        AssembledProgram program;
        try {
            HcsAsm.Run run = HcsAsm.run(exe, source, Collections.<String>emptyList());
            if (run.exit == 2) {
                error(run.stderr.trim());
                return;
            }
            program = AssembledProgram.fromJson(run.stdout);
        } catch (Exception ex) {
            error(String.valueOf(ex.getMessage()));
            return;
        }
        if (!program.errors.isEmpty()) {
            StringBuilder sb = new StringBuilder(Text.of("Assembly errors in ", "어셈블 오류: ").get())
                    .append(source.getName()).append('\n');
            for (int i = 0; i < program.errors.size() && i < 10; i += 1) {
                sb.append('\n').append(program.errors.get(i));
            }
            error(sb.toString());
            return;
        }

        List<Circuit> circuits = proj.getLogisimFile().getCircuits();
        Component clicked = Instance.getComponentFor(instance);
        ProgramLoader.Target self = new ProgramLoader.Target(proj.getCurrentCircuit(), clicked);
        boolean clickedText = ProgramLoader.isText(clicked.getFactory());
        ProgramLoader.Target text = clickedText ? self : choose(ProgramLoader.find(circuits, true), ".text");
        ProgramLoader.Target data = clickedText ? choose(ProgramLoader.find(circuits, false), ".data") : self;
        if (text == null && data == null) {
            return;
        }
        String sourceAttr = ProgramLoader.relativeSource(proj.getLogisimFile().getLoader().getMainFile(), source);
        ProgramLoader.Plan plan = ProgramLoader.plan(program, text, data, sourceAttr);

        Action action = null;
        for (Circuit c : circuitsOf(plan)) {
            SetAttributeAction act = new SetAttributeAction(c, Text.name("Load .s"));
            for (ProgramLoader.Change ch : plan.changes) {
                if (ch.target.circuit == c) {
                    act.set(ch.target.component, ch.attr, ch.value);
                }
            }
            action = action == null ? act : action.append(act);
        }
        if (action != null) {
            proj.doAction(action);
        }
        JOptionPane.showMessageDialog(proj.getFrame(), String.join("\n", plan.notes),
                source.getName(), JOptionPane.INFORMATION_MESSAGE);
    }

    private static List<Circuit> circuitsOf(ProgramLoader.Plan plan) {
        List<Circuit> out = new ArrayList<Circuit>();
        for (ProgramLoader.Change ch : plan.changes) {
            if (!out.contains(ch.target.circuit)) {
                out.add(ch.target.circuit);
            }
        }
        return out;
    }

    /** 후보가 하나면 그것, 여럿이면 묻는다. 없으면 null. */
    private ProgramLoader.Target choose(List<ProgramLoader.Target> candidates, String segment) {
        if (candidates.size() <= 1) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        Object picked = JOptionPane.showInputDialog(proj.getFrame(),
                Text.of("Which memory gets " + segment + "?", segment + "를 넣을 메모리를 고르세요.").get(),
                segment, JOptionPane.QUESTION_MESSAGE, null, candidates.toArray(), candidates.get(0));
        return (ProgramLoader.Target) picked;
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(proj.getFrame(), message,
                Text.name("Load .s").get(), JOptionPane.ERROR_MESSAGE);
    }
}
