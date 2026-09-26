/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.tutorial;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.gui.main.Canvas;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 빈 캔버스 안내(V-07, D-102): 보고 있는 회로에 부품도 선도 없으면 캔버스 가운데에 흐린 안내 한 덩어리를 그린다.
 * 할 일 셋: 부품 검색(Ctrl+K), 왼쪽 목록에서 끌어 놓기, 예제 열기(Help › Examples). 첫 부품을 놓는 순간 사라지고
 * 파일에는 아무것도 저장되지 않는다. 캐릭터는 쓰지 않는다.
 */
public final class EmptyHint {
    private EmptyHint() {
    }

    /** 보여야 하는가: 회로가 비었을 때만. */
    public static boolean shouldShow(Circuit circuit) {
        return circuit != null && circuit.getNonWires().isEmpty() && circuit.getWires().isEmpty();
    }

    /** 안내 글 줄들: 제목, 할 일 셋. */
    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add(Messages.get("hint.title"));
        out.add(Messages.get("hint.search"));
        out.add(Messages.get("hint.drag"));
        out.add(Messages.get("hint.examples"));
        return out;
    }

    /**
     * 캔버스 좌표(배율 없이, 원점만 옮긴 Graphics)에 그린다. 보이는 영역 가운데에 둔다.
     */
    public static void paint(Canvas canvas, Graphics g0, Circuit circuit) {
        if (!shouldShow(circuit) || !(g0 instanceof Graphics2D)) {
            return;
        }
        Rectangle vis = canvas.getVisibleRect();
        int cx = vis.x + vis.width / 2 - canvas.getHcsOriginX();
        int cy = vis.y + vis.height / 2 - canvas.getHcsOriginY();
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            List<String> lines = lines();
            Font title = new Font(Tokens.UI_FONT, Font.BOLD, Tokens.FONT_TITLE);
            Font body = new Font(Tokens.UI_FONT, Font.PLAIN, Tokens.FONT_UI);
            FontMetrics tm = g.getFontMetrics(title);
            FontMetrics bm = g.getFontMetrics(body);
            int gap = 8;
            int total = tm.getHeight() + gap + lines.size() - 1 == 0 ? 0 : tm.getHeight() + gap
                    + (lines.size() - 1) * (bm.getHeight() + 4);
            int y = cy - total / 2 + tm.getAscent();
            // 격자 점이 글자 사이로 비치지 않게 흰 바탕을 깐다
            int wmax = tm.stringWidth(lines.get(0));
            for (int i = 1; i < lines.size(); i++) {
                wmax = Math.max(wmax, bm.stringWidth(lines.get(i)));
            }
            g.setColor(Tokens.WHITE);
            g.fillRoundRect(cx - wmax / 2 - 24, cy - total / 2 - 16, wmax + 48, total + 32, 12, 12);
            g.setColor(Tokens.TEXT_MUTED);
            g.setFont(title);
            g.drawString(lines.get(0), cx - tm.stringWidth(lines.get(0)) / 2, y);
            y += tm.getDescent() + gap + bm.getAscent();
            g.setFont(body);
            for (int i = 1; i < lines.size(); i++) {
                g.drawString(lines.get(i), cx - bm.stringWidth(lines.get(i)) / 2, y);
                y += bm.getHeight() + 4;
            }
        } finally {
            g.dispose();
        }
    }
}
