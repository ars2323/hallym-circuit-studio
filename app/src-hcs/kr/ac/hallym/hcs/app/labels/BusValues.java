/*
 * Hallym Circuit Studio
 * Copyright (c) 2026 AIAC Lab, Hallym University.
 * License: GNU GPL version 2 or later. See LICENSE.
 */
package kr.ac.hallym.hcs.app.labels;

import java.awt.FontMetrics;

import javax.swing.JButton;

import com.cburch.logisim.data.Value;

import kr.ac.hallym.hcs.app.Messages;
import kr.ac.hallym.hcs.app.Settings;
import kr.ac.hallym.hcs.app.theme.Tokens;

/**
 * 버스 값 칩(C-08, PLAN.md 11.12 "버스 값"): 시뮬레이션 중 버스(폭 2 이상)의 가장 긴 선 옆에 지금 값을 라벨 칩 규칙대로
 * 보인다. 이름 있는 버스는 이름 칩에 값을 붙인다({@code ALUResult[31:0] = 0x0000000c}). 사이클 뷰에서 지난 사이클을
 * 고르면 그 사이클의 값이다(기록 엔진이 상태를 바꿔 끼운다). 진법은 상태 표시줄 단추로 바꾸고 앱 환경설정에 둔다.
 * 부품이 아니라 표시라 파일은 그대로다.
 */
public final class BusValues {
    /** 진법. 누를 때마다 이 순서로 돈다. */
    public enum Mode {
        HEX("hex"), DEC("dec"), SIGNED("signed"), OFF("off");

        private final String key;

        Mode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    static final String KEY = "labels.busValues";

    private BusValues() {
    }

    public static Mode mode() {
        String s = Settings.get().getString(KEY, Mode.HEX.key());
        for (Mode m : Mode.values()) {
            if (m.key().equals(s)) {
                return m;
            }
        }
        return Mode.HEX;
    }

    /** 모든 창의 상태 표시줄 단추(글자를 함께 바꾼다). */
    private static final java.util.Set<JButton> BUTTONS = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());

    public static void setMode(Mode m) {
        Settings.get().set(KEY, m.key());
        synchronized (BUTTONS) {
            for (JButton b : BUTTONS) {
                b.setText(Messages.get("labels.busValues." + m.key()));
            }
        }
        try {
            Settings.get().save();
        } catch (java.io.IOException e) {
            // 환경설정을 못 써도 표시는 바뀐다
        }
        LabelOverlay.repaintAll();
    }

    /** 칩에 쓸 값. 보일 것이 없으면(끔, 값 없음, 모두 떠 있음) null. */
    static String format(Value v, Mode m) {
        if (m == Mode.OFF || v == null || v.getWidth() < 2 || v.isUnknown()) {
            return null;
        }
        if (!v.isFullyDefined()) {
            // 일부만 정해졌거나 오류: 원조 16진 표기(x는 떠 있음, E는 충돌)를 진법과 상관없이 그대로
            return "0x" + v.toHexString();
        }
        long u = v.toIntValue() & (v.getWidth() >= 32 ? 0xffffffffL : (1L << v.getWidth()) - 1);
        switch (m) {
        case DEC:
            return Long.toString(u);
        case SIGNED:
            long s = u >= 1L << (v.getWidth() - 1) ? u - (1L << v.getWidth()) : u;
            return Long.toString(s);
        default:
            return String.format("0x%0" + ((v.getWidth() + 3) / 4) + "x", u);
        }
    }

    /**
     * 배치에 쓸 가장 넓은 값 글자(값이 바뀌어도 칩이 움직이지 않게). 숫자 칸마다 이 글꼴에서 가장 넓은 숫자를 둔다.
     */
    static String template(int width, Mode m, FontMetrics fm) {
        String digits = m == Mode.HEX ? "0123456789abcdefxE" : "0123456789";
        char wide = '0';
        for (char c : digits.toCharArray()) {
            if (fm.charWidth(c) > fm.charWidth(wide)) {
                wide = c;
            }
        }
        int n;
        switch (m) {
        case DEC:
            n = Long.toString(width >= 32 ? 0xffffffffL : (1L << width) - 1).length();
            break;
        case SIGNED:
            n = Long.toString(-(1L << (width - 1))).length() - 1;
            break;
        default:
            n = (width + 3) / 4;
            break;
        }
        StringBuilder sb = new StringBuilder(m == Mode.SIGNED ? "-" : m == Mode.HEX ? "0x" : "");
        for (int i = 0; i < n; i++) {
            sb.append(wide);
        }
        return sb.toString();
    }

    /** 상태 표시줄 단추(누를 때마다 다음 진법, 그다음 끔). */
    public static JButton button() {
        JButton b = new JButton(Messages.get("labels.busValues." + mode().key()));
        b.setFocusable(false);
        b.setToolTipText(Messages.get("labels.busValuesTip"));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setForeground(Tokens.TEXT_2);
        b.addActionListener(e -> setMode(Mode.values()[(mode().ordinal() + 1) % Mode.values().length]));
        synchronized (BUTTONS) {
            BUTTONS.add(b); // 코드나 다른 창에서 바꿔도 글자가 따라간다(C-08 검토)
        }
        return b;
    }
}
