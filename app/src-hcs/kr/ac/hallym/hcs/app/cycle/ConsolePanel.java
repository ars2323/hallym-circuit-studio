/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.cycle;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.cburch.logisim.circuit.CircuitState;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 캔버스 아래 Console 탭(C-09): 보고 있는 사이클의 Console 부품 출력 전체. 부품 몸통은 마지막 몇 줄만 보인다.
 * Console이 여럿이면 이름 줄로 나눈다. exit 했으면 끝에 "-- exit --".
 */
final class ConsolePanel {
    private final Supplier<CircuitState> root;
    private final JTextArea area = new JTextArea();
    private final JPanel panel = new JPanel(new BorderLayout());
    private String last = "";

    ConsolePanel(Supplier<CircuitState> root) {
        this.root = root;
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Tokens.FONT_UI));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        area.setForeground(Tokens.TEXT);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
    }

    JComponent component() {
        return panel;
    }

    /** 표시할 글(테스트). */
    static String render(List<ConsoleText.Entry> entries) {
        if (entries.isEmpty()) {
            return Messages.get("console.none");
        }
        StringBuilder sb = new StringBuilder();
        for (ConsoleText.Entry e : entries) {
            if (entries.size() > 1) {
                sb.append("── ").append(e.name).append(" ──\n");
            }
            sb.append(e.text);
            if (e.exited) {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append("-- exit --\n");
            }
        }
        return sb.toString();
    }

    String text() {
        return area.getText();
    }

    void refresh() {
        String now = render(ConsoleText.collect(root.get()));
        if (!now.equals(last)) {
            last = now;
            area.setText(now);
            area.setCaretPosition(now.length()); // 새 출력이 보이게
        }
    }
}
