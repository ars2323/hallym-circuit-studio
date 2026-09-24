/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.menu;

import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.model.Netlist;

/** 넷 정보(#73): 폭, 값을 내는 포트, 읽는 포트, 그 밖(터널·스플리터). 이름은 공용 식별자 규칙. */
final class NetInfoDialog {
    private NetInfoDialog() {
    }

    static String text(ContextMenus.Target t, Netlist.Net net) {
        Map<String, List<String>> info = EditMenus.netInfo(t.circuit, net);
        StringBuilder sb = new StringBuilder();
        sb.append(Messages.get("net.width", net.width())).append('\n');
        for (String k : new String[] {"drivers", "readers", "others"}) {
            List<String> ports = info.get(k);
            sb.append('\n').append(Messages.get("net." + k, ports.size())).append('\n');
            for (String p : ports) {
                sb.append("  ").append(p).append('\n');
            }
        }
        return sb.toString();
    }

    static void show(ContextMenus.Target t, Netlist.Net net) {
        if (net == null) {
            return;
        }
        javax.swing.JTextArea area = new javax.swing.JTextArea(text(t, net), 14, 40);
        area.setEditable(false);
        JOptionPane.showMessageDialog(t.project.getFrame(), new javax.swing.JScrollPane(area),
                Messages.get("net.title"), JOptionPane.PLAIN_MESSAGE);
    }
}
