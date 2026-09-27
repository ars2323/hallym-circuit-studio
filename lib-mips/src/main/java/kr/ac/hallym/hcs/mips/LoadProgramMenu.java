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

import kr.ac.hallym.hcs.mips.image.AssemblySource;

/**
 * Instruction Memory·Data Memory의 우클릭 메뉴 "Load Program..."(PLAN.md 6.3, Z-01). Hallym MIPS의 실행 이미지(.hmx)를
 * 읽어 .text와 .data를 메모리의 {@code contents}에 넣는다. 파일 고르기 창은 .hmx만 보인다(D-141). 고른 파일은 .circ 기준
 * 상대 경로로 {@code source} 속성에 둔다. 속성 변경은 되돌리기 한 번으로 취소된다. 파일에 오류가 있거나 담을 부품이
 * 없으면 아무것도 바꾸지 않는다.
 *
 * <p>옛 파일의 {@code source}가 .s를 가리키면 "Reload" 대신 "Load .hmx for 이름.s..."를 둔다. 누르면 사실과 할 일
 * ({@link AssemblySource#FACT})을 보이고 .s가 있던 폴더에서 .hmx 고르기 창을 연다(같은 이름의 .hmx가 있으면 골라 둔다).
 * 학생이 .hmx를 고르면 불러오기가 {@code source}를 그 경로로 바꾼다. 고르지 않으면 아무것도 바꾸지 않는다.
 */
final class LoadProgramMenu implements MenuExtender, ActionListener {
    /** 결과 창에서 한 번에 보일 오류 수. */
    static final int MAX_ERRORS = 12;
    /** 노란 사실 줄의 바탕(Hallym MIPS warning 토큰의 옅은 바탕). */
    static final String WARN_BACKGROUND = "#FFF4C2";

    private final Instance instance;
    private Project proj;
    private JMenuItem load;
    private JMenuItem reload;
    /** 옛 .s 경로를 .hmx로 바꾸는 항목(source가 .s일 때만). */
    private JMenuItem replace;

    LoadProgramMenu(Instance instance) {
        this.instance = instance;
    }

    @Override
    public void configureMenu(JPopupMenu menu, Project proj) {
        this.proj = proj;
        menu.addSeparator();
        load = new JMenuItem(Text.name("Load Program...").get());
        load.addActionListener(this);
        menu.add(load);
        String source = instance.getAttributeValue(MemoryFactory.SOURCE);
        if (source != null && !source.isEmpty()) {
            if (AssemblySource.isAssembly(source)) {
                replace = new JMenuItem(replaceLabel(source));
                replace.addActionListener(this);
                menu.add(replace);
            } else {
                reload = new JMenuItem(Text.name("Reload ").get() + AssemblySource.fileName(source));
                reload.addActionListener(this);
                menu.add(reload);
            }
        }
    }

    /** 옛 .s 경로 항목의 이름(메뉴 이름은 영어, D-049). 예: {@code Load .hmx for sum.s...}. */
    static String replaceLabel(String source) {
        return Text.name("Load .hmx for ").get() + AssemblySource.fileName(source) + "...";
    }

    /** 옛 .s 경로 안내: 사실과 할 일, 그리고 속성 값. */
    static String replaceMessage(String source) {
        return AssemblySource.FACT.get(Text.korean()) + "\n" + MemoryFactory.SOURCE.getDisplayName() + ": " + source;
    }

    /**
     * 실행 이미지(.hmx)만 보이는 파일 고르기 창. dir에서 열고, select가 있는 파일이면 골라 둔다. "모든 파일" 거르개는
     * 두지 않는다(D-141).
     */
    static JFileChooser chooser(File dir, File select) {
        JFileChooser chooser = new JFileChooser(dir);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(Text.name("Executable image (*.hmx)").get(), "hmx"));
        if (select != null && select.isFile()) {
            chooser.setSelectedFile(select);
        }
        return chooser;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File circ = proj.getLogisimFile().getLoader().getMainFile();
        String source = instance.getAttributeValue(MemoryFactory.SOURCE);
        if (e.getSource() == reload) {
            load(ProgramLoader.resolveSource(circ, source));
            return;
        }
        File dir = circ == null ? null : circ.getAbsoluteFile().getParentFile();
        File select = null;
        if (e.getSource() == replace) {
            JOptionPane.showMessageDialog(proj.getFrame(), replaceMessage(source), Text.name("Load Program").get(),
                    JOptionPane.INFORMATION_MESSAGE);
            File old = ProgramLoader.resolveSource(circ, source);
            if (old.getParentFile() != null && old.getParentFile().isDirectory()) {
                dir = old.getParentFile();
            }
            select = new File(dir, AssemblySource.imageName(source));
        }
        JFileChooser chooser = chooser(dir, select);
        if (chooser.showOpenDialog(proj.getFrame()) == JFileChooser.APPROVE_OPTION) {
            load(chooser.getSelectedFile());
        }
    }

    private void load(File file) {
        ProgramLoader.Loaded loaded = ProgramLoader.read(file);
        if (!loaded.ok()) {
            error(file.getName(), loaded.errors);
            return;
        }
        List<Circuit> circuits = proj.getLogisimFile().getCircuits();
        Component clicked = Instance.getComponentFor(instance);
        ProgramLoader.Target self = new ProgramLoader.Target(proj.getCurrentCircuit(), clicked);
        String sourceAttr = ProgramLoader.relativeSource(proj.getLogisimFile().getLoader().getMainFile(), file);
        ProgramLoader.Plan plan = ProgramLoader.plan(loaded, circuits, self, new ProgramLoader.Chooser() {
            @Override
            public ProgramLoader.Target choose(List<ProgramLoader.Target> candidates, String what) {
                Object picked = JOptionPane.showInputDialog(proj.getFrame(),
                        Text.of("Which memory gets " + what + "?", what + " 구간을 넣을 메모리를 고르세요.").get(),
                        Text.name("Load Program").get(), JOptionPane.QUESTION_MESSAGE, null, candidates.toArray(),
                        candidates.get(0));
                return (ProgramLoader.Target) picked;
            }
        }, sourceAttr);
        if (!plan.errors.isEmpty()) {
            error(file.getName(), plan.errors);
            return;
        }

        Action action = null;
        for (Circuit c : circuitsOf(plan)) {
            SetAttributeAction act = new SetAttributeAction(c, Text.name("Load Program"));
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
        JOptionPane.showMessageDialog(proj.getFrame(), summaryHtml(plan), file.getName(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** 요약 창의 글: 요약 줄, 사실 줄, 노란 사실 줄. */
    static String summaryHtml(ProgramLoader.Plan plan) {
        StringBuilder sb = new StringBuilder("<html>");
        for (String n : plan.notes) {
            sb.append(escape(n)).append("<br>");
        }
        for (String f : plan.facts) {
            sb.append(escape(f)).append("<br>");
        }
        for (String w : plan.warnings) {
            sb.append("<div style='background-color:").append(WARN_BACKGROUND).append(";padding:2px 4px'>")
                    .append(escape(w)).append("</div>");
        }
        return sb.append("</html>").toString();
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 오류 목록(앞 {@link #MAX_ERRORS}개와 나머지 개수). */
    static String errorText(List<String> errors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errors.size() && i < MAX_ERRORS; i += 1) {
            sb.append(i == 0 ? "" : "\n").append(errors.get(i));
        }
        int more = errors.size() - MAX_ERRORS;
        if (more > 0) {
            sb.append('\n').append(Text.of("… and " + more + " more", "… 그 밖에 " + more + "개").get());
        }
        return sb.toString();
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

    private void error(String file, List<String> errors) {
        JOptionPane.showMessageDialog(proj.getFrame(), errorText(errors),
                Text.name("Load Program").get() + ": " + file, JOptionPane.ERROR_MESSAGE);
    }
}
